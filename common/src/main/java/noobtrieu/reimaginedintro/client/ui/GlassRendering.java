package noobtrieu.reimaginedintro.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.FrameStreamer;

/**
 * Liquid-glass rendering following the ReGlass recipe (github.com/RedxAx/ReGlass):
 * the glass body is NOT painted - it IS the background, sampled per scanline
 * (menus: the looping video background; the button shows it through like a
 * lens). The shape is defined entirely by optics:
 *  - a bottom drop shadow (offset +2px, soft halo);
 *  - an edge refraction band: the outer ring samples the background with a
 *    slight barrel magnification, so light bends at the rim;
 *  - a Fresnel rim: bright stroke hugging the capsule edge (top-left facing
 *    the light), dimmer below;
 *  - a glare streak top-left, a thin inner shadow bottom;
 *  - hover: rim/tint boost + glow.
 * In-game (no video background to sample) falls back to the capsule fills.
 * Pure GuiGraphics fills/blits - VulkanMod-safe by construction.
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
     * Disabled: the per-scanline video-sampling lens (v3.9.0) caused a giant
     * brown block under VulkanMod - raw-texture blits misrender there, and
     * ReGlass (the reference) cannot run under VulkanMod either (shader
     * compile crash). The capsule glass is the VulkanMod-safe material.
     */
    private static boolean videoGlass() {
        return false;
    }

    /** Cover-crop UV mapping of the video background (same math as the menu background). */
    private static final class VidMap {
        final float w, h;
        final boolean widthLimited;
        final float vh, vTop, uw, uLeft;

        VidMap(GuiGraphics g) {
            w = g.guiWidth();
            h = g.guiHeight();
            float aspect = 16.0f / 9.0f;
            widthLimited = w / h > aspect;
            vh = w / aspect;
            vTop = (vh - h) / 2.0f;
            uw = h * aspect;
            uLeft = (uw - w) / 2.0f;
        }

        float u(float px) {
            return widthLimited ? px / w : (uLeft + px) / uw;
        }

        float v(float py) {
            return widthLimited ? (vTop + py) / vh : py / h;
        }
    }

    /**
     * Liquid glass button panel: background-sampled lens body (ReGlass recipe).
     * Same signature the mixins and the morph renderer call.
     */
    public static void drawButtonPanel(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        if (videoGlass()) {
            drawVideoGlass(g, x, y, w, h, hovered, alpha);
        } else {
            drawFallbackGlass(g, x, y, w, h, hovered, alpha);
        }
        if (!active) {
            fillPill(g, x, y, w, h, scaled(argb(0x58, 0x10, 0x14, 0x18), alpha));
        }
    }

    /** The ReGlass-style lens: shadow + refracting edge + Fresnel rim + glare. */
    private static void drawVideoGlass(GuiGraphics g, int x, int y, int w, int h, boolean hovered, float alpha) {
        float hv = hovered ? 1.35f : 1.0f;
        int r = Math.max(3, Math.min(w, h) / 2);
        VidMap m = new VidMap(g);
        var tex = FrameStreamer.getTextureId();

        // 1. Drop shadow under the lens (offset +2, core + soft halo).
        fillPill(g, x - 3, y + 2, w + 6, h + 6, scaled(argb(0x1C, 0x00, 0x00, 0x00), alpha));
        fillPill(g, x - 1, y + 2, w + 2, h + 2, scaled(argb(0x42, 0x00, 0x00, 0x00), alpha));

        // 2. Edge refraction pass: full pill sampled with barrel magnification -
        //    light bends around the rim (ReGlass refraction band).
        float cx = x + w / 2.0f, cy = y + h / 2.0f;
        final float MAG = 1.14f;
        for (int i = 0; i < h; i++) {
            double dy = (i + 0.5) - h / 2.0;
            double a = (double) r * r - dy * dy;
            if (a <= 0) continue;
            double hw = Math.sqrt(a);
            int x0 = (int) Math.floor(x + r - hw);
            int x1 = (int) Math.ceil(x + w - r + hw);
            if (x1 <= x0) continue;
            float sx0 = cx + (x0 - cx) / MAG, sx1 = cx + (x1 - cx) / MAG;
            float sy = cy + (y + i + 0.5f - cy) / MAG;
            g.blit(tex, x0, y + i, x1 - x0, 1, m.u(sx0), m.u(sx1), m.v(sy), m.v(sy + 1));
        }

        // 3. Inner pass: 1:1 background through the flat middle (inset 3px).
        if (w > 10 && h > 10) {
            int ri = r - 3;
            int xi = x + 3, yi = y + 3, wi = w - 6, hi = h - 6;
            for (int i = 0; i < hi; i++) {
                double dy = (i + 0.5) - hi / 2.0;
                double a = (double) ri * ri - dy * dy;
                if (a <= 0) continue;
                double hw = Math.sqrt(a);
                int x0 = (int) Math.floor(xi + ri - hw);
                int x1 = (int) Math.ceil(xi + wi - ri + hw);
                if (x1 <= x0) continue;
                g.blit(tex, x0, yi + i, x1 - x0, 1, m.u(x0), m.u(x1), m.v(yi + i), m.v(yi + i + 1));
            }
        }

        // 4. Fresnel rim: bright stroke hugging the top-left edge, dimmer below.
        int rimB = boost(argb(0x78, 0xFF, 0xFF, 0xFF), hv);
        int rimD = boost(argb(0x34, 0xFF, 0xFF, 0xFF), hv);
        for (int i = 0; i < h; i++) {
            double dy = (i + 0.5) - h / 2.0;
            double a = (double) r * r - dy * dy;
            if (a <= 0) continue;
            double hw = Math.sqrt(a);
            int x0 = (int) Math.floor(x + r - hw);
            int x1 = (int) Math.ceil(x + w - r + hw);
            int c = i < h / 2 ? rimB : rimD;
            g.fill(x0, y + i, x0 + 1, y + i + 1, scaled(c, alpha));
            g.fill(x1 - 1, y + i, x1, y + i + 1, scaled(c, alpha));
        }
        // top/bottom rim rows
        g.fill(x + r, y, x + w - r, y + 1, scaled(rimB, alpha));
        g.fill(x + r, y + h - 1, x + w - r, y + h, scaled(rimD, alpha));

        // 5. Glare streak top-left + thin inner shadow bottom.
        int glare = Math.max(6, Math.min(w / 4, 18));
        g.fill(x + r, y + 1, x + r + glare, y + 2, scaled(boost(argb(0x8C, 0xFF, 0xFF, 0xFF), hv), alpha));
        g.fill(x + 2, y + h / 2 - 1, x + 3, y + h / 2 + 1, scaled(boost(argb(0x64, 0xFF, 0xFF, 0xFF), hv), alpha));
        g.fill(x + r, y + h - 2, x + w - r, y + h - 1, scaled(argb(0x26, 0x00, 0x00, 0x00), alpha));

        // 6. Glass material hint: whisper of white + hover glow.
        fillPill(g, x + 1, y + 1, w - 2, h - 2, scaled(boost(argb(hovered ? 0x20 : 0x12, 0xFF, 0xFF, 0xFF), hv), alpha));
    }

    /** In-game fallback: capsule fills on the dark scrim (no video to sample). */
    private static void drawFallbackGlass(GuiGraphics g, int x, int y, int w, int h, boolean hovered, float alpha) {
        float hv = hovered ? 1.3f : 1.0f;
        int r = Math.max(3, Math.min(w, h) / 2);
        fillPill(g, x - 1, y - 1, w + 2, h + 2, scaled(argb(0x22, 0x00, 0x0A, 0x14), alpha));
        fillPill(g, x, y, w, h, scaled(boost(argb(0xB8, 0xFF, 0xFF, 0xFF), hv), alpha));
        fillPill(g, x + 1, y + 1, w - 2, h - 2, scaled(boost(argb(0x2C, 0xFF, 0xFF, 0xFF), hv), alpha));
        if (w > h && h >= 8) {
            fillPill(g, x + r / 2, y + 1, w - r, 2, scaled(boost(argb(0x50, 0xFF, 0xFF, 0xFF), hv), alpha));
        }
    }

    /** Liquid glass slider: recessed groove + lens handle. */
    public static void drawSlider(GuiGraphics g, int x, int y, int w, int h, double value, boolean active, boolean hovered, float alpha) {
        int cy = y + h / 2;
        fillRounded(g, x + 2, cy - 3, w - 4, 6, scaled(argb(0x60, 0x14, 0x18, 0x20), alpha));
        if (w > 8) {
            fillRounded(g, x + 3, cy - 2, w - 6, 4, scaled(argb(0x24, 0xFF, 0xFF, 0xFF), alpha));
        }
        int hx = x + (int) (Math.max(0.0, Math.min(1.0, value)) * (w - 8));
        if (videoGlass()) {
            drawVideoGlass(g, hx, y, 8, h, hovered, alpha);
        } else {
            drawFallbackGlass(g, hx, y, 8, h, hovered, alpha);
        }
        if (!active) {
            fillPill(g, x, y, w, h, scaled(argb(0x50, 0x10, 0x14, 0x18), alpha));
        }
    }

    /**
     * Full capsule (stadium) fill: radius = min(w,h)/2. Wide rects scan rows
     * from the left cap's arc to the right cap's arc; tall rects transpose.
     */
    public static void fillPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(w, h) / 2;
        if (r < 3) {
            fillRounded(g, x, y, w, h, color);
            return;
        }
        if (w >= h) {
            for (int i = 0; i < h; i++) {
                double dy = (i + 0.5) - h / 2.0;
                double a = (double) r * r - dy * dy;
                if (a <= 0) continue;
                double hw = Math.sqrt(a);
                int x0 = (int) Math.floor(x + r - hw);
                int x1 = (int) Math.ceil(x + w - r + hw);
                if (x1 > x0) g.fill(x0, y + i, x1, y + i + 1, color);
            }
        } else {
            for (int j = 0; j < w; j++) {
                double dx = (j + 0.5) - w / 2.0;
                double a = (double) r * r - dx * dx;
                if (a <= 0) continue;
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
     * Liquid neck between two blobs, tapered slice strip along the connecting
     * line (split/merge morph). Body colour matches the glass material hint.
     */
    public static void drawLiquidNeck(GuiGraphics g, float x0, float y0, float x1, float y1, float r0, float r1, float pinch, int bodyColor) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float capSum = r0 + r1;
        if (len <= capSum) return;
        float ang = (float) Math.atan2(dy, dx);
        float sx = x0 + (dx / len) * r0, sy = y0 + (dy / len) * r0;
        float neckLen = len - capSum;

        g.pose().pushMatrix();
        g.pose().translate(sx, sy);
        g.pose().rotate(ang);
        int slices = 12;
        for (int i = 0; i < slices; i++) {
            float t = (i + 0.5f) / slices;
            float r = (r0 + (r1 - r0) * t) * (1.0f - pinch * (float) Math.sin(Math.PI * t));
            float xA = t * neckLen;
            float xB = (i + 1.0f) / slices * neckLen;
            if (r < 0.5f) r = 0.5f;
            g.fill(Math.round(xA - 0.5f), Math.round(-r), Math.round(xB + 1.0f), Math.round(r + 0.5f), bodyColor);
        }
        g.pose().popMatrix();
    }
}
