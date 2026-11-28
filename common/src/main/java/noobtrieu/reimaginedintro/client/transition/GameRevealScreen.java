package noobtrieu.reimaginedintro.client.transition;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.FrameStreamer;

/**
 * The reveal half of the enter-world transition. Opens at the vanilla
 * "world ready" moment; the live game renders behind it (normal screen
 * behaviour), so:
 *  - VIDEO: if the reversed intro had time left, finish it first - pre-logo
 *    frames fullscreen, logo-page frames in bands around the logo rect;
 *  - LOGO HOLD: the logo page with the logo rect LEFT TRANSPARENT - the
 *    live entering game shows exactly where the Mojang logo would be
 *    ("mojang logo replaced by the game view");
 *  - EXPAND: the window eases out from the logo rect to fullscreen, then
 *    the screen closes and the game is in. ESC skips the ceremony.
 * Pure fills and blits - VulkanMod-safe by construction.
 */
public class GameRevealScreen extends Screen {
    private static final long HOLD_MS = 500;
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
        int w = (int) g.guiWidth();
        int h = (int) g.guiHeight();

        FrameStreamer.updateReversed(); // freeze-with-the-game clock
        long remaining = FrameStreamer.reversedRemainingMs();
        if (remaining > 0) {
            // Finish the reversed intro first. Logo-page frames are drawn in
            // bands so the logo rect stays open - the live game is already
            // visible behind this screen.
            EnterWorldTransition.drawReversedFrame(g, false);
            return;
        }

        long after = elapsed - videoRemainingMs;
        float expand = clamp01((after - HOLD_MS) / (float) EXPAND_MS);
        float e = 1.0f - (float) Math.pow(1.0f - expand, 3.0f); // ease-out cubic

        int[] lr = EnterWorldTransition.logoScreenRect(w, h);
        int x0 = Math.round(lr[0] * (1.0f - e));
        int y0 = Math.round(lr[1] * (1.0f - e));
        int x1 = lr[2] + Math.round((w - lr[2]) * e);
        int y1 = lr[3] + Math.round((h - lr[3]) * e);

        // The logo page keeps playing as the backdrop (held final frame),
        // in bands around the EXPANDING window so the video shrinks away in
        // sync as the live game takes over. Window left transparent.
        EnterWorldTransition.drawVideoBandsAround(g, x0, y0, x1, y1);

        if (expand < 1.0f) {
            int border = ((int) (0x60 * (1.0f - e))) << 24 | 0xFFFFFF;
            g.fill(x0, y0, x1, y0 + 1, border);
            g.fill(x0, y1 - 1, x1, y1, border);
            g.fill(x0, y0, x0 + 1, y1, border);
            g.fill(x1 - 1, y0, x1, y1, border);
        } else if (!finished) {
            finished = true;
            FrameStreamer.warmupLoop(); // ready for a later return to the menu
            onClose();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC skips straight into the game
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
