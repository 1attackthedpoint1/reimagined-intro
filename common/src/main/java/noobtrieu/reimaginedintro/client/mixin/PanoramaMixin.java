package noobtrieu.reimaginedintro.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PanoramaRenderer.class)
public class PanoramaMixin {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V"
        )
    )
    private void fillPanoramaOverlay(
        GuiGraphics guiGraphics,
        RenderPipeline pipeline,
        Identifier texture,
        int x,
        int y,
        float uOffset,
        float vOffset,
        int width,
        int height,
        int uWidth,
        int vHeight,
        int textureWidth,
        int textureHeight
    ) {
        float targetAspect = 16.0f / 9.0f;
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (tex != null && tex.getTexture() != null) {
            int tw = tex.getTexture().getWidth(0);
            int th = tex.getTexture().getHeight(0);
            if (tw == 16 && th == 128) {
                // Vanilla default 16x128 gradient: stretch as normal
                guiGraphics.blit(pipeline, texture, x, y, uOffset, vOffset, width, height, uWidth, vHeight, textureWidth, textureHeight);
                return;
            }
            if (tw > 0 && th > 0) {
                targetAspect = (float) tw / (float) th;
            }
        }

        float screenAspect = (float) width / (float) height;
        int renderWidth, renderHeight, renderX, renderY;

        if (screenAspect > targetAspect) {
            // Screen is wider than texture -> match width, crop top/bottom
            renderWidth = width;
            renderHeight = (int) (width / targetAspect);
            renderX = 0;
            renderY = (height - renderHeight) / 2;
        } else {
            // Screen is taller than texture -> match height, crop left/right
            renderHeight = height;
            renderWidth = (int) (height * targetAspect);
            renderX = (width - renderWidth) / 2;
            renderY = 0;
        }

        guiGraphics.blit(
            texture,
            renderX,
            renderY,
            renderX + renderWidth,
            renderY + renderHeight,
            0.0f,
            1.0f,
            0.0f,
            1.0f
        );
    }
}
