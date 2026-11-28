package noobtrieu.reimaginedintro.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.transition.ScreenTransitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Swallow keys while a menu transition is playing (short window) so a
        // fast double-tap cannot leak onto the incoming screen.
        if (ScreenTransitions.isBlockingInput()) {
            cir.setReturnValue(true);
            return;
        }

        if ((Object) this instanceof TitleScreen) {
            if (event.key() == InputConstants.KEY_F6) {
                IntroManager.resetForReplay();
                Minecraft.getInstance().setScreen(new IntroScreen());
                cir.setReturnValue(true);
            } else if (event.key() == InputConstants.KEY_F7) {
                Minecraft.getInstance().setScreen(new noobtrieu.reimaginedintro.client.config.ModConfigScreen((Screen) (Object) this));
                cir.setReturnValue(true);
            } else if (event.key() == InputConstants.KEY_ESCAPE && noobtrieu.reimaginedintro.client.IntroSoundController.isPlaying()) {
                noobtrieu.reimaginedintro.client.IntroSoundController.stop();
                cir.setReturnValue(true);
            }
        }
    }

    // The game renders the active screen through renderWithTooltipAndSubtitles
    // (background + widgets + deferred). During a transition the ACTIVE screen
    // gets a pose transform around this wrapper — nothing else is re-rendered.
    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"))
    private void reimaginedIntro$transitionRenderHead(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenTransitions.onRenderHead((Screen) (Object) this, guiGraphics, mouseX, mouseY, partialTick, ci);
    }

    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("TAIL"))
    private void reimaginedIntro$transitionRenderTail(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenTransitions.onRenderTail((Screen) (Object) this, guiGraphics, mouseX, mouseY, partialTick);
    }
}
