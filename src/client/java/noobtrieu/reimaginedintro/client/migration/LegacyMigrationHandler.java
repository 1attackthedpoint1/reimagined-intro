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
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        if (ModConfig.get().ignoreLegacyWarning) {
            return;
        }

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path legacyVersionFile = configDir.resolve("fancymenu").resolve("reimagined_intro_version.txt");

        boolean hasFancyMenu = FabricLoader.getInstance().isModLoaded("fancymenu");
        boolean hasLegacyFile = Files.exists(legacyVersionFile);

        if (!hasFancyMenu || !hasLegacyFile) {
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel messageLabel = new JLabel("<html><body style='width: 380px; font-family: sans-serif;'>"
            + "<h2 style='margin: 0 0 8px 0; color: #1E3A8A;'>Previous Version Detected (v2)</h2>"
            + "<p style='margin: 0 0 8px 0;'>An existing setup of <b>Reimagined Intro (v2)</b> using <i>FancyMenu</i> & <i>Drippy Loading Screen</i> was detected in this instance.</p>"
            + "<p style='margin: 0 0 8px 0;'><b>Reimagined Intro v3</b> is now a standalone native mod and no longer requires external menu mods. Leaving old v2 layouts active may cause visual glitches or duplicate loading screens.</p>"
            + "<p style='margin: 0 0 6px 0;'><i>Note: A safety backup of your existing configs will always be created before making any changes.</i></p>"
            + "<p style='margin: 0 0 4px 0;'><b>How would you like to proceed?</b></p>"
            + "</body></html>");
        panel.add(messageLabel, BorderLayout.CENTER);

        JCheckBox doNotShowAgain = new JCheckBox("Do not show this warning again", true);
        panel.add(doNotShowAgain, BorderLayout.SOUTH);

        String[] options = new String[]{
            "Clean Upgrade (Recommended)",
            "Backup Legacy Config",
            "Keep & Continue"
        };

        int choice = JOptionPane.showOptionDialog(
            null,
            panel,
            "Reimagined Intro — Migration Assistant",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        if (choice == 0) {
            // Clean Upgrade: Backup first, remove legacy version file, and disable FancyMenu/Drippy jars
            performCleanUpgrade(gameDir, configDir, legacyVersionFile);
            ModConfig.get().ignoreLegacyWarning = true;
            ModConfig.save();
        } else if (choice == 1) {
            // Backup Legacy Config: Backup configs, remove version file, but keep jars intact
            performBackupOnly(configDir, legacyVersionFile);
            ModConfig.get().ignoreLegacyWarning = true;
            ModConfig.save();
        } else {
            // Keep & Continue or closed window
            if (doNotShowAgain.isSelected()) {
                ModConfig.get().ignoreLegacyWarning = true;
                ModConfig.save();
            }
        }
    }

    private static Path backupConfigs(Path configDir) {
        try {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path backupDir = configDir.resolve("fancymenu_backup_" + timestamp);
            Files.createDirectories(backupDir);

            Path fancyMenuDir = configDir.resolve("fancymenu");
            if (Files.isDirectory(fancyMenuDir)) {
                copyDirectory(fancyMenuDir, backupDir.resolve("fancymenu"));
            }

            Path drippyDir = configDir.resolve("drippyloadingscreen");
            if (Files.isDirectory(drippyDir)) {
                copyDirectory(drippyDir, backupDir.resolve("drippyloadingscreen"));
            }

            ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Backed up legacy configs to: {}", backupDir);
            return backupDir;
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("[ReimaginedIntro] Failed to backup legacy configs", e);
            return null;
        }
    }

    private static void performCleanUpgrade(Path gameDir, Path configDir, Path legacyVersionFile) {
        Path backupDir = backupConfigs(configDir);

        try {
            Files.deleteIfExists(legacyVersionFile);
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Could not delete legacy marker file", e);
        }

        Path modsDir = gameDir.resolve("mods");
        List<String> disabledJars = disableLegacyJars(modsDir);

        StringBuilder sb = new StringBuilder("Clean Upgrade Complete!\n\n");
        if (backupDir != null) {
            sb.append("• Safety backup created in:\n  config/").append(backupDir.getFileName()).append("\n\n");
        }
        sb.append("• Legacy Reimagined Intro v2 marker removed.\n");
        if (!disabledJars.isEmpty()) {
            sb.append("• Disabled legacy mod JAR(s):\n");
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
            "Reimagined Intro — Migration Assistant",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static void performBackupOnly(Path configDir, Path legacyVersionFile) {
        Path backupDir = backupConfigs(configDir);

        try {
            Files.deleteIfExists(legacyVersionFile);
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Could not delete legacy marker file", e);
        }

        StringBuilder sb = new StringBuilder("Backup Complete!\n\n");
        if (backupDir != null) {
            sb.append("• Legacy configs safely backed up in:\n  config/").append(backupDir.getFileName()).append("\n\n");
        }
        sb.append("• Legacy Reimagined Intro v2 marker removed.\n");
        sb.append("• FancyMenu & Drippy Loading Screen JARs left intact.");

        JOptionPane.showMessageDialog(
            null,
            sb.toString(),
            "Reimagined Intro — Migration Assistant",
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
}
