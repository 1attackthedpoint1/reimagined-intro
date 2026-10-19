package noobtrieu.reimaginedintro.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

public class IntroScreen extends Screen {
    public IntroScreen() {
        super(Component.literal("Reimagined Intro"));
    }

    @Override
    protected void init() {
        super.init();
        IntroManager.startIntro();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = IntroManager.getElapsedMs();

        if (elapsed >= FrameStreamer.INTRO_TOTAL_DURATION_MS) {
            IntroManager.onIntroFinished();
            return;
        }

        // Update active texture
        FrameStreamer.update(elapsed);

        // Fill background with black
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        // Scale 16:9 animation to fit screen
        float screenAspect = (float) this.width / (float) this.height;
        float targetAspect = 16.0f / 9.0f;
        int renderWidth, renderHeight, x, y;

        if (screenAspect > targetAspect) {
            renderHeight = this.height;
            renderWidth = (int) (this.height * targetAspect);
            x = (this.width - renderWidth) / 2;
            y = 0;
        } else {
            renderWidth = this.width;
            renderHeight = (int) (this.width / targetAspect);
            x = 0;
            y = (this.height - renderHeight) / 2;
        }

        guiGraphics.blit(FrameStreamer.getTextureId(), x, y, renderWidth, renderHeight, 0.0f, 0.0f, 1.0f, 1.0f);

        // Smooth fade-in from black during first 600ms
        if (elapsed < 600) {
            float fadeProgress = 1.0f - ((float) elapsed / 600.0f);
            int alpha = (int) (Math.clamp(fadeProgress, 0.0f, 1.0f) * 255.0f);
            if (alpha > 0) {
                guiGraphics.fill(0, 0, this.width, this.height, (alpha << 24));
            }
        }

        // Subtle skip prompt in bottom-right corner after 1.5s
        if (elapsed > 1500) {
            Component skipPrompt = Component.literal("SPACE to skip");
            int textWidth = this.font.width(skipPrompt);
            guiGraphics.text(this.font, skipPrompt, this.width - textWidth - 16, this.height - 20, 0x66FFFFFF);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN) {
            IntroManager.skip();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        IntroManager.skip();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
