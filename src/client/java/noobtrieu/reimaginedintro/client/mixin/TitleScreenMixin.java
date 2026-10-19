package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.IntroManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void renderCustomBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long elapsed = IntroManager.getElapsedMs();
        FrameStreamer.update(elapsed);

        // Fill background black
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        // Calculate aspect ratio scaling to cover screen
        float screenAspect = (float) this.width / (float) this.height;
        float targetAspect = 16.0f / 9.0f;
        int renderWidth, renderHeight, x, y;

        if (screenAspect > targetAspect) {
            renderWidth = this.width;
            renderHeight = (int) (this.width / targetAspect);
            x = 0;
            y = (this.height - renderHeight) / 2;
        } else {
            renderHeight = this.height;
            renderWidth = (int) (this.height * targetAspect);
            x = (this.width - renderWidth) / 2;
            y = 0;
        }

        guiGraphics.blit(FrameStreamer.getTextureId(), x, y, renderWidth, renderHeight, 0.0f, 0.0f, 1.0f, 1.0f);
        ci.cancel();
    }
}
