package noobtrieu.reimaginedintro.client.transition;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.ui.GlassRendering;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * Two-phase menu transitions WITHOUT re-rendering any screen.
 *
 * Architecture (v3.2.1): we intercept Minecraft.setScreen and DELAY the actual
 * swap. During phase A the outgoing screen is still the active screen and renders
 * through its completely normal vanilla path — we only apply a pose transform
 * around its wrapper. At the swap point we perform the real setScreen. During
 * phase B the incoming screen is active and, again, renders normally under a
 * pose transform. No screen is ever rendered while not being the active screen,
 * so scissor stacks, z-order navigation (GuiRenderState) and strata bookkeeping
 * only ever see a single screen per frame — exactly what vanilla is built for.
 *
 * v3.5.0 FLUID: a composite effect. Screens whose incoming side contains a big
 * selection list (world list, server list, mod list) reveal top-to-bottom via a
 * video-frame wipe (seamless because every menu already shares that background).
 * Button-to-button screens swap instantly, then the real buttons are hidden via
 * a per-widget render gate and replaced by morphing glass rectangles that split
 * (fewer -> more) or merge (more -> fewer) from the old layout into the new one
 * with a per-button stagger. All drawing stays in the vanilla GUI pipeline.
 */
public final class ScreenTransitions {
    public enum Type {
        SLIDE,     // two-phase push: old slides left, then new slides in from the right
        SLIDE_UP,  // same, vertically
        COVER,     // old stays put (no phase-A transform), new slides over from the right
        FADE,      // fade through black
        ZOOM,      // old scales back slightly + fade, new scales up from 92%
        FLUID,     // button morph / list wipe (see class doc)
        OFF
    }

    private enum Phase { IDLE, A, B }

    private static Screen pending;
    private static Phase phase = Phase.IDLE;
    private static Type type = Type.OFF;
    private static long durationMs = 300;
    private static long startMs;
    /** Set while we internally perform the real setScreen, so our hook lets it through. */
    private static boolean performingSwap;

    // --- FLUID state ---
    /** Set between the HEAD and TAIL of a vanilla setScreen that should morph. */
    private static boolean fluidArmed;
    /** True while a transition that started inside a world is running (scrim instead of menu video). */
    private static boolean transitionInGame;
    /** True while drawMorph renders a real widget in flight (bypasses WidgetGateMixin). */
    private static boolean flightRendering;
    /** Bounds (x,y,w,h) of the incoming screen's selection list, if any - the reveal covers only this rect. */
    private static int[] listRect;
    private static boolean morphLogged;
    /** Source button rects of the outgoing screen, reading order. */
    private static List<int[]> morphSources;
    /** Target widgets of the incoming screen (identity = render gate). */
    private static List<AbstractWidget> morphTargets;
    /** Widgets whose vanilla rendering is suppressed during the morph. */
    private static final Set<AbstractWidget> hiddenWidgets = new HashSet<>();
    /** FLUID phase A/B uses a video-frame wipe (list screens / no buttons). */
    private static boolean fluidWipe;

    private ScreenTransitions() {
    }

    /**
     * Called from MinecraftMixin at the HEAD of Minecraft.setScreen.
     * @return true if the transition captured this swap — the original call must be cancelled.
     */
    public static boolean onSetScreen(Screen next) {
        if (performingSwap) return false; // our own swap: let it through
        if (next == null) return false;    // closing to the game world: instant
        if (MinecraftAccessorBridge.getActiveScreen() == null) return false;
        if (next instanceof IntroScreen) return false; // intro owns its transitions
        if (MinecraftAccessorBridge.getActiveScreen() instanceof IntroScreen) return false;

        // Entering a world: the reversed-intro overlay covers the level
        // loading screen entirely. Initial entry only (level == null) - the
        // nether-portal variant of this screen appears in-game and is left
        // alone. Arm first, then step aside (returns false untouched when the
        // feature is disabled in the config).
        if (next instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
            && Minecraft.getInstance().level == null) {
            if (EnterWorldTransition.arm()) {
                if (phase != Phase.IDLE || fluidArmed) abandon(); // drop any half-played menu morph
                return false;
            }
        }

        // A new swap arriving mid-transition: resolve it by finishing instantly,
        // then the caller's setScreen proceeds normally (no new animation).
        if (phase != Phase.IDLE || fluidArmed) {
            abandon();
            return false;
        }

        Type t = configuredType();
        if (t == Type.OFF) return false;
        // In-game CONTAINER screens (inventory, chests, crafting...) must be
        // left alone: deferring the swap even briefly disturbs their init chain
        // (RecipeBookComponent.book stays null -> NPE, seen with JEI). Non-
        // container screens in-game (the ESC game menu and its sub-screens)
        // animate exactly like the main menu.
        transitionInGame = Minecraft.getInstance().level != null;
        if (transitionInGame && next instanceof AbstractContainerScreen) return false;
        long d = configuredDuration();
        if (d <= 0) return false;

        if (t == Type.FLUID) {
            Screen current = MinecraftAccessorBridge.getActiveScreen();
            boolean outList = hasSelectionList(current);
            // NOTE: the incoming screen is NOT initialized yet at this point, so
            // hasSelectionList(next) would always be false - the incoming side is
            // decided later, at the swap point (setupFluidPhaseB).
            // Unified two-phase flow: phase A always retains the outgoing screen
            // (wiped out for list screens, held still briefly for button screens),
            // then the real swap happens and phase B picks wipe-in / morph.
            List<AbstractWidget> srcButtons = collectButtons(current);
            morphSources = srcButtons.stream()
                .map(w -> new int[]{w.getX(), w.getY(), w.getWidth(), w.getHeight()})
                .collect(Collectors.toList());
            ReimaginedIntro.LOGGER.info("[ReimaginedIntro/FLUID] transition: {} -> {} (outList={} sources={})",
                current.getClass().getSimpleName(), next.getClass().getSimpleName(), outList, srcButtons.size());
            pending = next;
            type = Type.FLUID;
            fluidWipe = false; // decided for phase B (reveal) at the swap point
            durationMs = Math.max(100, d * 3 / 10); // brief hold of the old screen
            startMs = Util.getMillis();
            phase = Phase.A;
            return true;
        }

        pending = next;
        type = t;
        durationMs = d;
        startMs = Util.getMillis();
        phase = Phase.A;
        return true;
    }

    /**
     * MinecraftMixin TAIL of setScreen: the incoming screen is initialized now.
     * Used by FLUID to capture the target layout and start phase B.
     */
    public static void onAfterSetScreen() {
        // FLUID now always goes through the two-phase interceptor, so the swap
        // itself is driven by maybeAdvance; nothing to do here anymore.
    }

    /** Decides the FLUID phase B effect from the (now active) incoming screen. */
    private static void setupFluidPhaseB() {
        Screen incoming = MinecraftAccessorBridge.getActiveScreen();
        // Clear only the PREVIOUS morph's targets/hidden widgets. morphSources
        // was just armed by onSetScreen for THIS transition and must survive
        // (this was the bug that made morphs dead code since v3.5.0).
        morphTargets = null;
        hiddenWidgets.clear();
        if (incoming == null) return;

        long d = configuredDuration();
        boolean incomingList = hasSelectionList(incoming);
        List<AbstractWidget> targets = collectButtons(incoming);
        List<AbstractWidget> lists = collectWidgets(incoming, new ArrayList<>(), true);
        listRect = lists.isEmpty() ? null
            : new int[]{lists.get(0).getX(), lists.get(0).getY(), lists.get(0).getWidth(), lists.get(0).getHeight()};
        boolean canMorph = morphSources != null && !morphSources.isEmpty() && !targets.isEmpty();
        // Hybrid: buttons ALWAYS fly (morph); list screens ALSO get the
        // top-down reveal of the list area over the shared background video.
        boolean reveal = incomingList && videoAvailable();
        ReimaginedIntro.LOGGER.info("[ReimaginedIntro/FLUID] phase B setup: incoming={} list={} targets={} sources={} morph={} reveal={}",
            incoming.getClass().getSimpleName(), incomingList, targets.size(), morphSources == null ? 0 : morphSources.size(), canMorph, reveal);
        if (canMorph) {
            morphTargets = targets;
            hiddenWidgets.addAll(targets);
            startPhaseB(Math.round(d * 2.2f), reveal);
        } else if (reveal) {
            startPhaseB(d, true);
        } else if (videoAvailable()) {
            startPhaseB(Math.max(180, d * 6 / 10), true);
        } else {
            startFallbackFadeB(d);
        }
    }

    /** Starts a B-only phase with a fresh timeline (e starts at 0). */
    private static void startPhaseB(long realDuration, boolean wipe) {
        type = Type.FLUID;
        fluidWipe = wipe;
        durationMs = realDuration;
        startMs = Util.getMillis() - realDuration / 2; // so (p-0.5)*2 starts at 0
        phase = Phase.B;
    }

    private static void startFallbackFadeB(long d) {
        type = Type.FADE;
        fluidWipe = false;
        durationMs = Math.max(150, d * 6 / 10);
        startMs = Util.getMillis() - durationMs / 2;
        phase = Phase.B;
    }

    /**
     * Resolves an interrupted transition by performing the pending swap immediately
     * (used when a new setScreen arrives while a transition is still playing).
     */
    private static void abandon() {
        Screen p = pending;
        reset();
        if (p != null) {
            performingSwap = true;
            try {
                MinecraftAccessorBridge.setScreen(p);
            } finally {
                performingSwap = false;
            }
        }
    }

    /** While a transition is in flight, menu input is swallowed. */
    public static boolean isBlockingInput() {
        if (phase == Phase.IDLE && !fluidArmed) return false;
        // Safety valve: never wedge input if the render hook stopped firing.
        if (Util.getMillis() - startMs > durationMs + 1000L) {
            abandon();
            return false;
        }
        return true;
    }

    /** Widget render gate (WidgetGateMixin): suppresses the real buttons during a morph. */
    public static boolean isWidgetHidden(AbstractWidget widget) {
        if (flightRendering) return false; // drawMorph renders this very widget right now
        if (phase == Phase.IDLE || hiddenWidgets.isEmpty() || !hiddenWidgets.contains(widget)) return false;
        // Safety fuse: never keep widgets hidden past the animation window.
        return Util.getMillis() - startMs <= durationMs + 500L;
    }

    /**
     * ScreenMixin HEAD of renderWithTooltipAndSubtitles for the ACTIVE screen.
     * Applies the phase transform around the screen's normal rendering.
     */
    public static void onRenderHead(Screen self, GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (phase == Phase.IDLE) return;

        float w = g.guiWidth();
        float h = g.guiHeight();
        float p = progress();

        switch (phase) {
            case A -> {
                if (p < 0.5f && self == MinecraftAccessorBridge.getActiveScreen()) {
                    applyOutgoing(g, w, h, clamp01(p * 2.0f));
                }
            }
            case B -> {
                if (self == MinecraftAccessorBridge.getActiveScreen()) {
                    applyIncoming(g, w, h, clamp01((p - 0.5f) * 2.0f));
                }
            }
            default -> {
            }
        }
    }

    /** ScreenMixin TAIL — restores the pose pushed by the head hook and draws overlays. */
    public static void onRenderTail(Screen self, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (phase != Phase.IDLE) {
            if (posePushed) {
                g.pose().popMatrix();
                posePushed = false;
            }
            float w = g.guiWidth();
            float h = g.guiHeight();
            float e = phase == Phase.A ? clamp01(progress() * 2.0f) : clamp01((progress() - 0.5f) * 2.0f);

            if (type == Type.FLUID && phase == Phase.B) {
                // Hybrid phase B: top-down list reveal (video cover retreating)
                // AND/OR button morphs flying in - both can run together.
                if (fluidWipe) {
                    if (listRect != null) {
                        // The reveal covers ONLY the list region - everything
                        // else on the screen (title, background, buttons) shows
                        // immediately; the buttons themselves fly in via morph.
                        int lx = listRect[0], ly = listRect[1], lw = listRect[2], lh = listRect[3];
                        int edge = Math.round(lh * ease(e));
                        if (edge < lh) {
                            drawVideoRegion(g, ly + edge, ly + lh);
                            drawWipeEdge(g, lx, lw, ly + edge);
                        }
                    } else {
                        int edge = Math.round(h * ease(e));
                        if (edge < h) {
                            drawVideoRegion(g, edge, (int) h);
                            drawWipeEdge(g, 0, (int) w, edge);
                        }
                    }
                }
                if (morphTargets != null) {
                    drawMorph(g, e);
                }
            }

            if (curtainAlpha > 0) {
                g.fill(0, 0, (int) w, (int) h, ((int) (curtainAlpha * 255.0f)) << 24);
                curtainAlpha = 0.0f;
            }
        }
        maybeAdvance();
    }

    private static boolean posePushed;
    private static float curtainAlpha;

    /**
     * Soft fade-out bands below the reveal boundary: the incoming content fades
     * into the background video with NO visible line — a pure top-down reveal.
     * (Pure fill stack, VulkanMod-safe.)
     */
    private static void drawWipeEdge(GuiGraphics g, int x, int w, int edge) {
        if (edge <= 0) return;
        g.fill(x, edge, x + w, edge + 5, 0xB4000000);
        g.fill(x, edge + 5, x + w, edge + 11, 0x87000000);
        g.fill(x, edge + 11, x + w, edge + 18, 0x5A000000);
        g.fill(x, edge + 18, x + w, edge + 26, 0x30000000);
    }

    private static void applyOutgoing(GuiGraphics g, float w, float h, float e) {
        switch (type) {
            case SLIDE, COVER -> {
                if (type == Type.COVER) return;
                g.fill(0, 0, (int) w, (int) h, 0xFF000000);
                g.pose().pushMatrix();
                g.pose().translate(-w * ease(e), 0.0f);
                posePushed = true;
            }
            case SLIDE_UP -> {
                g.fill(0, 0, (int) w, (int) h, 0xFF000000);
                g.pose().pushMatrix();
                g.pose().translate(0.0f, -h * ease(e));
                posePushed = true;
            }
            case FADE -> curtainAlpha = e;
            case ZOOM -> {
                float s = 1.0f - 0.06f * ease(e);
                g.fill(0, 0, (int) w, (int) h, 0xFF000000);
                g.pose().pushMatrix();
                g.pose().translate(w / 2.0f, h / 2.0f);
                g.pose().scale(s, s);
                g.pose().translate(-w / 2.0f, -h / 2.0f);
                posePushed = true;
                curtainAlpha = 0.35f * e;
            }
            default -> {
            }
        }
    }

    private static void applyIncoming(GuiGraphics g, float w, float h, float e) {
        switch (type) {
            case SLIDE, COVER -> {
                g.fill(0, 0, (int) w, (int) h, 0xFF000000);
                g.pose().pushMatrix();
                g.pose().translate(w * (1.0f - ease(e)), 0.0f);
                posePushed = true;
            }
            case SLIDE_UP -> {
                g.fill(0, 0, (int) w, (int) h, 0xFF000000);
                g.pose().pushMatrix();
                g.pose().translate(0.0f, h * (1.0f - ease(e)));
                posePushed = true;
            }
            case FADE -> curtainAlpha = 1.0f - e;
            case FLUID -> {
                // No positional drift for the incoming page: the background and
                // title must stay perfectly still (user request) - only the
                // reveal and the button morphs move.
            }
            case ZOOM -> {
                float s = 0.92f + 0.08f * ease(e);
                g.fill(0, 0, (int) w, (int) h, 0xFF000000);
                g.pose().pushMatrix();
                g.pose().translate(w / 2.0f, h / 2.0f);
                g.pose().scale(s, s);
                g.pose().translate(-w / 2.0f, -h / 2.0f);
                posePushed = true;
            }
            default -> {
            }
        }
    }

    /** Drives the state machine from the render tick (TAIL). */
    private static void maybeAdvance() {
        float p = progress();
        if (phase == Phase.A && p >= 0.5f) {
            phase = Phase.B;
            Screen next = pending;
            pending = null;
            performingSwap = true;
            try {
                MinecraftAccessorBridge.setScreen(next);
            } catch (Throwable t) {
                ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Failed to open screen during transition; ending it.", t);
                reset();
            } finally {
                performingSwap = false;
            }
            if (type == Type.FLUID && phase == Phase.B) {
                setupFluidPhaseB(); // pick wipe-in / morph for the incoming screen
            }
        } else if (phase == Phase.B && p >= 1.0f) {
            reset();
        }
    }

    private static void reset() {
        pending = null;
        phase = Phase.IDLE;
        type = Type.OFF;
        posePushed = false;
        curtainAlpha = 0.0f;
        fluidArmed = false;
        fluidWipe = false;
        morphLogged = false;
        transitionInGame = false;
        listRect = null;
        clearMorph();
    }

    private static void clearMorph() {
        morphSources = null;
        morphTargets = null;
        hiddenWidgets.clear();
    }

    // -------------------------------------------------------------------------
    // FLUID internals
    // -------------------------------------------------------------------------

    private static boolean videoAvailable() {
        if (transitionInGame) return true; // in-game reveal uses a dark scrim, not the menu video
        return ModConfig.get().enableTitleScreenLoop && FrameStreamer.isReady();
    }

    /** True if the screen contains a big selection list (world/server/mod lists). */
    private static boolean hasSelectionList(Screen screen) {
        return !collectWidgets(screen, new ArrayList<>(), true).isEmpty();
    }

    /** Buttons (and sliders) of a screen in reading order. */
    private static List<AbstractWidget> collectButtons(Screen screen) {
        List<AbstractWidget> out = new ArrayList<>();
        collectWidgets(screen, out, false);
        out.sort((a, b) -> {
            int dy = Integer.compare(a.getY(), b.getY());
            return dy != 0 ? dy : Integer.compare(a.getX(), b.getX());
        });
        return out;
    }

    /**
     * Walks a screen's widget tree. In list mode collects selection lists,
     * otherwise collects visible buttons/sliders.
     */
    private static List<AbstractWidget> collectWidgets(Screen screen, List<AbstractWidget> out, boolean listMode) {
        if (screen == null) return out;
        List<GuiEventListener> queue = new ArrayList<>(screen.children());
        Set<GuiEventListener> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!queue.isEmpty()) {
            GuiEventListener l = queue.remove(queue.size() - 1);
            if (!seen.add(l)) continue;
            if (l instanceof AbstractSelectionList) {
                if (listMode) {
                    out.add((AbstractWidget) l);
                } else {
                    // Force-collect the row widgets of scrollable option
                    // screens. In 1.21.11 the option rows (sound/video sliders,
                    // cycle buttons, keybind buttons...) extend AbstractWidget
                    // DIRECTLY - not AbstractButton/AbstractSliderButton - so
                    // the generic filter below skips them and they were left
                    // to the reveal (the 'no effect' report). Entries expose
                    // their row widgets via children(); collect every visible
                    // non-container AbstractWidget found there.
                    for (GuiEventListener entry : ((AbstractSelectionList<?>) l).children()) {
                        if (!(entry instanceof net.minecraft.client.gui.components.events.ContainerEventHandler eh)) continue;
                        for (GuiEventListener c : eh.children()) {
                            if (c instanceof AbstractWidget w && w.visible && !(w instanceof AbstractContainerWidget)) {
                                if (seen.add(w)) out.add(w);
                            }
                        }
                    }
                }
            } else if (l instanceof AbstractButton || l instanceof AbstractSliderButton) {
                AbstractWidget w = (AbstractWidget) l;
                if (w.visible) out.add(w);
            }
            // Descend containers via children(): selection-list entries
            // implement ContainerEventHandler and expose their row elements -
            // the same path event dispatch uses. Those widgets must fly too:
            // option sub-screens (sound, controls, video...) are scrollable
            // list pages whose rows ARE buttons/sliders; without this they
            // were left to the reveal. Self/identity guards keep the walk safe.
            if (l instanceof net.minecraft.client.gui.components.events.ContainerEventHandler handler) {
                for (GuiEventListener c : handler.children()) {
                    if (c != null && c != l && seen.add(c)) queue.add(c);
                }
            } else if (!(l instanceof AbstractWidget)) {
                // Non-widget listeners (e.g. the OptionInstanceWidget record that
                // OptionsList rows use in 1.21.11) hold the real sliders in
                // their fields. Dig them out BY TYPE - no names, so it works
                // under obfuscation and against any list entry implementation.
                unwrapNested(l, queue, seen);
            }
        }
        return out;
    }

    /** Type-based reflection dig: finds AbstractWidgets hidden in field graphs. */
    private static void unwrapNested(GuiEventListener l, List<GuiEventListener> queue, Set<GuiEventListener> seen) {
        try {
            for (Class<?> c = l.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    addNestedValues(f.get(l), queue, seen);
                }
            }
        } catch (Throwable ignored) {
            // best effort only - if reflection fails the rows simply reveal
        }
    }

    private static void addNestedValues(Object v, List<GuiEventListener> queue, Set<GuiEventListener> seen) {
        if (v == null) return;
        if (v instanceof AbstractWidget w) {
            if (seen.add(w)) queue.add(w);
        } else if (v instanceof GuiEventListener g) {
            if (seen.add(g)) queue.add(g);
        } else if (v instanceof Iterable<?> it) {
            for (Object e : it) addNestedValues(e, queue, seen);
        }
    }

    /**
     * Draws the shared looping-video background over a horizontal band of the
     * screen, using the exact cover-crop mapping MenuBackgroundMixin uses, so
     * the band is pixel-identical to the menu background behind it.
     */
    private static void drawVideoRegion(GuiGraphics g, int y0, int y1) {
        if (y1 <= y0) return;
        if (transitionInGame) {
            // In-game: a dark scrim instead of the menu background video -
            // the video would completely hide the world behind the game menu.
            g.fill(0, y0, (int) g.guiWidth(), y1, 0xC8000000);
            return;
        }
        float w = g.guiWidth();
        float h = g.guiHeight();
        float aspect = 16.0f / 9.0f;
        float u0, u1, v0, v1;
        if (w / h > aspect) {
            // Width-limited: the full texture width is shown, top/bottom cropped.
            float vh = w / aspect;
            float vTop = (vh - h) / 2.0f;
            u0 = 0.0f;
            u1 = 1.0f;
            v0 = (vTop + y0) / vh;
            v1 = (vTop + y1) / vh;
        } else {
            // Height-limited: the full texture height is shown, left/right cropped.
            float uw = h * aspect;
            float uLeft = (uw - w) / 2.0f;
            u0 = uLeft / uw;
            u1 = (uLeft + w) / uw;
            v0 = y0 / h;
            v1 = y1 / h;
        }
        g.blit(FrameStreamer.getTextureId(), 0, y0, (int) w, y1, u0, u1, v0, v1);
    }

    /**
     * The button morph: source buttons split/merge into the target layout with a
     * per-button stagger. Target labels fade in as they arrive.
     */
    private static void drawMorph(GuiGraphics g, float e) {
        if (!morphLogged) {
            morphLogged = true;
            ReimaginedIntro.LOGGER.info("[ReimaginedIntro/FLUID] morph rendering: {} targets", morphTargets.size());
        }
        int m = morphSources.size();
        int n = morphTargets.size();
        if (m == 0 || n == 0) return;

        long total = durationMs;
        float stagger = Math.min(35.0f, total * 0.25f / n);

        for (int j = 0; j < n; j++) {
            // Contiguous source block for this target: merges extras (m > n) and
            // splits shared sources (m < n) by index fraction.
            int a = (int) Math.floor((double) j * m / n);
            int b = (int) Math.floor((double) (j + 1) * m / n);
            if (b <= a) {
                a = Math.min(a, m - 1);
                b = a + 1;
            }

            float sx = 0, sy = 0, sw = 0, sh = 0;
            for (int k = a; k < b; k++) {
                int[] r = morphSources.get(k);
                sx += r[0] + r[2] / 2.0f;
                sy += r[1] + r[3] / 2.0f;
                sw += r[2];
                sh += r[3];
            }
            int cnt = b - a;
            sx /= cnt;
            sy /= cnt;
            sw /= cnt;
            sh /= cnt;

            AbstractWidget target = morphTargets.get(j);
            float tx = target.getX() + target.getWidth() / 2.0f;
            float ty = target.getY() + target.getHeight() / 2.0f;
            float tw = target.getWidth();
            float th = target.getHeight();

            // Per-button staggered progress.
            float delay = j * stagger;
            float span = Math.max(1.0f, total - (n - 1) * stagger);
            float pj = clamp01((e * total - delay) / span);
            float ej = ease(pj);

            float cx = sx + (tx - sx) * ej;
            float cy = sy + (ty - sy) * ej;
            float bw = sw + (tw - sw) * ej;
            float bh = sh + (th - sh) * ej;

            // Arc: gentle lift mid-flight so the travel is clearly readable.
            cy -= Math.sin(pj * (float) Math.PI) * 14.0f;
            // Alpha valley: slightly translucent mid-flight, solid on arrival.
            float fly = 1.0f - 0.28f * (float) Math.sin(pj * (float) Math.PI);

            // Render the REAL target widget (themed texture, slider track, lock
            // icon...) at the interpolated rect instead of a synthetic glass
            // ghost: the last in-flight frame is then pixel-identical to the
            // settled screen - no style pop at the end of the flight, and
            // icon buttons no longer leak their message text.
            // NOTE: keep rendering even after pj reaches 1.0 - the real widget
            // stays gated (hidden) until the whole transition resets, so stopping
            // early would leave arrived buttons INVISIBLE for a few frames
            // (that was the end-of-flight flash). At pj=1 the rect/alpha are
            // exactly the settled ones, so this is seamless.
            {
                int ox = target.getX(), oy = target.getY();
                flightRendering = true;
                int ow = target.getWidth(), oh2 = target.getHeight();
                boolean ovis = target.visible;
                target.visible = true;
                target.setAlpha(fly);
                target.setPosition(Math.round(cx - bw / 2.0f), Math.round(cy - bh / 2.0f));
                target.setWidth(Math.round(bw));
                target.setHeight(Math.round(bh));
                try {
                    target.render(g, -10000, -10000, 0.0f); // mouse far away: no hover highlight
                } finally {
                    target.setPosition(ox, oy);
                    target.setWidth(ow);
                    target.setHeight(oh2);
                    target.visible = ovis;
                    target.setAlpha(1.0f);
                    flightRendering = false;
                }
            }
        }
    }

    private static float progress() {
        long elapsed = Util.getMillis() - startMs;
        if (elapsed >= durationMs) return 1.0f;
        return (float) elapsed / (float) durationMs;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    /** Cubic ease-in-out for a smoother feel. */
    private static float ease(float t) {
        return t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3.0f) / 2.0f;
    }

    private static Type configuredType() {
        String raw = ModConfig.get().menuTransition;
        if (raw == null || raw.isEmpty()) return Type.FLUID;
        try {
            return Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Type.FLUID;
        }
    }

    /** Current configured type, for the config screen's cycle button. */
    public static Type currentType() {
        return configuredType();
    }

    private static long configuredDuration() {
        return Math.max(50, Math.min(1500, ModConfig.get().menuTransitionDurationMs));
    }
}
