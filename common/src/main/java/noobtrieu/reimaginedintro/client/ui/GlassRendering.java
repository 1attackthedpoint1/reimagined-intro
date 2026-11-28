package noobtrieu.reimaginedintro.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Liquid-glass button rendering, capsule edition. User directive: TRANSPARENT
 * body with VERY round (full capsule) corners. Recipe:
 * - full capsule silhouette (radius = min(w,h)/2) via scanline circles;
 * - highly transparent bright body (the background clearly shows through);
 * - a crisp bright rim ring hugging the capsule edge (light bending around
 *   the glass), strongest along the top;
 * - a subtle top sheen band; a faint dark halo just outside the rim lifts the
 *   glass off the background.
 * Pure GuiGraphics fills - VulkanMod-safe by construction.
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

    private static int boost(int color, float f) {
        int a = (color >>> 24) & 0xFF;
        int na = Math.min(255, Math.round(a * f));
        return (na << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Full capsule (stadium) fill: radius = min(w,h)/2. Correct scanline
     * geometry: for a WIDE rect every row spans from the LEFT cap's arc to the
     * RIGHT cap's arc (leftmost x+r-hw .. rightmost x+w-r+hw); TALL rects
     * transpose to per-column spans. (The previous version drew both caps at
     * the left end with an always-empty middle band - for wide buttons the
     * entire body was silently missing.)
     */
    public static void fillPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(w, h) / 2;
        if (r < 3) {
            fillRounded(g, x, y, w, h, color);
            return;
        }
        double rr = (double) r;
        if (w >= h) {
            // horizontal stadium: caps on the left/right ends
            for (int i = 0; i < h; i++) {
                double dy = (i + 0.5) - h / 2.0;
                double a = rr * rr - dy * dy;
                if (a <= 0.0) continue;
                double hw = Math.sqrt(a);
                int x0 = (int) Math.floor(x + r - hw);
                int x1 = (int) Math.ceil(x + w - r + hw);
                if (x1 > x0) g.fill(x0, y + i, x1, y + i + 1, color);
            }
        } else {
            // vertical stadium: caps on the top/bottom ends
            for (int j = 0; j < w; j++) {
                double dx = (j + 0.5) - w / 2.0;
                double a = rr * rr - dx * dx;
                if (a <= 0.0) continue;
                double hw = Math.sqrt(a);
                int y0 = (int) Math.floor(y + r - hw);
                int y1 = (int) Math.ceil(y + h - r + hw);
                if (y1 > y0) g.fill(x + j, y0, x + j + 1, y1, color);
            }
        }
    }

    /** Legacy 3px staircase rounded rect (grooves, small elements). */
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
     * Liquid glass button panel - TRANSPARENT capsule edition. Same signature
     * the mixins and the morph renderer call.
     */
    public static void drawButtonPanel(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        float hv = hovered ? 1.3f : 1.0f;
        int r = Math.max(3, Math.min(w, h) / 2);

        // 1. Faint dark halo just outside the rim: lifts the glass off the bg.
        fillPill(g, x - 1, y - 1, w + 2, h + 2, scaled(argb(0x22, 0x00, 0x0A, 0x14), alpha));

        // 2. Rim ring: a crisp bright capsule edge (light bends around it).
        fillPill(g, x, y, w, h, scaled(boost(argb(0xB8, 0xFF, 0xFF, 0xFF), hv), alpha));

        // 3. Highly transparent body inset 1px - background clearly shows through.
        fillPill(g, x + 1, y + 1, w - 2, h - 2, scaled(boost(argb(0x2C, 0xFF, 0xFF, 0xFF), hv), alpha));

        // 4. Top sheen: brighter band hugging the top inside the rim.
        if (w > h && h >= 8) {
            fillPill(g, x + r / 2, y + 1, w - r, 2, scaled(boost(argb(0x50, 0xFF, 0xFF, 0xFF), hv), alpha));
            // left glint where the lens catches the light
            if (w >= 40) {
                fillPill(g, x + 2, y + h / 2 - 2, Math.min(w / 5, 2 + r), 4, scaled(boost(argb(0x2E, 0xFF, 0xFF, 0xFF), hv), alpha));
            }
        }

        if (!active) {
            fillPill(g, x, y, w, h, scaled(argb(0x40, 0x10, 0x14, 0x18), alpha));
        }
    }

    /** Liquid glass slider: recessed groove + transparent capsule handle. */
    public static void drawSlider(GuiGraphics g, int x, int y, int w, int h, double value, boolean active, boolean hovered, float alpha) {
        float hv = hovered ? 1.3f : 1.0f;
        int cy = y + h / 2;
        // Recessed groove
        fillRounded(g, x + 2, cy - 3, w - 4, 6, scaled(argb(0x60, 0x14, 0x18, 0x20), alpha));
        if (w > 8) {
            fillRounded(g, x + 3, cy - 2, w - 6, 4, scaled(argb(0x24, 0xFF, 0xFF, 0xFF), alpha));
        }
        // Transparent capsule handle (8 wide)
        int hx = x + (int) (Math.max(0.0, Math.min(1.0, value)) * (w - 8));
        fillPill(g, hx - 1, y - 1, 10, h + 2, scaled(argb(0x22, 0x00, 0x0A, 0x14), alpha)); // halo
        fillPill(g, hx, y, 8, h, scaled(boost(argb(0xB8, 0xFF, 0xFF, 0xFF), hv), alpha)); // rim
        if (h >= 6) {
            fillPill(g, hx + 1, y + 1, 6, h - 2, scaled(boost(argb(0x2C, 0xFF, 0xFF, 0xFF), hv), alpha)); // body
            fillPill(g, hx + 2, y + 1, 4, 2, scaled(boost(argb(0x50, 0xFF, 0xFF, 0xFF), hv), alpha)); // sheen
        }
        if (!active) {
            fillPill(g, x, y, w, h, scaled(argb(0x40, 0x10, 0x14, 0x18), alpha));
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
