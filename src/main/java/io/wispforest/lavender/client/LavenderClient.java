package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.LavenderCommands;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.BookContentLoader;
import io.wispforest.lavender.book.BookItem;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.hud.Hud;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public class LavenderClient implements ClientModInitializer {

    private static final Int2ObjectMap<Size> TEXTURE_SIZES = new Int2ObjectOpenHashMap<>();
    private static final Identifier ENTRY_HUD_ID = Lavender.id("entry_hud");

    private static UUID currentWorldId = null;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(LavenderCommands.Client::register);

        ModelLoadingRegistry.INSTANCE.registerResourceProvider(manager -> (resourceId, context) -> {
            if (!resourceId.equals(Lavender.id("item/dynamic_book"))) return null;
            return new BookBakedModel.Unbaked();
        });

        StructureOverlayRenderer.initialize();
        OffhandBookRenderer.initialize();

        LavenderStructures.initialize();
        BookLoader.initialize();
        BookContentLoader.initialize();

        Hud.add(ENTRY_HUD_ID, () -> Containers.horizontalFlow(Sizing.content(), Sizing.content()).gap(5).positioning(Positioning.across(50, 52)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || !(Hud.getComponent(ENTRY_HUD_ID) instanceof FlowLayout hudComponent)) return;

            hudComponent.<FlowLayout>configure(container -> {
                container.clearChildren();

                Book book = BookItem.bookOf(client.player.getMainHandStack());
                if (book == null) book = BookItem.bookOf(client.player.getOffHandStack());
                if (book == null) return;

                if (!(client.crosshairTarget instanceof BlockHitResult hitResult)) return;
                var item = client.world.getBlockState(hitResult.getBlockPos()).getBlock().asItem();
                if (item == Items.AIR) return;

                var associatedEntry = book.entryByAssociatedItem(item);
                if (associatedEntry == null || !associatedEntry.canPlayerView(client.player)) return;

                container.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                        .child(Components.item(associatedEntry.icon().getDefaultStack()).margins(Insets.of(0, 1, 0, 1)))
                        .child(Components.item(BookItem.itemOf(book)).sizing(Sizing.fixed(8)).positioning(Positioning.absolute(9, 9)).zIndex(50)));
                container.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                        .child(Components.label(Text.literal(associatedEntry.title())).shadow(true))
                        .child(Components.label(Text.translatable(client.player.isSneaking() ? "text.lavender.entry_hud.click_to_view" : "text.lavender.entry_hud.sneak_to_view"))));
            });
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            var stack = player.getStackInHand(hand);
            if (!player.isSneaking()) return ActionResult.PASS;

            var book = BookItem.bookOf(stack);
            if (book == null) return ActionResult.PASS;

            var item = world.getBlockState(hitResult.getBlockPos()).getBlock().asItem();
            if (item == Items.AIR) return ActionResult.PASS;

            var associatedEntry = book.entryByAssociatedItem(item);
            if (associatedEntry == null || !associatedEntry.canPlayerView((ClientPlayerEntity) player)) {
                return ActionResult.PASS;
            }

            BookScreen.pushEntry(book, associatedEntry);
            MinecraftClient.getInstance().setScreen(new BookScreen(book));

            player.swingHand(hand);
            return ActionResult.FAIL;
        });

        ClientPlayNetworking.registerGlobalReceiver(Lavender.WORLD_ID_CHANNEL, (client, handler, buf, responseSender) -> {
            currentWorldId = buf.readUuid();
        });
    }

    public static UUID currentWorldId() {
        return currentWorldId;
    }

    public static void registerTextureSize(int textureId, int width, int height) {
        TEXTURE_SIZES.put(textureId, Size.of(width, height));
    }

    public static @Nullable Size getTextureSize(Identifier texture) {
        return TEXTURE_SIZES.get(MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlId());
    }
}
