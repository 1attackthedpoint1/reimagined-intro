package noobtrieu.reimaginedintro.client.transition;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.config.ModConfig;

/**
 * Enter-world reversed-intro transition, 3.7.7-base edition.
 *
 * Request: entering a world plays the startup sequence BACKWARDS - menu ->
 * un-morphing cave -> back INTO the logo page - but the Mojang logo is never
 * shown: from the moment the reversed video reaches the logo-page frames, the
 * logo's rect is covered by a dark panel (world still loading) that becomes a
 * LIVE WINDOW into the entering game once the world is ready, then expands to
 * fullscreen.
 *
 * Logo page measured from the intro frames (2560x1440): frames 1..76 are one
 * STATIC full-red page with white text at u 0.223..0.783 / v 0.389..0.539;
 * the morph away starts at ~77. The replacement window is a large centered
 * 16:9 rect (u 0.17..0.83 / v 0.278..0.650) containing the text.
 */
public final class EnterWorldTransition {
    private EnterWorldTransition() {
    }

    /** Last frame of the static logo page (morph starts after this). */
    public static final int LOGO_LAST_FRAME = 76;
    /**
     * Window rect in video UV that replaces the Mojang mark. The logo page is
     * a FULL-RED screen with white text at u 0.223..0.783 / v 0.389..0.539,
     * so a white-text-sized window reads as a tiny patch. This is a large
     * centered 16:9 window (66% wide, 37% tall, red band left as the page
     * frame) that fully contains the text with generous margin - the "logo
     * replaced by a big screen showing the game" look.
     */
    public static final float LOGO_U0 = 0.17f, LOGO_U1 = 0.83f;
    public static final float LOGO_V0 = 0.278f, LOGO_V1 = 0.650f;

    private static boolean armed;   // LevelLoadingScreen was seen; overlay may run
    private static boolean active;  // overlay is rendering
    private static long startMs;

    /** Arms the transition if enabled. Returns true when armed. */
    public static boolean arm() {
        if (!ModConfig.get().reverseIntroOnWorldEnter) return false;
        armed = true;
        active = false;
        // Kick the background decode right at the click - a few hundred ms
        // before the loading screen's first render, for free.
        FrameStreamer.preDecodeReversed();
        return true;
    }

    public static boolean isArmed() {
        return armed;
    }

    /**
     * ScreenMixin TAIL: draws the overlay over the vanilla level-loading
     * screen. The vanilla screen is fully covered by the reversed video.
     */
    public static void onScreenRenderTail(Screen screen, GuiGraphics g) {
        if (!armed) return;
        if (!(screen instanceof LevelLoadingScreen)) {
            // Load cancelled/failed and another screen took over - disarm
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
        FrameStreamer.updateReversed(elapsed);
        drawReversedFrame(g, elapsed, true);
    }

    /**
     * MinecraftMixin setScreen TAIL: the vanilla world-ready moment clears the
     * screen. If our overlay was covering the load, hand the remaining
     * ceremony to the reveal screen so the game stays masked behind the logo
     * page until the window expands.
     */
    public static void onSetScreenDone(Screen newScreen) {
        if (newScreen != null || !armed) return;
        armed = false;
        boolean wasActive = active;
        active = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return; // not a world-ready moment

        long videoRemaining = wasActive ? Math.max(0, FrameStreamer.INTRO_TOTAL_DURATION_MS - (Util.getMillis() - startMs)) : FrameStreamer.INTRO_TOTAL_DURATION_MS;
        mc.setScreen(new GameRevealScreen(videoRemaining));
    }

    // ------------------------------------------------------------------ //

    /**
     * Cover-crop mapping (16:9 fill, no bars) as an affine screen->UV map:
     * u = (sx + m[0]) * m[2]; v = (sy + m[1]) * m[3]. Same math the intro
     * screen and drawVideoRegion use, just folded into one form.
     */
    private static float[] coverMap(int screenW, int screenH) {
        float aspect = 16.0f / 9.0f;
        if (screenW / (float) screenH > aspect) {
            // width-limited: full texture width spans the screen; v is offset.
            float vh = screenW / aspect;
            float vTop = (vh - screenH) / 2.0f;
            return new float[]{0.0f, vTop, 1.0f / screenW, 1.0f / vh};
        } else {
            // height-limited: full texture height spans the screen; u offset.
            float uw = screenH * aspect;
            float uLeft = (uw - screenW) / 2.0f;
            return new float[]{uLeft, 0.0f, 1.0f / uw, 1.0f / screenH};
        }
    }

    /**
     * The logo's rect on screen for the current cover-crop mapping, in
     * screen pixel coords {x0, y0, x1, y1}.
     */
    public static int[] logoScreenRect(int screenW, int screenH) {
        float[] m = coverMap(screenW, screenH);
        int x0 = Math.round(LOGO_U0 / m[2] - m[0]);
        int y0 = Math.round(LOGO_V0 / m[3] - m[1]);
        int x1 = Math.round(LOGO_U1 / m[2] - m[0]);
        int y1 = Math.round(LOGO_V1 / m[3] - m[1]);
        return new int[]{x0, y0, x1, y1};
    }

    /**
     * Blits the reversed video fullscreen, EXCLUDING the logo rect once the
     * reversed playback reaches the logo-page frames: the logo area is left
     * for the caller to fill (dark panel while loading, live game once the
     * world is up - GameRevealScreen just leaves it transparent).
     */
    static void drawReversedFrame(GuiGraphics g, long elapsed, boolean fillLogoWithPanel) {
        int w = (int) g.guiWidth();
        int h = (int) g.guiHeight();

        // Position-based phase: skip-aheads may put the picture on the logo
        // page before the wall clock gets there - trust the picture.
        if (!FrameStreamer.reversedAtLogoPage()) {
            // Pre-logo frames: fullscreen video, no window yet. One band over
            // the whole screen = exactly the intro screen's cover look.
            float[] m = coverMap(w, h);
            g.blit(FrameStreamer.getTextureId(), 0, 0, w, h,
                (0 + m[0]) * m[2], (w + m[0]) * m[2],
                (0 + m[1]) * m[3], (h + m[1]) * m[3]);
            return;
        }

        // Logo phase: video in four bands around the logo rect.
        int[] lr = logoScreenRect(w, h);
        int lx0 = clamp(lr[0], 0, w), ly0 = clamp(lr[1], 0, h);
        int lx1 = clamp(lr[2], 0, w), ly1 = clamp(lr[3], 0, h);
        float[] m = coverMap(w, h);
        drawBands(g, w, h, lx0, ly0, lx1, ly1, m);

        if (fillLogoWithPanel) {
            // Dark panel + pulsing border where the logo would be: the loading
            // page look, logo replaced by the (not yet live) game view.
            g.fill(lx0, ly0, lx1, ly1, 0xFF090404);
            float pulse = 0.5f + 0.5f * (float) Math.sin((elapsed % 2400) / 2400.0 * Math.PI * 2);
            int border = ((int) (0x38 + 0x30 * pulse)) << 24 | 0xFFFFFF;
            g.fill(lx0, ly0, lx1, ly0 + 1, border);
            g.fill(lx0, ly1 - 1, lx1, ly1, border);
            g.fill(lx0, ly0, lx0 + 1, ly1, border);
            g.fill(lx1 - 1, ly0, lx1, ly1, border);
        }
    }

    /**
     * Video blits for the regions above/below/left/right of the given window
     * rect (screen coords). The window itself is left untouched - the caller
     * either fills it (dark panel) or leaves it open (live game). Used both
     * for the fixed logo rect and the expanding reveal window.
     */
    static void drawVideoBandsAround(GuiGraphics g, int sx0, int sy0, int sx1, int sy1) {
        int w = (int) g.guiWidth();
        int h = (int) g.guiHeight();
        float[] m = coverMap(w, h);
        sx0 = clamp(sx0, 0, w); sx1 = clamp(sx1, 0, w);
        sy0 = clamp(sy0, 0, h); sy1 = clamp(sy1, 0, h);
        blitBand(g, 0, 0, w, sy0, m);      // top
        blitBand(g, 0, sy1, w, h, m);      // bottom
        blitBand(g, 0, sy0, sx0, sy1, m);  // left
        blitBand(g, sx1, sy0, w, sy1, m);  // right
    }

    /** Video blits for the regions above/below/left/right of the logo rect. */
    private static void drawBands(GuiGraphics g, int w, int h, int lx0, int ly0, int lx1, int ly1, float[] m) {
        // top band
        blitBand(g, 0, 0, w, ly0, m);
        // bottom band
        blitBand(g, 0, ly1, w, h, m);
        // left band
        blitBand(g, 0, ly0, lx0, ly1, m);
        // right band
        blitBand(g, lx1, ly0, w, ly1, m);
    }

    /** Blit the video restricted to a screen-rect band (cover-crop UV math). */
    private static void blitBand(GuiGraphics g, int sx0, int sy0, int sx1, int sy1, float[] m) {
        if (sx1 <= sx0 || sy1 <= sy0) return;
        float u0 = (sx0 + m[0]) * m[2];
        float u1 = (sx1 + m[0]) * m[2];
        float v0 = (sy0 + m[1]) * m[3];
        float v1 = (sy1 + m[1]) * m[3];
        g.blit(FrameStreamer.getTextureId(), sx0, sy0, sx1, sy1, u0, u1, v0, v1);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
