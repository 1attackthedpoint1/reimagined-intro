package noobtrieu.reimaginedintro.platform.services;

import java.nio.file.Path;

public interface IPlatformHelper {
    /**
     * Gets the name of the current platform.
     *
     * @return The platform name ("Fabric", "NeoForge", etc.)
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given ID is loaded.
     *
     * @param modId The mod ID
     * @return true if loaded, false otherwise
     */
    boolean isModLoaded(String modId);

    /**
     * Gets the config directory path.
     */
    Path getConfigDir();

    /**
     * Gets the game root directory path.
     */
    Path getGameDir();
}
