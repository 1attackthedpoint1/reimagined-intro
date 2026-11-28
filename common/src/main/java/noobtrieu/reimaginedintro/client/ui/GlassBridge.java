package noobtrieu.reimaginedintro.client.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import restudio.reglass.client.api.ReGlassApi;
import restudio.reglass.client.api.WidgetStyle;

/**
 * Bridge to the ReGlass liquid-glass renderer (optional runtime dependency).
 * When ReGlass is present our own button skin steps aside - ReGlass restyles
 * every vanilla button through its blitSprite intercept - and our morph
 * droplets render through the same SDF shader, so flying droplets and settled
 * buttons share one material. Without ReGlass everything falls back to the
 * built-in capsule glass.
 */
public final class GlassBridge {
    private GlassBridge() {
    }

    private static final boolean PRESENT = checkPresent();

    private static boolean checkPresent() {
        try {
            return FabricLoader.getInstance().isModLoaded("reglass");
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when ReGlass is loaded and should own the button material. */
    public static boolean present() {
        return PRESENT;
    }

    /** Recommended corner radius, ReGlass style: half the shorter side. */
    public static float cornerRadius(int w, int h) {
        return 0.5f * Math.min(w, h);
    }

    /**
     * Renders a liquid-glass capsule at the given rect through ReGlass's SDF
     * shader (refraction, fresnel rim, shadow, the works). No-op without
     * ReGlass - callers pair this with their own fallback.
     */
    public static void renderGlass(GuiGraphics g, int x, int y, int w, int h, float hover) {
        if (!PRESENT || w <= 0 || h <= 0) return;
        try {
            ReGlassApi.create(g)
                .position(x, y)
                .size(w, h)
                .cornerRadius(cornerRadius(w, h))
                .hover(hover)
                .render();
        } catch (Throwable ignored) {
            // ReGlass API hiccup (e.g. widget capacity): drop the droplet frame
        }
    }

    /** Same as {@link #renderGlass} plus a text label (droplets arriving home). */
    public static void renderGlassWithText(GuiGraphics g, int x, int y, int w, int h, float hover, net.minecraft.network.chat.Component text) {
        if (!PRESENT || w <= 0 || h <= 0) return;
        try {
            ReGlassApi.create(g)
                .position(x, y)
                .size(w, h)
                .cornerRadius(cornerRadius(w, h))
                .hover(hover)
                .text(text)
                .render();
        } catch (Throwable ignored) {
        }
    }

    /** Static holder so the style tweak is applied once per frame, lazily. */
    private static volatile boolean tuned = false;

    /** Optional: nudge ReGlass global defaults toward the menu look. */
    public static void tune() {
        if (!PRESENT || tuned) return;
        tuned = true;
        try {
            // Keep ReGlass defaults - they ARE the reference look. Nothing to do
            // here yet; reserved for future tuning (tint, blur radius...).
        } catch (Throwable ignored) {
        }
    }

    /** Unused import silencer for WidgetStyle (kept for future tuning). */
    @SuppressWarnings("unused")
    private static WidgetStyle unused() {
        return WidgetStyle.create();
    }
}
