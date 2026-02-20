package io.github.nek0cha.screenshottoclipboard.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreenshottoclipboardClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ScreenshotToClipboard");

    @Override
    public void onInitializeClient() {
        // Load (or create) the config file on startup
        ModConfig.getInstance();
        LOGGER.info("[ScreenshotToClipboard] Initialized. showMessage={}",
                ModConfig.getInstance().showMessage);
    }
}