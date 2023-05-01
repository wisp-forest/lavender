package io.wispforest.lavender.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.wispforest.lavender.parsing.MarkdownProcessor;
import io.wispforest.lavender.parsing.Parser;
import io.wispforest.owo.ui.core.Size;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class LavenderClient implements ClientModInitializer {

    private static final Int2ObjectMap<Size> TEXTURE_SIZES = new Int2ObjectOpenHashMap<>();

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("parse-md").then(argument("md", StringArgumentType.greedyString()).executes(context -> {
                context.getSource().sendFeedback(MarkdownProcessor.TEXT.process(StringArgumentType.getString(context, "md")));
                return 0;
            })));

            dispatcher.register(literal("edit-md").executes(context -> {
                MinecraftClient.getInstance().setScreen(new EditMdScreen());
                return 0;
            }));
        });
    }

    public static void registerTextureSize(int textureId, int width, int height) {
        TEXTURE_SIZES.put(textureId, Size.of(width, height));
    }

    public static @Nullable Size getTextureSize(Identifier texture) {
        return TEXTURE_SIZES.get(MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlId());
    }
}
