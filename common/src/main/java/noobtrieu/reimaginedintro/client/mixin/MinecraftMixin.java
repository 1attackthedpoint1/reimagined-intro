package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$transitionSetScreen(Screen newScreen, CallbackInfo ci) {
        if (ScreenTransitions.onSetScreen(newScreen)) {
            ci.cancel();
        }
    }
}
