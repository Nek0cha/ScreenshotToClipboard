package io.github.nek0cha.screenshottoclipboard.mixin.client;

import io.github.nek0cha.screenshottoclipboard.client.ModConfig;
import io.github.nek0cha.screenshottoclipboard.client.ScreenshotClipboardService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

/**
 * Java port of ScreenshotToClipboardMixin.java (reference project).
 * Hooks the private static helper {@code method_22691} inside ScreenshotRecorder
 * that is called after the screenshot PNG has been written to disk.
 */
@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenshotToClipboard");
    private static boolean stc$loggedError = false;

    @Inject(
        method = "method_22691(Lnet/minecraft/client/texture/NativeImage;Ljava/io/File;Ljava/util/function/Consumer;)V",
        at = @At("TAIL")
    )
    private static void stc$copyScreenshotToClipboard(
            NativeImage image,
            File file,
            Consumer<Text> messageReceiver,
            CallbackInfo ci) {
        try {
            if (file == null) return;

            // Enqueue the saved PNG file for off-thread clipboard copy.
            // onSuccess / onFailure send a chat message only when showMessage is enabled.
            ScreenshotClipboardService.INSTANCE.enqueueFile(
                file,
                /* onSuccess */ () -> {
                    if (!ModConfig.getInstance().showMessage) return;
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client == null) return;
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(
                                Text.translatable("screenshottoclipboard.message.copied"),
                                false);
                        }
                    });
                },
                /* onFailure */ () -> {
                    if (!ModConfig.getInstance().showMessage) return;
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client == null) return;
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(
                                Text.translatable("screenshottoclipboard.message.failed"),
                                false);
                        }
                    });
                }
            );

            LOGGER.debug("[ScreenshotToClipboard] Enqueued screenshot copy to clipboard: {}", file);
        } catch (Throwable t) {
            if (!stc$loggedError) {
                stc$loggedError = true;
                LOGGER.error("[ScreenshotToClipboard] Unexpected error in screenshot clipboard mixin", t);
            }
        }
    }
}
