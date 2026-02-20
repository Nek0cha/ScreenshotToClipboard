package io.github.nek0cha.screenshottoclipboard.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent configuration for ScreenshotToClipboard.
 * Saved to {@code config/screenshottoclipboard.json}.
 */
public final class ModConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenshotToClipboard");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("screenshottoclipboard.json");

    private static ModConfig instance;

    // ------------------------------------------------------------------
    // Config fields (default: showMessage = false)
    // ------------------------------------------------------------------

    /** Show an in-game chat message when a screenshot is copied. Default: false. */
    public boolean showMessage = false;

    private ModConfig() {}

    public static ModConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig cfg = GSON.fromJson(r, ModConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException e) {
                LOGGER.warn("[ScreenshotToClipboard] Failed to load config, using defaults", e);
            }
        }
        ModConfig defaults = new ModConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            LOGGER.warn("[ScreenshotToClipboard] Failed to save config", e);
        }
    }
}