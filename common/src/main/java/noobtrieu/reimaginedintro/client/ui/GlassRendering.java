package noobtrieu.reimaginedintro.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Liquid-glass button rendering (iOS 26 "thick lens" recipe), pure GuiGraphics
 * fills - VulkanMod-safe. The VLM critique of the first attempt nailed the
 * recipe error: glass is a BRIGHT material, not a dark one. So:
 * - bright translucent white body that takes the background colour through it
 *   (frosted tint), never a dark plastic base;
 * - full bright rim ring (light bends around the whole edge), strongest on the
 *   top-left where the light comes from, with a corner hotspot;
 * - big pill radius (~6px on 20px buttons) instead of 3px pixel steps;
 * - a soft dark frost halo just outside the rim fakes backdrop blur/darkening
 *   and lifts the glass off the background (suspension).
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
     * Rounded rectangle with a ~6px pill radius (4-step staircase). Falls back
     * to a plain fill for tiny rects.
     */
    public static void fillRounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        if (h < 12 || w < 12) { // small: 3px steps
            if (h < 6 || w < 6) { g.fill(x, y, x + w, y + h, color); return; }
            g.fill(x + 3, y, x + w - 3, y + 1, color);
            g.fill(x + 1, y + 1, x + w - 1, y + 3, color);
            g.fill(x, y + 3, x + w, y + h - 3, color);
            g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, color);
            g.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
            return;
        }
        // 6px pill corners
        g.fill(x + 6, y, x + w - 6, y + 1, color);
        g.fill(x + 3, y + 1, x + w - 3, y + 2, color);
        g.fill(x + 1, y + 2, x + w - 1, y + 4, color);
        g.fill(x, y + 4, x + w, y + h - 4, color);
        g.fill(x + 1, y + h - 4, x + w - 1, y + h - 2, color);
        g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, color);
        g.fill(x + 6, y + h - 1, x + w - 6, y + h, color);
    }

    /**
     * Liquid glass button panel - BRIGHT recipe. Same signature the mixins and
     * the morph renderer call.
     */
    public static void drawButtonPanel(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        float hv = hovered ? 1.25f : 1.0f;

        // 1. Frost halo: soft dark ring just outside the rim - fakes the blur
        //    darkening around the glass and lifts it off the background.
        fillRounded(g, x - 1, y - 1, w + 2, h + 2, scaled(argb(0x30, 0x00, 0x0A, 0x14), alpha));

        // 2. Glass body: BRIGHT translucent white - the background colour shows
        //    through and tints it (frosted glass follows the backdrop).
        fillRounded(g, x, y, w, h, scaled(boost(argb(0x5E, 0xFF, 0xFF, 0xFF), hv), alpha));

        // 3. Vertical gradient inside the body: brighter top, clearer middle,
        //    denser bottom (thick-lens banding) - drawn as inset bands so the
        //    6px corners of layer 2 still define the silhouette.
        if (h >= 10 && w >= 12) {
            g.fillGradient(x + 3, y + 2, x + w - 3, y + h / 2,
                scaled(boost(argb(0x54, 0xFF, 0xFF, 0xFF), hv), alpha),
                scaled(argb(0x18, 0xFF, 0xFF, 0xFF), alpha));
            g.fillGradient(x + 3, y + h / 2, x + w - 3, y + h - 3,
                scaled(argb(0x18, 0xFF, 0xFF, 0xFF), alpha),
                scaled(boost(argb(0x40, 0x9C, 0xC8, 0xE8), hv), alpha));
        }

        // 4. Full bright rim ring - light bends around the WHOLE edge.
        if (h >= 8 && w >= 10) {
            int rimT = boost(argb(0xE8, 0xFF, 0xFF, 0xFF), hv); // top: faces the light
            int rimS = boost(argb(0x90, 0xFF, 0xFF, 0xFF), hv); // sides
            int rimB = boost(argb(0xA8, 0xFF, 0xFF, 0xFF), hv); // bottom rim light
            g.fill(x + 5, y, x + w - 5, y + 1, scaled(rimT, alpha));
            g.fill(x + 2, y + 1, x + 4, y + 2, scaled(rimT, alpha));
            g.fill(x + w - 4, y + 1, x + w - 2, y + 2, scaled(rimB, alpha));
            g.fill(x + 1, y + 2, x + 2, y + h - 3, scaled(rimS, alpha));
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 3, scaled(rimS, alpha));
            g.fill(x + 2, y + h - 2, x + 4, y + h - 1, scaled(rimB, alpha));
            g.fill(x + w - 4, y + h - 2, x + w - 2, y + h - 1, scaled(rimB, alpha));
            g.fill(x + 5, y + h - 1, x + w - 5, y + h, scaled(rimB, alpha));
            // top-left corner hotspot (the lens catches a glint there)
            g.fill(x + 3, y + 2, x + 7, y + 3, scaled(boost(argb(0xFF, 0xFF, 0xFF, 0xFF), hv), alpha));
            g.fill(x + 2, y + 3, x + 3, y + 6, scaled(boost(argb(0xCC, 0xFF, 0xFF, 0xFF), hv), alpha));
        }

        // 5. Glossy sheen band under the top rim.
        if (h >= 12 && w >= 14) {
            g.fill(x + 8, y + 3, x + w - 8, y + 4, scaled(boost(argb(0x6E, 0xFF, 0xFF, 0xFF), hv), alpha));
        }

        if (!active) {
            fillRounded(g, x, y, w, h, scaled(argb(0x50, 0x10, 0x14, 0x18), alpha));
        }
    }

    /** Liquid glass slider: recessed groove + bright pill glass handle. */
    public static void drawSlider(GuiGraphics g, int x, int y, int w, int h, double value, boolean active, boolean hovered, float alpha) {
        float hv = hovered ? 1.25f : 1.0f;
        int cy = y + h / 2;
        // Recessed groove: dark channel + faint inner light
        fillRounded(g, x + 2, cy - 3, w - 4, 6, scaled(argb(0x70, 0x14, 0x18, 0x20), alpha));
        if (w > 8) {
            fillRounded(g, x + 3, cy - 2, w - 6, 4, scaled(argb(0x28, 0xFF, 0xFF, 0xFF), alpha));
        }
        // Bright glass handle (8 wide pill)
        int hx = x + (int) (Math.max(0.0, Math.min(1.0, value)) * (w - 8));
        fillRounded(g, hx - 1, y - 1, 10, h + 2, scaled(argb(0x30, 0x00, 0x0A, 0x14), alpha)); // frost halo
        fillRounded(g, hx, y, 8, h, scaled(boost(argb(0x60, 0xFF, 0xFF, 0xFF), hv), alpha));
        if (h >= 6) {
            g.fillGradient(hx + 2, y + 2, hx + 6, y + h / 2,
                scaled(boost(argb(0x60, 0xFF, 0xFF, 0xFF), hv), alpha),
                scaled(argb(0x18, 0xFF, 0xFF, 0xFF), alpha));
            g.fill(hx + 2, y + 1, hx + 6, y + 2, scaled(boost(argb(0xE8, 0xFF, 0xFF, 0xFF), hv), alpha));
            g.fill(hx + 1, y + 2, hx + 2, y + h - 2, scaled(boost(argb(0x90, 0xFF, 0xFF, 0xFF), hv), alpha));
            g.fill(hx + 6, y + 2, hx + 7, y + h - 2, scaled(boost(argb(0x90, 0xFF, 0xFF, 0xFF), hv), alpha));
            g.fill(hx + 2, y + h - 2, hx + 6, y + h - 1, scaled(boost(argb(0xA8, 0xFF, 0xFF, 0xFF), hv), alpha));
        }
        if (!active) {
            fillRounded(g, x, y, w, h, scaled(argb(0x50, 0x10, 0x14, 0x18), alpha));
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
