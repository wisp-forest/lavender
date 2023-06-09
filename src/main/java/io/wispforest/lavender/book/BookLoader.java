package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.client.BookBakedModel;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BookLoader {

    private static final Gson GSON = new GsonBuilder().setLenient().disableHtmlEscaping().create();
    private static final ResourceFinder BOOK_FINDER = ResourceFinder.json("lavender/books");

    private static final Map<Identifier, Book> LOADED_BOOKS = new HashMap<>();
    private static final Map<Identifier, Book> VISIBLE_BOOKS = new HashMap<>();

    public static void initialize() {
        ModelLoadingRegistry.INSTANCE.registerModelProvider(($, out) -> {
            out.accept(BookBakedModel.Unbaked.BROWN_BOOK_ID);
            for (var book : VISIBLE_BOOKS.values()) {
                if (book.dynamicBookModel() == null) return;
                out.accept(new ModelIdentifier(book.dynamicBookModel(), "inventory"));
            }
        });
    }

    public static @Nullable Book get(Identifier bookId) {
        return LOADED_BOOKS.get(bookId);
    }

    public static Collection<Book> loadedBooks() {
        return Collections.unmodifiableCollection(VISIBLE_BOOKS.values());
    }

    static Collection<Book> allBooks() {
        return Collections.unmodifiableCollection(LOADED_BOOKS.values());
    }

    public static void reload(ResourceManager manager) {
        LOADED_BOOKS.clear();
        BOOK_FINDER.findResources(manager).forEach((identifier, resource) -> {
            JsonElement jsonElement;
            try (var reader = resource.getReader()) {
                jsonElement = JsonHelper.deserialize(GSON, reader, JsonElement.class);
            } catch (IOException e) {
                Lavender.LOGGER.warn("Could not load book '{}'", identifier, e);
                return;
            }

            if (!jsonElement.isJsonObject()) return;
            var bookObject = jsonElement.getAsJsonObject();
            var resourceId = BOOK_FINDER.toResourceId(identifier);

            var texture = JsonHelper.getString(bookObject, "texture", null);
            var textureId = texture != null ? Identifier.tryParse(texture) : null;

            var extend = JsonHelper.getString(bookObject, "extend", null);
            var extendId = extend != null ? Identifier.tryParse(extend) : null;

            var dynamicBookModel = JsonHelper.getString(bookObject, "dynamic_book_model", null);
            var dynamicBookModelId = dynamicBookModel != null ? Identifier.tryParse(dynamicBookModel) : null;

            var displayCompletion = JsonHelper.getBoolean(bookObject, "display_completion", false);

            var book = new Book(resourceId, extendId, textureId, dynamicBookModelId, displayCompletion);
            LOADED_BOOKS.put(resourceId, book);
            if (extendId == null) VISIBLE_BOOKS.put(resourceId, book);
        });

        LOADED_BOOKS.values().removeIf(book -> {
            if (book.tryResolveExtension()) return false;

            Lavender.LOGGER.warn("Book '" + book.id() + "' (an extension) failed to load because its target was not found");
            return true;
        });
    }
}
