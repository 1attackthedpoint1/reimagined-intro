package noobtrieu.reimaginedintro.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.ui.GlassRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frosted-glass rendering for sliders (volume / transition speed in menus):
 * recessed rounded track + rounded glass handle replaces the vanilla sprites.
 * The value label and hover cursor logic stay completely vanilla.
 */
@Mixin(AbstractSliderButton.class)
public abstract class GlassSliderMixin extends AbstractWidget {
    @Shadow protected double value;

    protected GlassSliderMixin(int x, int y, int width, int height, net.minecraft.network.chat.Component message) {
        super(x, y, width, height, message);
    }

    private static boolean shouldGlass() {
        return ModConfig.get().glassButtons && Minecraft.getInstance().level == null;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void reimaginedIntro$renderGlassSlider(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!shouldGlass()) return;

        GlassRendering.drawSlider(
            guiGraphics,
            this.getX(), this.getY(), this.getWidth(), this.getHeight(),
            this.value, this.isActive(), this.isHoveredOrFocused(), this.alpha
        );
    }

    @WrapWithCondition(
        method = "renderWidget",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V")
    )
    private boolean reimaginedIntro$skipSliderSprites(GuiGraphics instance, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int tint) {
        // Drawn as glass by the HEAD inject instead; skip both vanilla sprite blits.
        return !shouldGlass();
    }
}
