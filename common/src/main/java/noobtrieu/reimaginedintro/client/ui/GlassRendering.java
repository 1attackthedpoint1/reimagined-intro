package noobtrieu.reimaginedintro.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Liquid-glass button rendering (iOS 26 "thick lens" recipe, adapted to the
 * vanilla GUI pipeline): the body stays fairly clear through the flat middle,
 * light bends at the rim - expressed as an asymmetric specular rim (bright
 * top-left facing the light), a dark inner shadow along the bottom-right edge,
 * a vertical gradient body and a glossy sheen band. Everything is pure
 * GuiGraphics fills, so it stays VulkanMod-safe by construction.
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
     * Liquid glass button panel. Same signature the mixins and the morph
     * renderer already call, so the whole menu picks the new look up at once.
     */
    public static void drawButtonPanel(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        // Rim base: near-black ring, reads as the bent glass edge.
        fillRounded(g, x, y, w, h, scaled(argb(0xC0, 0x05, 0x08, 0x0C), alpha));

        // Glass body: vertical gradient - clear through the middle, denser at
        // the bottom (thick-lens look instead of uniform frost).
        int top = hovered ? argb(0x64, 0xFF, 0xFF, 0xFF) : argb(0x40, 0xFF, 0xFF, 0xFF);
        int bot = hovered ? argb(0x30, 0xB8, 0xD2, 0xE8) : argb(0x20, 0x8A, 0xA8, 0xC8);
        if (h >= 4 && w >= 4) {
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, scaled(top, alpha), scaled(bot, alpha));
        }

        // Asymmetric specular rim: bright edge facing the light (top + left).
        if (h >= 6 && w >= 8) {
            int rim = hovered ? argb(0xE0, 0xFF, 0xFF, 0xFF) : argb(0x96, 0xFF, 0xFF, 0xFF);
            g.fill(x + 4, y + 1, x + w - 4, y + 2, scaled(rim, alpha));            // top
            int leftRim = argb(hovered ? 0x70 : 0x46, 0xFF, 0xFF, 0xFF);
            g.fill(x + 1, y + 4, x + 2, y + h - 4, scaled(leftRim, alpha));        // left (weaker)
            // Dark inner shadow along the bottom-right (light bends away there).
            g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, scaled(argb(0x70, 0x00, 0x10, 0x18), alpha));
            g.fill(x + w - 2, y + 3, x + w - 1, y + h - 3, scaled(argb(0x48, 0x00, 0x10, 0x18), alpha));
        }

        // Glossy sheen: a soft light band just under the top rim.
        if (h >= 9 && w >= 10 && !hovered) {
            g.fill(x + 6, y + 3, x + w - 6, y + 4, scaled(argb(0x3C, 0xFF, 0xFF, 0xFF), alpha));
        }

        if (!active) {
            fillRounded(g, x, y, w, h, scaled(argb(0x6E, 0x00, 0x00, 0x00), alpha));
        }
    }

    /** Liquid glass slider: recessed groove + lens-like glass handle. */
    public static void drawSlider(GuiGraphics g, int x, int y, int w, int h, double value, boolean active, boolean hovered, float alpha) {
        int cy = y + h / 2;
        // Recessed groove (dark channel + faint inner light)
        fillRounded(g, x + 2, cy - 3, w - 4, 6, scaled(argb(0x96, 0x04, 0x08, 0x0C), alpha));
        if (w > 8) {
            fillRounded(g, x + 3, cy - 2, w - 6, 4, scaled(argb(0x28, 0xFF, 0xFF, 0xFF), alpha));
        }
        // Glass handle (8 wide): gradient body + bright top rim
        int hx = x + (int) (Math.max(0.0, Math.min(1.0, value)) * (w - 8));
        fillRounded(g, hx, y, 8, h, scaled(argb(0xC0, 0x05, 0x08, 0x0C), alpha));
        if (h >= 4) {
            int top = hovered ? argb(0x78, 0xFF, 0xFF, 0xFF) : argb(0x50, 0xFF, 0xFF, 0xFF);
            g.fillGradient(hx + 1, y + 1, hx + 7, y + h - 1, scaled(top, alpha), scaled(argb(0x24, 0x8A, 0xA8, 0xC8), alpha));
        }
        if (h >= 6) {
            g.fill(hx + 3, y + 1, hx + 5, y + 2, scaled(argb(0xB0, 0xFF, 0xFF, 0xFF), alpha));
        }
        if (!active) {
            fillRounded(g, x, y, w, h, scaled(argb(0x6E, 0x00, 0x00, 0x00), alpha));
        }
    }

    /**
     * Liquid neck between two blobs, drawn as a tapered slice strip along the
     * connecting line (used by the split/merge morph). (x0,y0)->(x1,y1) are the
     * CAP CENTERS in screen space; r0/r1 the cap radii; pinch 0..1 = how thin
     * the middle gets (0 = even bar, ~0.85 = thread).
     */
    public static void drawLiquidNeck(GuiGraphics g, float x0, float y0, float x1, float y1, float r0, float r1, float pinch, int bodyColor) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float capSum = r0 + r1;
        if (len <= capSum) return; // caps already touching - no neck needed
        float ang = (float) Math.atan2(dy, dx);
        float sx = x0 + (dx / len) * r0, sy = y0 + (dy / len) * r0; // neck start: source cap edge
        float neckLen = len - capSum;

        g.pose().pushMatrix();
        g.pose().translate(sx, sy);
        g.pose().rotate(ang); // 1.21.11 GUI poses are JOML 2D stacks - plain rotation
        // local frame: x axis along the neck, y centered
        int slices = 12;
        for (int i = 0; i < slices; i++) {
            float t = (i + 0.5f) / slices;
            // radius profile: matches the caps at both ends, pinched in the middle
            float r = (r0 + (r1 - r0) * t) * (1.0f - pinch * (float) Math.sin(Math.PI * t));
            float xA = t * neckLen;
            float xB = (i + 1.0f) / slices * neckLen;
            if (r < 0.5f) r = 0.5f;
            g.fill(Math.round(xA - 0.5f), Math.round(-r), Math.round(xB + 1.0f), Math.round(r + 0.5f), bodyColor);
        }
        g.pose().popMatrix();
    }
}
