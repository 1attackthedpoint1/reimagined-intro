package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import noobtrieu.reimaginedintro.client.transition.ScreenTransitions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swallows mouse input while a menu transition is playing, so a fast double
 * click cannot leak onto the incoming screen (e.g. double-clicking Singleplayer
 * must not select/join a world in SelectWorldScreen by accident).
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$blockMouseDuringTransition(long windowPointer, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (ScreenTransitions.isBlockingInput()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$blockScrollDuringTransition(long windowPointer, double deltaX, double deltaY, CallbackInfo ci) {
        if (ScreenTransitions.isBlockingInput()) {
            ci.cancel();
        }
    }
}
