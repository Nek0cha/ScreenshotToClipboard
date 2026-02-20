package io.github.nek0cha.screenshottoclipboard.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Registers a Cloth Config screen with ModMenu.
 * Only loaded when both ModMenu and Cloth Config are present at runtime.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::buildScreen;
    }

    private static Screen buildScreen(Screen parent) {
        ModConfig config = ModConfig.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("screenshottoclipboard.config.title"))
                .setSavingRunnable(config::save);

        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(
                Text.translatable("screenshottoclipboard.config.category.general"));

        general.addEntry(eb
                .startBooleanToggle(
                        Text.translatable("screenshottoclipboard.config.show_message"),
                        config.showMessage)
                .setDefaultValue(false)
                .setTooltip(Text.translatable("screenshottoclipboard.config.show_message.tooltip"))
                .setSaveConsumer(v -> config.showMessage = v)
                .build());

        return builder.build();
    }
}