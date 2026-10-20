package noobtrieu.reimaginedintro.client.migration;

import net.fabricmc.loader.api.FabricLoader;
import noobtrieu.reimaginedintro.ReimaginedIntro;
import noobtrieu.reimaginedintro.client.config.ModConfig;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
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

            JLabel messageLabel = new JLabel("<html><body style='width: 380px; font-family: sans-serif;'>"
                + "<h2 style='margin: 0 0 8px 0; color: #1E3A8A;'>Previous Version Detected (v2)</h2>"
                + "<p style='margin: 0 0 8px 0;'>An existing setup of <b>Reimagined Intro (v2)</b> was detected.</p>"
                + "<p style='margin: 0 0 8px 0;'><b>Reimagined Intro v3</b> is now a standalone mod and no longer requires FancyMenu. Leaving the old v2 layouts active will cause visual glitches and duplicate screens.</p>"
                + "<p style='margin: 0 0 8px 0;'><i>(Note: If you no longer need FancyMenu or Drippy Loading Screen, you can delete their JARs from your mods folder after closing the game.)</i></p>"
                + "<p style='margin: 0 0 4px 0;'><b>How would you like to proceed?</b></p>"
                + "</body></html>");
            panel.add(messageLabel, BorderLayout.CENTER);

            JCheckBox doNotShowAgain = new JCheckBox("Do not show this warning again", false);
            panel.add(doNotShowAgain, BorderLayout.SOUTH);

            String[] options = new String[]{
                "Disable v2 Layouts (Recommended)",
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
                // Disable v2 Layouts (Recommended): Disable layout files, unhook screens, remove marker
                performDisableLayouts(configDir, legacyVersionFile);
            }

            // Only suppress future warnings if the user explicitly checked the box
            if (doNotShowAgain.isSelected()) {
                ModConfig.get().ignoreLegacyWarning = true;
                ModConfig.save();
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

    private static List<String> disableLegacyLayouts(Path configDir) {
        List<String> disabled = new ArrayList<>();
        Path customizationDir = configDir.resolve("fancymenu").resolve("customization");
        if (!Files.isDirectory(customizationDir)) {
            return disabled;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(customizationDir, "reimagined_intro_*.txt")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                Path target = file.resolveSibling(fileName + ".disabled");
                try {
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                    disabled.add(fileName);
                } catch (Exception e) {
                    ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Could not disable layout {}: {}", fileName, e.getMessage());
                }
            }
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.error("[ReimaginedIntro] Error scanning customization directory", e);
        }
        return disabled;
    }

    private static void cleanCustomGuiScreens(Path configDir) {
        Path file = configDir.resolve("fancymenu").resolve("custom_gui_screens.txt");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            String updated = content.replaceAll("(?s)custom_gui\\s*\\{[^}]*identifier\\s*=\\s*reimagined_intro[^}]*\\}", "");

            if (!content.equals(updated)) {
                Files.writeString(file, updated);
                ReimaginedIntro.LOGGER.info("[ReimaginedIntro] Cleaned reimagined_intro from custom_gui_screens.txt");
            }
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Could not update custom_gui_screens.txt: {}", e.getMessage());
        }
    }

    private static void performDisableLayouts(Path configDir, Path legacyVersionFile) {
        List<String> disabledLayouts = disableLegacyLayouts(configDir);
        cleanCustomGuiScreens(configDir);

        try {
            Files.deleteIfExists(legacyVersionFile);
        } catch (Exception e) {
            ReimaginedIntro.LOGGER.warn("[ReimaginedIntro] Could not delete legacy marker file", e);
        }

        StringBuilder sb = new StringBuilder("v2 Layouts Disabled!\n\n");
        if (!disabledLayouts.isEmpty()) {
            sb.append("• Disabled v2 FancyMenu layout(s):\n");
            for (String layout : disabledLayouts) {
                sb.append("   - ").append(layout).append("\n");
            }
            sb.append("\n");
        }
        sb.append("You're all set to enjoy Reimagined Intro v3!");

        JOptionPane.showMessageDialog(
            null,
            sb.toString(),
            "Reimagined Intro",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
