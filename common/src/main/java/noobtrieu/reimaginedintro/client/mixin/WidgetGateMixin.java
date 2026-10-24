package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import noobtrieu.reimaginedintro.client.transition.ScreenTransitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FLUID morph render gate: while a morph transition is playing, the incoming
 * screen's real buttons are suppressed (they are replaced by morphing glass
 * rectangles drawn by ScreenTransitions at the screen TAIL). Identity-based:
 * only widgets captured as morph targets are ever hidden.
 */
@Mixin(AbstractWidget.class)
public abstract class WidgetGateMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$hideDuringMorph(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ScreenTransitions.isWidgetHidden((AbstractWidget) (Object) this)) {
            ci.cancel();
        }
    }
}
