package noobtrieu.reimaginedintro.client.transition;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

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
 * All drawing goes through GuiGraphics pose (JOML Matrix3x2fStack) and fill(),
 * which is the VulkanMod-safe path.
 */
public final class ScreenTransitions {
    public enum Type {
        SLIDE,     // two-phase push: old slides left, then new slides in from the right
        SLIDE_UP,  // same, vertically
        COVER,     // old stays put (no phase-A transform), new slides over from the right
        FADE,      // fade through black
        ZOOM,      // old scales back slightly + fade, new scales up from 92%
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

        // A new swap arriving mid-transition: resolve it by finishing instantly,
        // then the caller's setScreen proceeds normally (no new animation).
        if (phase != Phase.IDLE) {
            abandon();
            return false;
        }

        Type t = configuredType();
        if (t == Type.OFF) return false;
        long d = configuredDuration();
        if (d <= 0) return false;

        pending = next;
        type = t;
        durationMs = d;
        startMs = Util.getMillis();
        phase = Phase.A;
        return true;
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
        if (phase == Phase.IDLE) return false;
        // Safety valve: never wedge input if the render hook stopped firing.
        if (Util.getMillis() - startMs > durationMs + 1000L) {
            abandon();
            return false;
        }
        return true;
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
                // Phase A: the outgoing screen is active; it may already be beyond
                // its half of the animation if the frame is late — that's fine,
                // the swap happens on the tick logic, not here.
                if (p < 0.5f && self == MinecraftAccessorBridge.getActiveScreen()) {
                    applyOutgoing(g, w, h, clamp01(p * 2.0f));
                }
                // (p >= 0.5f in phase A only if the swap callback hasn't run yet;
                // render untouched — one static frame before the swap.)
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

    /** ScreenMixin TAIL — restores the pose pushed by the head hook. */
    public static void onRenderTail(Screen self, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (phase == Phase.IDLE) return;
        if (posePushed) {
            g.pose().popMatrix();
            posePushed = false;
        }
        if (curtainAlpha > 0) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (curtainAlpha * 255.0f)) << 24);
            curtainAlpha = 0.0f;
        }
        maybeAdvance();
    }

    private static boolean posePushed;
    private static float curtainAlpha;

    private static void applyOutgoing(GuiGraphics g, float w, float h, float e) {
        switch (type) {
            case SLIDE, COVER -> {
                // COVER leaves the old screen perfectly still.
                if (type == Type.COVER) return;
                g.fill(0, 0, (int) w, (int) h, 0xFF000000); // substrate for the exposed edge
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
                curtainAlpha = 0.35f * e; // slight dim alongside the scale-back
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
            // Swap point: perform the real setScreen; phase B renders the new screen.
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
        if (raw == null || raw.isEmpty()) return Type.SLIDE;
        try {
            return Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Type.SLIDE;
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
