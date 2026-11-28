package noobtrieu.reimaginedintro.client.transition;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.config.ModConfig;

/**
 * Enter-world reversed-intro transition: the user asked for entering a world
 * to play the STARTUP sequence backwards - main menu -> reversed cave morph
 * (the intro video played 161 -> 1) -> the Mojang-logo-style loading page,
 * except the logo is replaced by the game view, which then expands to fill the
 * screen once the world is ready.
 *
 * Phases (the active screen is LevelLoadingScreen underneath, fully covered):
 *  - VIDEO: reversed intro video fullscreen for the intro's full duration;
 *  - LOGO HOLD: dark loading-page look with a centered panel (world loading
 *    still going on underneath);
 * then the vanilla "world ready" setScreen(null) moment swaps in
 * GameRevealScreen, which finishes any remaining video, holds the logo page
 * (with the LIVE GAME visible in the logo's rect), and expands it.
 */
public final class EnterWorldTransition {
    private EnterWorldTransition() {
    }

    private static final long VIDEO_MS = FrameStreamer.INTRO_TOTAL_DURATION_MS;

    private static boolean armed;   // LevelLoadingScreen was seen; overlay may start
    private static boolean active;  // overlay is rendering
    private static long startMs;

    /** Arms the transition. Returns true if the caller should skip other menu transitions. */
    public static boolean arm() {
        if (!ModConfig.get().reverseIntroOnWorldEnter) return false;
        armed = true;
        active = false;
        return true;
    }

    public static boolean isArmed() {
        return armed;
    }

    /** ScreenMixin TAIL: draws the overlay over the vanilla level-loading screen. */
    public static void onScreenRenderTail(Screen screen, GuiGraphics g) {
        if (!armed) return;
        if (!(screen instanceof LevelLoadingScreen)) {
            // The load was cancelled/failed and another screen took over - disarm
            // silently so we never overlay unrelated screens.
            armed = false;
            active = false;
            return;
        }
        if (!active) {
            active = true;
            startMs = Util.getMillis();
            FrameStreamer.startReverse();
        }

        long elapsed = Util.getMillis() - startMs;
        if (elapsed < VIDEO_MS) {
            FrameStreamer.updateReversed(elapsed);
            drawCoverVideo(g);
        } else {
            // Logo page hold: the video reached its (reversed) end = the
            // mojang-logo-style page. Draw the page chrome around the panel.
            FrameStreamer.updateReversed(VIDEO_MS);
            drawLogoPage(g, screen, elapsed);
        }
    }

    /**
     * MinecraftMixin setScreen TAIL: the vanilla world-ready moment clears the
     * screen. If our overlay was covering the load, substitute the reveal
     * screen instead so the game stays masked until the logo page expands.
     */
    public static void onSetScreenDone(Screen newScreen) {
        if (newScreen != null || !armed) return;
        armed = false;
        boolean wasActive = active;
        active = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return; // not a world-ready moment

        long videoRemaining = wasActive ? Math.max(0, VIDEO_MS - (Util.getMillis() - startMs)) : VIDEO_MS;
        // never actually call setScreen from inside the mixin if we're not in
        // a sane state; GameRevealScreen guards its own lifecycle
        mc.setScreen(new GameRevealScreen(videoRemaining));
    }

    /** Fullscreen 16:9 cover-crop blit of the current (reversed) intro frame. */
    private static void drawCoverVideo(GuiGraphics g) {
        float w = g.guiWidth();
        float h = g.guiHeight();
        float aspect = 16.0f / 9.0f;
        int x, y, rw, rh;
        if (w / h > aspect) {
            rw = (int) w;
            rh = Math.round(w / aspect);
            x = 0;
            y = (int) ((h - rh) / 2.0f);
        } else {
            rh = (int) h;
            rw = Math.round(h * aspect);
            x = (int) ((w - rw) / 2.0f);
            y = 0;
        }
        g.blit(FrameStreamer.getTextureId(), x, y, rw, rh, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    /**
     * The mojang-logo-page look during the hold: near-black maroon backdrop,
     * centered 16:9 panel where the logo/game view sits (still dark - the
     * world is not rendering yet while the loading screen is up), with a
     * subtle pulsing border so it reads as a loading page.
     */
    private static void drawLogoPage(GuiGraphics g, Screen screen, long elapsedMs) {
        int w = (int) g.guiWidth();
        int h = (int) g.guiHeight();
        g.fill(0, 0, w, h, 0xFF0E0705);

        int[] rect = logoRect(w, h);
        int px = rect[0], py = rect[1], pw = rect[2], ph = rect[3];
        // Panel body: darker still (the game view arrives with the reveal)
        g.fill(px, py, px + pw, py + ph, 0xFF090404);
        // Pulsing border - the loading page alive
        float pulse = 0.5f + 0.5f * (float) Math.sin((elapsedMs % 2400) / 2400.0 * Math.PI * 2);
        int border = ((int) (0x38 + 0x30 * pulse)) << 24 | 0xFFFFFF;
        g.fill(px, py, px + pw, py + 1, border);
        g.fill(px, py + ph - 1, px + pw, py + ph, border);
        g.fill(px, py, px + 1, py + ph, border);
        g.fill(px + pw - 1, py, px + pw, py + ph, border);
    }

    /** The centered rect the "logo" (game view) occupies - shared with the reveal. */
    static int[] logoRect(int screenW, int screenH) {
        int pw = Math.round(screenW * 0.55f);
        int ph = Math.round(pw * 9.0f / 16.0f);
        if (ph > screenH * 0.9f) {
            ph = Math.round(screenH * 0.9f);
            pw = Math.round(ph * 16.0f / 9.0f);
        }
        return new int[]{(screenW - pw) / 2, (screenH - ph) / 2, pw, ph};
    }
}
