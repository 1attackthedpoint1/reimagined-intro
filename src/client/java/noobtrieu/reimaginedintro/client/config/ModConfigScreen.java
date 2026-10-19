package noobtrieu.reimaginedintro.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import noobtrieu.reimaginedintro.client.IntroManager;
import noobtrieu.reimaginedintro.client.IntroScreen;

public class ModConfigScreen extends Screen {
    private final Screen parent;

    public ModConfigScreen(Screen parent) {
        super(Component.literal("Reimagined Intro Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = Math.max(50, this.height / 4);

        // 1. Startup Intro Toggle
        this.addRenderableWidget(
            CycleButton.onOffBuilder(ModConfig.get().enableIntro)
                .create(centerX - 100, startY, 200, 20, Component.literal("Startup Intro"), (btn, val) -> {
                    ModConfig.get().enableIntro = val;
                })
        );

        // 2. Title Screen Loop Toggle
        this.addRenderableWidget(
            CycleButton.onOffBuilder(ModConfig.get().enableTitleScreenLoop)
                .create(centerX - 100, startY + 26, 200, 20, Component.literal("Title Screen Loop"), (btn, val) -> {
                    ModConfig.get().enableTitleScreenLoop = val;
                })
        );

        // 3. Volume Slider
        this.addRenderableWidget(new AbstractSliderButton(centerX - 100, startY + 52, 200, 20, Component.empty(), ModConfig.get().introVolume) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int pct = (int) Math.round(this.value * 100.0);
                setMessage(Component.literal("Intro Volume: " + pct + "%"));
            }

            @Override
            protected void applyValue() {
                ModConfig.get().introVolume = (float) this.value;
            }
        });

        // 4. Test / Preview Intro button
        this.addRenderableWidget(
            Button.builder(Component.literal("Preview Intro Sequence"), b -> {
                ModConfig.save();
                IntroManager.resetForReplay();
                Minecraft.getInstance().gui.setScreen(new IntroScreen());
            }).bounds(centerX - 100, startY + 78, 200, 20).build()
        );

        // 5. Done Button
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(centerX - 100, this.height - 32, 200, 20)
                .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // Render Title
        int titleWidth = this.font.width(this.title);
        guiGraphics.text(this.font, this.title, this.width / 2 - titleWidth / 2, 22, 0xFFFFFFFF);

        // Render Explanatory Subtext
        Component hint1 = Component.literal("When Title Screen Loop is ON: uses intro loop background (hides logo & splash)");
        Component hint2 = Component.literal("When Title Screen Loop is OFF: dissolves into panorama (shows logo & splash)");
        int h1Width = this.font.width(hint1);
        int h2Width = this.font.width(hint2);
        int startY = Math.max(50, this.height / 4);

        guiGraphics.text(this.font, hint1, this.width / 2 - h1Width / 2, startY + 110, 0x88AAAAAA);
        guiGraphics.text(this.font, hint2, this.width / 2 - h2Width / 2, startY + 122, 0x88AAAAAA);
    }

    @Override
    public void onClose() {
        ModConfig.save();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }
}
