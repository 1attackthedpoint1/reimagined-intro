package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import noobtrieu.reimaginedintro.client.transition.EnterWorldTransition;
import noobtrieu.reimaginedintro.client.transition.ScreenTransitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two-phase menu transitions: intercept setScreen, let the outgoing screen keep
 * rendering as the ACTIVE screen (normal vanilla path) while it animates out,
 * then perform the real swap and animate the incoming screen in. No screen is
 * ever re-rendered while inactive.
 *
 * The TAIL hook arms FLUID morphs: by the time setScreen returns, the incoming
 * screen is initialized, so its widget layout can be captured for the morph.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$transitionSetScreen(Screen newScreen, CallbackInfo ci) {
        if (ScreenTransitions.onSetScreen(newScreen)) {
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void reimaginedIntro$afterSetScreen(Screen newScreen, CallbackInfo ci) {
        ScreenTransitions.onAfterSetScreen();
        // The vanilla "world ready" moment clears the screen; if the reversed
        // intro was covering the load, swap in the reveal screen so the logo
        // page's logo rect - now a live window into the game - expands.
        EnterWorldTransition.onSetScreenDone(newScreen);
    }
}
