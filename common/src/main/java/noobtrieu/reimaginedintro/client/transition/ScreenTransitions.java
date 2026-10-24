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
 * Menu transition animations between screens (e.g. TitleScreen -> SelectWorldScreen).
 *
 * The outgoing screen keeps rendering for the duration of the animation while the
 * incoming screen is transformed in with a pose (JOML Matrix3x2fStack) — everything
 * goes through the vanilla GUI pipeline (fill/pose/render), so this is fully
 * VulkanMod-compatible (no GL calls, no framebuffers, no alpha tricks needed).
 */
public final class ScreenTransitions {
    public enum Type {
        SLIDE,     // horizontal push: old goes left, new comes from the right
        SLIDE_UP,  // vertical push: old goes up, new comes from the bottom
        COVER,     // new screen slides over a static old screen
        FADE,      // fade through black (two-phase curtain)
        ZOOM,      // depth: old shrinks slightly, new scales up from 92%
        OFF
    }

    private static Screen outgoing;
    private static Screen incoming;
    private static Type type = Type.OFF;
    private static long durationMs = 300;
    private static long startMs;
    private static boolean posePushed;

    private ScreenTransitions() {
    }

    /** Called from MinecraftMixin at the HEAD of Minecraft.setScreen. */
    public static void onSetScreen(Screen current, Screen next) {
        finish();
        if (current == null || next == null || current == next) return;
        // The intro handles its own cinematic transitions — never animate it.
        if (current instanceof IntroScreen || next instanceof IntroScreen) return;

        Type t = configuredType();
        if (t == Type.OFF) return;
        long d = configuredDuration();
        if (d <= 0) return;

        outgoing = current;
        incoming = next;
        type = t;
        durationMs = d;
        startMs = Util.getMillis();
    }

    /** While a transition is in flight, all menu input is swallowed. */
    public static boolean isBlockingInput() {
        if (incoming == null) return false;
        // Safety valve: never wedge input if the render hook somehow stopped firing.
        if (Util.getMillis() - startMs > durationMs + 1000L) {
            finish();
            return false;
        }
        return true;
    }

    /** ScreenMixin render HEAD — draws the outgoing screen and pre-transforms the incoming one. */
    public static void onRenderHead(Screen self, GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (self != incoming) return;

        float p = progress();
        if (p >= 1.0f) {
            finish();
            return;
        }

        float e = p * p * (3.0f - 2.0f * p); // smoothstep easing
        int w = g.guiWidth();
        int h = g.guiHeight();

        switch (type) {
            case SLIDE -> {
                if (renderOutgoing(g, mouseX, mouseY, partialTick, -w * e, 0.0f, 1.0f)) {
                    g.pose().pushMatrix();
                    g.pose().translate(w * (1.0f - e), 0.0f);
                    posePushed = true;
                }
            }
            case SLIDE_UP -> {
                if (renderOutgoing(g, mouseX, mouseY, partialTick, 0.0f, -h * e, 1.0f)) {
                    g.pose().pushMatrix();
                    g.pose().translate(0.0f, h * (1.0f - e));
                    posePushed = true;
                }
            }
            case COVER -> {
                if (renderOutgoing(g, mouseX, mouseY, partialTick, 0.0f, 0.0f, 1.0f)) {
                    g.pose().pushMatrix();
                    g.pose().translate(w * (1.0f - e), 0.0f);
                    posePushed = true;
                }
            }
            case FADE -> {
                if (p < 0.5f) {
                    // First half: show old screen, darken towards black, hide the incoming render.
                    renderOutgoing(g, mouseX, mouseY, partialTick, 0.0f, 0.0f, 1.0f);
                    int a = (int) Math.min(255.0f, 255.0f * (p * 2.0f));
                    g.fill(0, 0, w, h, (a << 24));
                    ci.cancel();
                }
                // Second half: incoming renders normally; the tail draws a fading curtain.
            }
            case ZOOM -> {
                g.fill(0, 0, w, h, 0xFF000000);
                if (renderOutgoing(g, mouseX, mouseY, partialTick, 0.0f, 0.0f, 1.0f - 0.06f * e)) {
                    float s = 0.92f + 0.08f * e;
                    g.pose().pushMatrix();
                    g.pose().translate(w / 2.0f, h / 2.0f);
                    g.pose().scale(s, s);
                    g.pose().translate(-w / 2.0f, -h / 2.0f);
                    posePushed = true;
                }
            }
            default -> {
            }
        }
    }

    /** ScreenMixin render TAIL — pops the incoming transform / draws the fade curtain. */
    public static void onRenderTail(Screen self, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (self != incoming) return;

        if (posePushed) {
            g.pose().popMatrix();
            posePushed = false;
        }

        float p = progress();
        if (p >= 1.0f) {
            finish();
            return;
        }

        if (type == Type.FADE && p >= 0.5f) {
            int a = (int) Math.min(255.0f, 255.0f * (1.0f - (p - 0.5f) * 2.0f));
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24));
        }
    }

    private static boolean renderOutgoing(GuiGraphics g, int mouseX, int mouseY, float partialTick, float dx, float dy, float scale) {
        Screen out = outgoing;
        if (out == null) return false;

        boolean failed = false;
        g.pose().pushMatrix();
        try {
            if (scale != 1.0f) {
                float w = g.guiWidth();
                float h = g.guiHeight();
                g.pose().translate(w / 2.0f, h / 2.0f);
                g.pose().scale(scale, scale);
                g.pose().translate(-w / 2.0f, -h / 2.0f);
            }
            g.pose().translate(dx, dy);
            // Through the same wrapper the game uses (background + widgets + deferred).
            out.renderWithTooltipAndSubtitles(g, mouseX, mouseY, partialTick);
        } catch (Throwable t) {
            ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Outgoing screen failed while rendering a menu transition; ending it.", t);
            failed = true;
        } finally {
            g.pose().popMatrix();
        }
        return !failed;
    }

    private static float progress() {
        long elapsed = Util.getMillis() - startMs;
        if (elapsed >= durationMs) return 1.0f;
        return (float) elapsed / (float) durationMs;
    }

    private static void finish() {
        outgoing = null;
        incoming = null;
        type = Type.OFF;
        posePushed = false;
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
