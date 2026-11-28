package noobtrieu.reimaginedintro.client.transition;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.FrameStreamer;

/**
 * The reveal half of the enter-world transition. Opens at the vanilla
 * "world ready" moment (screen cleared). While open the game world renders
 * behind it (normal in-game screen behaviour), so:
 *  - VIDEO: if the reversed intro had time left, finish it fullscreen first;
 *  - LOGO HOLD: the mojang-logo-page look, except the logo's rect is a hole
 *    showing the LIVE game (the user's "mojang logo replaced by the game
 *    screen");
 *  - EXPAND: the hole eases out to fullscreen, then the screen closes and
 *    the game is in.
 * Pure fills around the hole - VulkanMod-safe by construction.
 */
public class GameRevealScreen extends Screen {
    private static final long HOLD_MS = 400;
    private static final long EXPAND_MS = 900;

    private final long videoRemainingMs;
    private final long startMs = Util.getMillis();
    private boolean finished;

    public GameRevealScreen(long videoRemainingMs) {
        super(Component.literal("Entering world"));
        this.videoRemainingMs = videoRemainingMs;
    }

    @Override
    protected void init() {
        super.init();
        finished = false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Transparent: the live world behind IS the content.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long elapsed = Util.getMillis() - startMs;

        if (elapsed < videoRemainingMs) {
            // Finish the reversed intro fullscreen first.
            long introElapsed = FrameStreamer.INTRO_TOTAL_DURATION_MS - (videoRemainingMs - elapsed);
            FrameStreamer.updateReversed(introElapsed);
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
            return;
        }

        long after = elapsed - videoRemainingMs;
        float expand = clamp01((after - HOLD_MS) / (float) EXPAND_MS);
        float e = 1.0f - (float) Math.pow(1.0f - expand, 3.0f); // ease-out cubic

        int w = (int) g.guiWidth();
        int h = (int) g.guiHeight();
        int[] rect = EnterWorldTransition.logoRect(w, h);
        int px = Math.round(rect[0] * (1.0f - e));
        int py = Math.round(rect[1] * (1.0f - e));
        int pw = rect[2] + Math.round((w - rect[2]) * e);
        int ph = rect[3] + Math.round((h - rect[3]) * e);

        // Four bands around the hole: the logo-page backdrop. The hole shows
        // the live game through to the framebuffer.
        int backdrop = 0xFF0E0705;
        g.fill(0, 0, w, py, backdrop);
        g.fill(0, py + ph, w, h, backdrop);
        g.fill(0, py, px, py + ph, backdrop);
        g.fill(px + pw, py, w, py + ph, backdrop);

        if (expand < 1.0f) {
            // Hole border so the expanding window reads while it grows
            int border = ((int) (0x60 * (1.0f - e))) << 24 | 0xFFFFFF;
            g.fill(px, py, px + pw, py + 1, border);
            g.fill(px, py + ph - 1, px + pw, py + ph, border);
            g.fill(px, py, px + 1, py + ph, border);
            g.fill(px + pw - 1, py, px + pw, py + ph, border);
        }

        if (expand >= 1.0f && !finished) {
            finished = true;
            FrameStreamer.warmupLoop(); // ready for a later return to the menu
            onClose();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC skips the ceremony
        if (event.key() == 256) {
            finished = true;
            FrameStreamer.warmupLoop();
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }
}
