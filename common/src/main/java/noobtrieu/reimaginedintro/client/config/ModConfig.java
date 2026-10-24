package noobtrieu.reimaginedintro.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.platform.Services;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    public boolean enableIntro = true;
    public float introVolume = 1.0f;
    public boolean enableTitleScreenLoop = true;
    public boolean showSkipPrompt = true;
    public boolean ignoreLegacyWarning = false;
    /** Menu transition type; see ScreenTransitions.Type (SLIDE, SLIDE_UP, COVER, FADE, ZOOM, OFF). */
    public String menuTransition = "SLIDE";
    /** Menu transition duration in milliseconds (50-1500, clamped at runtime). */
    public int menuTransitionDurationMs = 300;
    /** Render sub-menu backgrounds (Singleplayer/Multiplayer/Options/...) with the
     *  same background the title screen uses, instead of vanilla's dark scrim. */
    public boolean unifyMenuBackground = true;

    private static ModConfig INSTANCE;
    private static final Path CONFIG_FILE = Services.PLATFORM.getConfigDir().resolve("reimagined-intro.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                ReimaginedIntro.LOGGER.error("Failed to load config file, falling back to defaults", e);
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(get(), writer);
            }
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("Failed to save config file", e);
        }
    }
}
