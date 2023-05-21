package io.wispforest.lavender;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.BookItem;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.client.EditMdScreen;
import io.wispforest.lavender.client.StructureOverlayRenderer;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.structure.LavenderStructures;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LavenderCommands {

    private static final SimpleCommandExceptionType NO_SUCH_BOOK = new SimpleCommandExceptionType(Text.literal("No such book is loaded"));

    private static final SuggestionProvider<ServerCommandSource> LOADED_BOOKS = (context, builder) -> {
        return CommandSource.suggestIdentifiers(BookLoader.loadedBooks().stream().map(Book::id), builder);
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("get-lavender-book")
                .then(argument("book_id", IdentifierArgumentType.identifier()).suggests(LOADED_BOOKS)
                        .executes(context -> executeGetLavenderBook(context, false))
                        .then(argument("force_dynamic_book", BoolArgumentType.bool())
                                .executes(context -> executeGetLavenderBook(context, BoolArgumentType.getBool(context, "force_dynamic_book"))))));

    }

    private static int executeGetLavenderBook(CommandContext<ServerCommandSource> context, boolean forceDynamicBook) throws CommandSyntaxException {
        var book = BookLoader.get(IdentifierArgumentType.getIdentifier(context, "book_id"));
        if (book == null) {
            throw NO_SUCH_BOOK.create();
        }

        context.getSource().getPlayer().getInventory().offerOrDrop(forceDynamicBook
                ? BookItem.createDynamic(book)
                : BookItem.itemOf(book)
        );

        return 0;
    }

    @Environment(EnvType.CLIENT)
    public static class Client {

        private static final SimpleCommandExceptionType NO_SUCH_STRUCTURE = new SimpleCommandExceptionType(Text.literal("No such structure is loaded"));
        private static final SuggestionProvider<FabricClientCommandSource> STRUCTURE_INFO = (context, builder) ->
                CommandSource.suggestMatching(LavenderStructures.loadedStructures().stream().map(Identifier::toString), builder);

        public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
            dispatcher.register(ClientCommandManager.literal("parse-md").then(ClientCommandManager.argument("md", StringArgumentType.greedyString()).executes(context -> {
                context.getSource().sendFeedback(MarkdownProcessor.TEXT.process(StringArgumentType.getString(context, "md")));
                return 0;
            })));

            dispatcher.register(ClientCommandManager.literal("edit-md").executes(context -> {
                MinecraftClient.getInstance().setScreen(new EditMdScreen());
                return 0;
            }));

            dispatcher.register(ClientCommandManager.literal("structure-overlay")
                    .then(ClientCommandManager.literal("clear-all").executes(context -> {
                        StructureOverlayRenderer.clearOverlays();
                        return 0;
                    }))

                    .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("structure", IdentifierArgumentType.identifier()).suggests(STRUCTURE_INFO).executes(context -> {
                                var structureId = context.getArgument("structure", Identifier.class);
                                if (LavenderStructures.get(structureId) == null) throw NO_SUCH_STRUCTURE.create();

                                StructureOverlayRenderer.addPendingOverlay(structureId);
                                return 0;
                            }))));
        }
    }

}
