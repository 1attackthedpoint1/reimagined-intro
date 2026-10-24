package noobtrieu.reimaginedintro.client.transition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Minimal indirection so ScreenTransitions can drive the real screen swap. */
final class MinecraftAccessorBridge {
    private MinecraftAccessorBridge() {
    }

    static Screen getActiveScreen() {
        return Minecraft.getInstance().screen;
    }

    static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
