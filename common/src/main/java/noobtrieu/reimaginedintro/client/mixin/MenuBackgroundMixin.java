package noobtrieu.reimaginedintro.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import noobtrieu.reimaginedintro.client.FrameStreamer;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;
import noobtrieu.reimaginedintro.client.config.ModConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unifies sub-menu backgrounds (Singleplayer world list, Multiplayer, Options,
 * mod menus, ...) with the exact background the mod renders on the title screen:
 *
 *  - TitleScreenLoop enabled  -> the looping intro video frame (same texture,
 *    same 16:9 cover logic, same timeline; FrameStreamer keeps advancing so the
 *    video is seamless across TitleScreen <-> sub-menus).
 *  - TitleScreenLoop disabled -> the vanilla panorama, rendered through the same
 *    PanoramaRenderer call the title screen uses, so the mod's panorama overlay
 *    (PanoramaMixin) applies identically.
 *
 * Vanilla's menu_background.png scrim (the flat dark wash players see as the
 * "black background") is skipped entirely. In-game screens (level != null) and
 * screens that override renderBackground themselves are left untouched.
 */
@Mixin(Screen.class)
public abstract class MenuBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void reimaginedIntro$unifyMenuBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return; // in-game UI keeps its own semantics

        Screen self = (Screen) (Object) this;
        if (self instanceof IntroScreen || self instanceof TitleScreen) return; // they manage their own background
        if (!ModConfig.get().unifyMenuBackground) return;

        int w = guiGraphics.guiWidth();
        int h = guiGraphics.guiHeight();

        if (ModConfig.get().enableTitleScreenLoop) {
            // Same looping video the title screen renders — advance the shared
            // timeline so it plays seamlessly across screens.
            FrameStreamer.update(IntroManager.getElapsedMs());

            guiGraphics.fill(0, 0, w, h, 0xFF000000);

            // 16:9 cover — identical math to the title screen's custom background.
            float screenAspect = (float) w / (float) h;
            float targetAspect = 16.0f / 9.0f;
            int renderWidth, renderHeight, x, y;
            if (screenAspect > targetAspect) {
                renderWidth = w;
                renderHeight = (int) (w / targetAspect);
                x = 0;
                y = (h - renderHeight) / 2;
            } else {
                renderHeight = h;
                renderWidth = (int) (h * targetAspect);
                x = (w - renderWidth) / 2;
                y = 0;
            }
            guiGraphics.blit(
                FrameStreamer.getTextureId(),
                x, y,
                x + renderWidth, y + renderHeight,
                0.0f, 1.0f, 0.0f, 1.0f
            );
        } else {
            // Same panorama path as the title screen (spinning), which also runs
            // through PanoramaMixin's overlay replacement for a perfect match.
            mc.gameRenderer.getPanorama().render(guiGraphics, w, h, true);
        }

        ci.cancel();
    }
}
