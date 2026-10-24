package noobtrieu.reimaginedintro.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
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
        // Suppress Mojang logo on splash screen when custom intro is enabled
        return !ModConfig.get().enableIntro;
    }
}
