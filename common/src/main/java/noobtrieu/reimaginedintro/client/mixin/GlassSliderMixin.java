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
 * The value label and hover cursor logic stay completely vanilla — only the two
 * sprite blits are skipped.
 */
@Mixin(AbstractSliderButton.class)
public abstract class GlassSliderMixin {
    @Shadow protected double value;
    @Shadow protected abstract boolean isHoveredOrFocused();
    @Shadow public abstract boolean isActive();
    @Shadow protected float alpha;

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void reimaginedIntro$renderGlassSlider(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ModConfig.get().glassButtons) return;
        if (Minecraft.getInstance().level != null) return;

        AbstractWidget self = (AbstractWidget) (Object) this;
        GlassRendering.drawSlider(
            guiGraphics,
            self.getX(), self.getY(), self.getWidth(), self.getHeight(),
            value, isActive(), isHoveredOrFocused(), alpha
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

    private static boolean shouldGlass() {
        return ModConfig.get().glassButtons && Minecraft.getInstance().level == null;
    }
}
