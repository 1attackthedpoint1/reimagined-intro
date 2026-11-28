package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.transition.ScreenTransitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FLUID list reveal gate: while a morph/reveal transition plays on the incoming
 * screen, the incoming selection list's ENTRIES are clipped to the region the
 * top-down reveal has already swept. This makes the reveal VISIBLE even if the
 * video-cover blit silently fails (e.g. raw-texture blit under VulkanMod) - the
 * cover + this scissor are belt and suspenders: whichever renders, the list
 * still reveals top-down.
 */
@Mixin(AbstractSelectionList.class)
public abstract class ListRevealMixin extends AbstractWidget {
    protected ListRevealMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void reimaginedIntro$clipRevealHead(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenTransitions.pushRevealScissor(guiGraphics, (AbstractSelectionList<?>) (Object) this);
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void reimaginedIntro$clipRevealTail(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenTransitions.popRevealScissor(guiGraphics);
    }
}
