package noobtrieu.reimaginedintro.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import noobtrieu.reimaginedintro.client.config.ModConfigScreen;
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
            if (ModConfig.get().enableIntro) {
                Minecraft.getInstance().gui.setScreen(new IntroScreen());
                ci.cancel();
            }
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addConfigButton(CallbackInfo ci) {
        this.addRenderableWidget(
            Button.builder(
                Component.literal("Intro..."),
                b -> Minecraft.getInstance().gui.setScreen(new ModConfigScreen(this))
            ).bounds(this.width - 62, 8, 54, 20).build()
        );
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

    @WrapWithCondition(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V")
    )
    private boolean shouldRenderLogo(LogoRenderer instance, GuiGraphicsExtractor guiGraphics, int width, float alpha) {
        // Suppress Minecraft logo when custom loop is enabled
        return !ModConfig.get().enableTitleScreenLoop;
    }

    @WrapWithCondition(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V")
    )
    private boolean shouldRenderSplash(SplashRenderer instance, GuiGraphicsExtractor guiGraphics, int width, Font font, float alpha) {
        // Suppress splash text when custom loop is enabled
        return !ModConfig.get().enableTitleScreenLoop;
    }

    @Inject(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V", shift = At.Shift.AFTER)
    )
    private void fadePanoramaFromBlack(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ModConfig.get().enableTitleScreenLoop && this.fading) {
            float f1 = (float) (Util.getMillis() - this.fadeInStart) / 2000.0F;
            float f = Math.clamp(Mth.clampedMap(f1, 0.5F, 1.0F, 0.0F, 1.0F), 0.0F, 1.0F);
            float blackAlpha = 1.0F - f;
            int alpha = (int) (blackAlpha * 255.0F);
            if (alpha > 0) {
                guiGraphics.fill(0, 0, this.width, this.height, (alpha << 24));
            }
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void renderCustomBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ModConfig.get().enableTitleScreenLoop) {
            return;
        }

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
