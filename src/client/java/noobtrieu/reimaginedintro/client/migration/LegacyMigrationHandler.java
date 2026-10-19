package noobtrieu.reimaginedintro.client.migration;

import net.fabricmc.loader.api.FabricLoader;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.config.ModConfig;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LegacyMigrationHandler {

    public static void checkAndPrompt() {
        if (ModConfig.get().ignoreLegacyWarning) {
            return;
        }

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path legacyVersionFile = configDir.resolve("fancymenu").resolve("reimagined_intro_version.txt");

        Path modsDir = gameDir.resolve("mods");
        boolean hasFancyMenuMod = FabricLoader.getInstance().isModLoaded("fancymenu");
        boolean hasFancyMenuJar = hasModJar(modsDir, "fancymenu");
        boolean hasLegacyFile = Files.exists(legacyVersionFile);

        // Open migration assistant if legacy marker is present and FancyMenu is installed (regardless of Drippy presence)
        if (!hasLegacyFile || !(hasFancyMenuMod || hasFancyMenuJar)) {
            return;
        }

        boolean hasDrippyMod = FabricLoader.getInstance().isModLoaded("drippyloadingscreen");
        boolean hasDrippyJar = hasModJar(modsDir, "drippyloadingscreen");
        boolean hasDrippy = hasDrippyMod || hasDrippyJar;

        ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Legacy Reimagined Intro v2 setup detected! Prompting migration assistant...");

        String prevHeadless = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "false");

        try {
            if (GraphicsEnvironment.isHeadless()) {
                ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Environment remains headless, skipping migration dialog.");
                return;
            }

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            JPanel panel = new JPanel(new BorderLayout(0, 14));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            String detectedDesc = hasDrippy
                ? "<p style='margin: 0 0 8px 0;'>An existing setup of <b>Reimagined Intro (v2)</b> using <i>FancyMenu</i> & <i>Drippy Loading Screen</i> was detected in this instance.</p>"
                  + "<p style='margin: 0 0 8px 0;'><b>Reimagined Intro v3</b> is a standalone native mod. Because Drippy Loading Screen depends directly on FancyMenu, removing FancyMenu will also disable Drippy Loading Screen to prevent game crashes.</p>"
                : "<p style='margin: 0 0 8px 0;'>An existing setup of <b>Reimagined Intro (v2)</b> using <i>FancyMenu</i> was detected in this instance.</p>"
                  + "<p style='margin: 0 0 8px 0;'><b>Reimagined Intro v3</b> is now a standalone native mod and no longer requires FancyMenu. Leaving old v2 layouts active may cause visual glitches or duplicate title screen elements.</p>";

            JLabel messageLabel = new JLabel("<html><body style='width: 380px; font-family: sans-serif;'>"
                + "<h2 style='margin: 0 0 8px 0; color: #1E3A8A;'>Previous Version Detected (v2)</h2>"
                + detectedDesc
                + "<p style='margin: 0 0 4px 0;'><b>How would you like to proceed?</b></p>"
                + "</body></html>");
            panel.add(messageLabel, BorderLayout.CENTER);

            JCheckBox doNotShowAgain = new JCheckBox("Do not show this warning again", false);
            panel.add(doNotShowAgain, BorderLayout.SOUTH);

            String[] options = new String[]{
                "Disable FancyMenu (Recommended)",
                "Disable Config Only",
                "Keep Everything (May Break)"
            };

            int choice = JOptionPane.showOptionDialog(
                null,
                panel,
                "Reimagined Intro",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
            );

            if (choice == 0) {
                // Remove FancyMenu (Recommended): Disable mods directly without backing up configs, keep marker
                performRemoveFancyMenu(gameDir);
                ModConfig.get().ignoreLegacyWarning = true;
                ModConfig.save();
            } else if (choice == 1) {
                // Disable Configs Only: Move FancyMenu configs to fancymenu_backup_[timestamp], keep marker, keep jars
                performDisableConfigsOnly(configDir, legacyVersionFile);
                ModConfig.get().ignoreLegacyWarning = true;
                ModConfig.save();
            } else {
                // Keep Everything (May Break) or closed window
                if (doNotShowAgain.isSelected()) {
                    ModConfig.get().ignoreLegacyWarning = true;
                    ModConfig.save();
                }
            }
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("[ReimaginedIntro] Error showing migration assistant dialog", e);
        } finally {
            if (prevHeadless != null) {
                System.setProperty("java.awt.headless", prevHeadless);
            }
        }
    }

    private static boolean hasModJar(Path modsDir, String keyword) {
        if (!Files.isDirectory(modsDir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Path backupAndDisableFancyMenuConfig(Path configDir, Path legacyVersionFile) {
        try {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path backupDir = configDir.resolve("fancymenu_backup_" + timestamp);
            Path fancyMenuDir = configDir.resolve("fancymenu");

            if (!Files.isDirectory(fancyMenuDir)) {
                return null;
            }

            Files.createDirectories(backupDir);
            copyDirectory(fancyMenuDir, backupDir);

            // Clear old layout/customization files from config/fancymenu but preserve the marker file
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fancyMenuDir)) {
                for (Path entry : stream) {
                    if (entry.getFileName().toString().equalsIgnoreCase("reimagined_intro_version.txt")) {
                        continue; // Keep marker intact
                    }
                    if (Files.isDirectory(entry)) {
                        deleteDirectory(entry);
                    } else {
                        Files.deleteIfExists(entry);
                    }
                }
            }

            ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Disabled FancyMenu configs and backed up to: {}", backupDir);
            return backupDir;
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("[ReimaginedIntro] Failed to backup and disable FancyMenu configs", e);
            return null;
        }
    }

    private static void performRemoveFancyMenu(Path gameDir) {
        Path modsDir = gameDir.resolve("mods");
        List<String> disabledJars = disableLegacyJars(modsDir);

        StringBuilder sb = new StringBuilder("Legacy Mods Disabled!\n\n");
        if (!disabledJars.isEmpty()) {
            sb.append("• Disabled mod JAR(s):\n");
            for (String jar : disabledJars) {
                sb.append("   - ").append(jar).append(" -> .disabled\n");
            }
        } else {
            sb.append("• No conflicting JAR files needed disabling.\n");
        }
        sb.append("\nYou're all set to enjoy Reimagined Intro v3!");

        JOptionPane.showMessageDialog(
            null,
            sb.toString(),
            "Reimagined Intro",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static void performDisableConfigsOnly(Path configDir, Path legacyVersionFile) {
        Path backupDir = backupAndDisableFancyMenuConfig(configDir, legacyVersionFile);

        StringBuilder sb = new StringBuilder("FancyMenu Configs Disabled!\n\n");
        if (backupDir != null) {
            sb.append("• Old FancyMenu configs backed up to:\n  config/").append(backupDir.getFileName()).append("\n\n");
        }
        sb.append("• FancyMenu mod JAR(s) left intact.\n");
        sb.append("\nYou're all set to enjoy Reimagined Intro v3!");

        JOptionPane.showMessageDialog(
            null,
            sb.toString(),
            "Reimagined Intro",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static List<String> disableLegacyJars(Path modsDir) {
        List<String> disabled = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return disabled;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                // Disable FancyMenu and, if present, dependent Drippy Loading Screen
                if (name.contains("fancymenu") || name.contains("drippyloadingscreen")) {
                    Path target = jar.resolveSibling(jar.getFileName().toString() + ".disabled");
                    try {
                        Files.move(jar, target, StandardCopyOption.REPLACE_EXISTING);
                        disabled.add(jar.getFileName().toString());
                    } catch (Exception e) {
                        ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Could not rename jar {}: {}", jar, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("[ReimaginedIntro] Error scanning mods directory", e);
        }
        return disabled;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a))
                  .forEach(p -> {
                      try {
                          Files.deleteIfExists(p);
                      } catch (IOException e) {
                          throw new UncheckedIOException(e);
                      }
                  });
        }
    }
}
