package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow private boolean fading;
    @Shadow private long fadeInStart;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void openIntroOnFirstLaunch(CallbackInfo ci) {
        if (!IntroManager.hasPlayed()) {
            IntroManager.setHasPlayed(true);
            Minecraft.getInstance().gui.setScreen(new IntroScreen());
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void eliminateFadeInDelay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.fading && this.fadeInStart == 0L) {
            // Vanilla TitleScreen hardcodes a 1000ms dead zone where alpha is clamped to 0.0.
            // By pre-advancing fadeInStart by 1000ms, alpha immediately begins rising from 0.0 on frame 1,
            // producing a smooth 1-second fade-in with zero dead delay!
            this.fadeInStart = Util.getMillis() - 1000L;
        }
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

        // Correct Minecraft 26.3 blit parameters: (id, x0, y0, x1, y1, u0, u1, v0, v1)
        guiGraphics.blit(
            FrameStreamer.getTextureId(),
            x,
            y,
            x + renderWidth,
            y + renderHeight,
            0.0f,
            1.0f,
            0.0f,
            1.0f
        );
        ci.cancel();
    }
}
