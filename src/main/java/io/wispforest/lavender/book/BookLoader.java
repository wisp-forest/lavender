package io.wispforest.lavender.book;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.wispforest.lavender.Lavender;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BookLoader extends JsonDataLoader implements IdentifiableResourceReloadListener {

    private static final Map<Identifier, Book> LOADED_BOOKS = new HashMap<>();

    private BookLoader() {
        super(new GsonBuilder().setLenient().disableHtmlEscaping().create(), "lavender/books");
    }

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new BookLoader());
    }

    public static @Nullable Book get(Identifier bookId) {
        return LOADED_BOOKS.get(bookId);
    }

    public static Collection<Book> loadedBooks() {
        return Collections.unmodifiableCollection(LOADED_BOOKS.values());
    }

    @Override
    public Identifier getFabricId() {
        return Lavender.id("book_loader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        LOADED_BOOKS.clear();
        prepared.forEach((resourceId, jsonElement) -> {
            if (!jsonElement.isJsonObject()) return;
            var bookObject = jsonElement.getAsJsonObject();

            var texture = JsonHelper.getString(bookObject, "texture", null);
            var textureId = texture != null ? Identifier.tryParse(texture) : null;

            var extend = JsonHelper.getString(bookObject, "extend", null);
            var extendId = extend != null ? Identifier.tryParse(extend) : null;

            LOADED_BOOKS.put(resourceId, new Book(resourceId, extendId, textureId));
        });

        LOADED_BOOKS.values().removeIf(book -> {
            if (book.tryResolveExtension()) return false;

            Lavender.LOGGER.warn("Book '" + book.id() + "' (an extension) failed to load because its target was not found");
            return true;
        });
    }
}
