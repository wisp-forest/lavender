package io.wispforest.lavender.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.BookContentLoader;
import io.wispforest.lavender.book.BookItem;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.owo.ops.TextOps;
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
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class LavenderClient implements ClientModInitializer {

    private static final Int2ObjectMap<Size> TEXTURE_SIZES = new Int2ObjectOpenHashMap<>();
    private static final Identifier ENTRY_HUD_ID = Lavender.id("entry_hud");

    private static final SimpleCommandExceptionType NO_SUCH_STRUCTURE = new SimpleCommandExceptionType(Text.literal("No such structure is loaded"));
    private static final SuggestionProvider<FabricClientCommandSource> STRUCTURE_INFO = (context, builder) ->
            CommandSource.suggestMatching(LavenderStructures.loadedStructures().stream().map(Identifier::toString), builder);

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

            dispatcher.register(literal("structure-overlay")
                    .then(literal("clear-all").executes(context -> {
                        StructureOverlayRenderer.clearOverlays();
                        return 0;
                    }))
                    .then(literal("add")
                            .then(argument("structure", IdentifierArgumentType.identifier()).suggests(STRUCTURE_INFO).executes(context -> {
                                var structureId = context.getArgument("structure", Identifier.class);
                                if (LavenderStructures.get(structureId) == null) throw NO_SUCH_STRUCTURE.create();

                                StructureOverlayRenderer.addPendingOverlay(structureId);
                                return 0;
                            }))));
        });

        StructureOverlayRenderer.initialize();
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
                        .child(Components.label(TextOps.withFormatting(client.player.isSneaking() ? "Click to view" : "Sneak to view", Formatting.GRAY))));
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
            if (associatedEntry == null || !associatedEntry.canPlayerView((ClientPlayerEntity) player))
                return ActionResult.PASS;

            BookScreen.pushEntry(book, associatedEntry);
            MinecraftClient.getInstance().setScreen(new BookScreen(book));

            player.swingHand(hand);
            return ActionResult.FAIL;
        });
    }

    public static void registerTextureSize(int textureId, int width, int height) {
        TEXTURE_SIZES.put(textureId, Size.of(width, height));
    }

    public static @Nullable Size getTextureSize(Identifier texture) {
        return TEXTURE_SIZES.get(MinecraftClient.getInstance().getTextureManager().getTexture(texture).getGlId());
    }
}
