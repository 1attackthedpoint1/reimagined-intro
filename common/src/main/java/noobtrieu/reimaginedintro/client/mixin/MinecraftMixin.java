package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import noobtrieu.reimaginedintro.client.transition.ScreenTransitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    public Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void reimaginedIntro$captureTransition(Screen newScreen, CallbackInfo ci) {
        // At HEAD, this.screen is still the outgoing screen — capture the pair
        // and let the original swap proceed (the incoming screen becomes active
        // immediately; the animation only affects rendering for a few frames).
        ScreenTransitions.onSetScreen(this.screen, newScreen);
    }
}
