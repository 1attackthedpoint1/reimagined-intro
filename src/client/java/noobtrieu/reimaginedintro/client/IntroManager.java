package noobtrieu.reimaginedintro.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

public class IntroManager {
    private static long startTimeMs = -1;
    private static boolean hasPlayed = false;
    private static boolean skipped = false;

    public static synchronized void startIntro() {
        if (startTimeMs == -1) {
            startTimeMs = System.currentTimeMillis();
            FrameStreamer.init();
            IntroSoundController.play();
        }
    }

    public static long getElapsedMs() {
        if (startTimeMs == -1) return 0;
        return System.currentTimeMillis() - startTimeMs;
    }

    public static boolean isIntroComplete() {
        return skipped || (startTimeMs != -1 && getElapsedMs() >= FrameStreamer.INTRO_TOTAL_DURATION_MS);
    }

    public static boolean hasPlayed() {
        return hasPlayed;
    }

    public static void setHasPlayed(boolean played) {
        hasPlayed = played;
    }

    public static void skip() {
        if (!skipped) {
            skipped = true;
            // Jump timeline to start of loop
            startTimeMs = System.currentTimeMillis() - FrameStreamer.INTRO_TOTAL_DURATION_MS;
            hasPlayed = true;

            // Transition directly to TitleScreen
            Minecraft.getInstance().gui.setScreen(new TitleScreen());
        }
    }

    public static void onIntroFinished() {
        hasPlayed = true;
        Minecraft.getInstance().gui.setScreen(new TitleScreen());
    }

    public static void resetForReplay() {
        startTimeMs = -1;
        skipped = false;
        hasPlayed = false;
        startIntro();
    }
}
