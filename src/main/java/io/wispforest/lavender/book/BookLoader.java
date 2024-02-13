package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.client.BookBakedModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
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
    private static final TypeToken<Map<String, String>> MACROS_TOKEN = new TypeToken<>() {};
    private static final ResourceFinder BOOK_FINDER = ResourceFinder.json("lavender/books");

    private static final Map<Identifier, Book> LOADED_BOOKS = new HashMap<>();
    private static final Map<Identifier, Book> VISIBLE_BOOKS = new HashMap<>();

    public static void initialize() {
        ModelLoadingPlugin.register(context -> {
            context.addModels(BookBakedModel.Unbaked.BROWN_BOOK_ID);
            for (var book : VISIBLE_BOOKS.values()) {
                if (book.dynamicBookModel() == null) return;
                context.addModels(new ModelIdentifier(book.dynamicBookModel(), "inventory"));
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

            var textureId = tryGetId(bookObject, "texture");
            var extendId = tryGetId(bookObject, "extend");
            var dynamicBookModelId = tryGetId(bookObject, "dynamic_book_model");

            var openSoundId = tryGetId(bookObject, "open_sound");
            var openSoundEvent = openSoundId != null ? Registries.SOUND_EVENT.get(openSoundId) : null;
            var flippingSoundId = tryGetId(bookObject, "flipping_sound");
            var flippingSoundEvent = flippingSoundId != null ? Registries.SOUND_EVENT.get(flippingSoundId) : null;

            var introEntryId = tryGetId(bookObject, "intro_entry");

            var displayCompletion = JsonHelper.getBoolean(bookObject, "display_completion", false);
            var displayUnreadEntryNotifications = JsonHelper.getBoolean(bookObject, "display_unread_entry_notifications", true);
            var macros = GSON.fromJson(JsonHelper.getObject(bookObject, "macros", new JsonObject()), MACROS_TOKEN);

            var book = new Book(resourceId, extendId, textureId, dynamicBookModelId, openSoundEvent, flippingSoundEvent, introEntryId, displayUnreadEntryNotifications, displayCompletion, macros);
            LOADED_BOOKS.put(resourceId, book);
            if (extendId == null) VISIBLE_BOOKS.put(resourceId, book);
        });

        LOADED_BOOKS.values().removeIf(book -> {
            if (book.tryResolveExtension()) return false;

            Lavender.LOGGER.warn("Book '" + book.id() + "' (an extension) failed to load because its target was not found");
            return true;
        });
    }

    private static @Nullable Identifier tryGetId(JsonObject json, String key) {
        var jsonString = JsonHelper.getString(json, key, null);
        if (jsonString == null) return null;

        return Identifier.tryParse(jsonString);
    }
}
