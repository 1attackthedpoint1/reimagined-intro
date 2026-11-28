package noobtrieu.reimaginedintro.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Frosted-glass rounded button rendering, assembled purely from GuiGraphics.fill
 * rectangles (two-step rounded corners). Stays inside the vanilla GUI pipeline,
 * so it is VulkanMod-safe by construction.
 */
public final class GlassRendering {
    private GlassRendering() {
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int scaled(int color, float alpha) {
        int a = (color >>> 24) & 0xFF;
        int na = Math.round(a * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (na << 24) | (color & 0x00FFFFFF);
    }

    /** Rounded rectangle (radius ~3px, two-step staircase) via fill rectangles. */
    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        if (h < 6 || w < 6) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 3, y, x + w - 3, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, color);
        g.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
    }

    /**
     * Frosted glass button panel: dark outer base, translucent white glass body,
     * bright top highlight, dark bottom shade. Hover brightens the glass.
     */
    public static void drawButtonPanel(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        // Dark outer base (also acts as the rounded border)
        fillRounded(g, x, y, w, h, scaled(argb(0xB4, 0, 0, 0), alpha));
        // Translucent white glass body
        int body = hovered ? argb(0x6E, 0xFF, 0xFF, 0xFF) : argb(0x46, 0xFF, 0xFF, 0xFF);
        if (h >= 4 && w >= 4) {
            fillRounded(g, x + 1, y + 1, w - 2, h - 2, scaled(body, alpha));
        }
        // Top highlight line (glass specular)
        if (h >= 6 && w >= 8) {
            g.fill(x + 4, y + 1, x + w - 4, y + 2, scaled(argb(0x8C, 0xFF, 0xFF, 0xFF), alpha));
        }
        // Bottom shade line
        if (h >= 6 && w >= 8) {
            g.fill(x + 4, y + h - 2, x + w - 4, y + h - 1, scaled(argb(0x4D, 0x00, 0x00, 0x00), alpha));
        }
        if (!active) {
            // Disabled: dim everything down
            fillRounded(g, x, y, w, h, scaled(argb(0x73, 0x00, 0x00, 0x00), alpha));
        }
    }

    /** Frosted glass slider: recessed track + rounded glass handle. */
    public static void drawSlider(GuiGraphics g, int x, int y, int w, int h, double value, boolean active, boolean hovered, float alpha) {
        int cy = y + h / 2;
        // Recessed track (dark groove + subtle inner light)
        fillRounded(g, x + 2, cy - 3, w - 4, 6, scaled(argb(0x8C, 0x00, 0x00, 0x00), alpha));
        if (w > 8) {
            fillRounded(g, x + 3, cy - 2, w - 6, 4, scaled(argb(0x2E, 0xFF, 0xFF, 0xFF), alpha));
        }
        // Rounded glass handle (8 wide, full height like vanilla)
        int hx = x + (int) (Math.max(0.0, Math.min(1.0, value)) * (w - 8));
        fillRounded(g, hx, y, 8, h, scaled(argb(0xC4, 0x00, 0x00, 0x00), alpha));
        if (h >= 4) {
            int handle = hovered ? argb(0x87, 0xFF, 0xFF, 0xFF) : argb(0x5A, 0xFF, 0xFF, 0xFF);
            fillRounded(g, hx + 1, y + 1, 6, h - 2, scaled(handle, alpha));
        }
        if (h >= 6) {
            g.fill(hx + 3, y + 1, hx + 5, y + 2, scaled(argb(0xA0, 0xFF, 0xFF, 0xFF), alpha));
        }
        if (!active) {
            fillRounded(g, x, y, w, h, scaled(argb(0x73, 0x00, 0x00, 0x00), alpha));
        }
    }
}
