package noobtrieu.reimaginedintro.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces the Mojang Studios logo on the loading screen with the mod's custom
 * logo texture (resources/assets/reimagined-intro/textures/loading/custom_logo.png).
 *
 * The vanilla logo is a split two-row texture drawn as two half-blits locked to
 * a 4:1 banner, so we cancel both blits and draw the custom image once, centered,
 * at its native aspect ratio — keeping vanilla's fade-in alpha (tint) intact.
 * The vanilla texture itself is loaded from the vanilla pack only (vanilla
 * LogoTexture hardcodes getVanillaPackResources), which is why a plain asset
 * override would NOT work and this code-level replacement is required.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    private static Identifier customLogoId;
    private static int customLogoWidth;
    private static int customLogoHeight;

    @WrapWithCondition(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"
        )
    )
    private boolean shouldRenderMojangLogo(
        GuiGraphics instance,
        RenderPipeline pipeline,
        Identifier texture,
        int x0,
        int y0,
        float u0,
        float v0,
        int width,
        int height,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight,
        int tintColor
    ) {
        Identifier logo = getCustomLogo();
        if (logo == null) {
            return true; // custom logo unavailable: fall back to vanilla
        }
        // The vanilla logo draws as two half-blits (u0 = -0.0625 then +0.0625).
        // Draw the custom image once on the left-half call; skip the right half.
        if (u0 > 0.0f) return false;

        int guiW = instance.guiWidth();
        int guiH = instance.guiHeight();
        // Same vertical budget as vanilla (min(w*0.75, h) * 0.25), native aspect.
        float boxH = Math.min(guiW * 0.75f, guiH) * 0.25f;
        float drawH = boxH;
        float drawW = boxH * (customLogoWidth / (float) customLogoHeight);
        if (drawW > guiW * 0.9f) { // clamp very wide images to the screen
            drawW = guiW * 0.9f;
            drawH = drawW / (customLogoWidth / (float) customLogoHeight);
        }
        int x = Math.round(guiW / 2.0f - drawW / 2.0f);
        int y = Math.round(guiH / 2.0f - drawH / 2.0f);

        instance.blit(
            RenderPipelines.GUI_TEXTURED, logo,
            x, y, 0.0f, 0.0f,
            Math.round(drawW), Math.round(drawH),
            customLogoWidth, customLogoHeight,
            customLogoWidth, customLogoHeight,
            tintColor
        );
        return false;
    }

    /** Lazily loads and registers the custom logo texture (once, on the render thread). */
    private static Identifier getCustomLogo() {
        if (customLogoId == null) {
            try (InputStream in = LoadingOverlayMixin.class.getResourceAsStream(
                    "/assets/reimagined-intro/textures/loading/custom_logo.png")) {
                if (in == null) {
                    ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] custom_logo.png missing from mod resources; keeping vanilla logo");
                    return null;
                }
                NativeImage image = NativeImage.read(in);
                customLogoWidth = image.getWidth();
                customLogoHeight = image.getHeight();
                customLogoId = ReimaginedIntro.id("loading/custom_logo");
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                DynamicTexture dynamicTexture = new DynamicTexture(() -> "custom_logo", image);
                textureManager.register(customLogoId, dynamicTexture);
            } catch (Throwable t) {
                ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] failed to load custom loading logo; keeping vanilla logo", t);
                return null;
            }
        }
        return customLogoId;
    }
}
