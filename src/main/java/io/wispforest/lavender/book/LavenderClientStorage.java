package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.client.LavenderClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LavenderClientStorage {

    private static final TypeToken<Map<UUID, Map<Identifier, List<Bookmark>>>> BOOKMARKS_TYPE = new TypeToken<>() {};
    private static Map<UUID, Map<Identifier, List<Bookmark>>> bookmarks;

    private static final TypeToken<Map<UUID, Set<Identifier>>> OPENED_BOOKS_TYPE = new TypeToken<>() {};
    private static Map<UUID, Set<Identifier>> openedBooks;

    private static final TypeToken<Map<UUID, Map<Identifier, Set<Identifier>>>> VIEWED_ENTRIES_TYPE = new TypeToken<>() {};
    private static Map<UUID, Map<Identifier, Set<Identifier>>> viewedEntries;

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Identifier.class, new Identifier.Serializer()).setPrettyPrinting().create();

    static {
        try {
            var data = GSON.fromJson(Files.readString(storageFile()), JsonObject.class);

            bookmarks = GSON.fromJson(data.get("bookmarks"), BOOKMARKS_TYPE);
            openedBooks = GSON.fromJson(data.get("opened_books"), OPENED_BOOKS_TYPE);
            viewedEntries = GSON.fromJson(data.get("viewed_entries"), VIEWED_ENTRIES_TYPE);

            if (bookmarks == null) bookmarks = new HashMap<>();
            if (openedBooks == null) openedBooks = new HashMap<>();
            if (viewedEntries == null) viewedEntries = new HashMap<>();
        } catch (Exception e) {
            bookmarks = new HashMap<>();
            openedBooks = new HashMap<>();
            viewedEntries = new HashMap<>();
            save();
        }
    }

    public static List<Bookmark> getBookmarks(Book book) {
        var worldBookmarks = bookmarks.get(LavenderClient.currentWorldId());
        if (worldBookmarks == null) return List.of();

        if (!worldBookmarks.containsKey(book.id())) return List.of();
        return worldBookmarks.get(book.id());
    }

    public static void addBookmark(Book book, Entry entry) {
        getBookmarkList(book).add(new Bookmark(Bookmark.Type.ENTRY, entry.id()));
        save();
    }

    public static void addBookmark(Book book, Category entry) {
        getBookmarkList(book).add(new Bookmark(Bookmark.Type.CATEGORY, entry.id()));
        save();
    }

    public static void removeBookmark(Book book, Bookmark bookmark) {
        getBookmarkList(book).remove(bookmark);
        save();
    }

    private static List<Bookmark> getBookmarkList(Book book) {
        return bookmarks.computeIfAbsent(LavenderClient.currentWorldId(), $ -> new HashMap<>()).computeIfAbsent(book.id(), $ -> new ArrayList<>());
    }

    public static boolean wasBookOpened(Identifier book) {
        return getOpenedBooksSet().contains(book);
    }

    public static void markBookOpened(Identifier book) {
        getOpenedBooksSet().add(book);
        save();
    }

    public static boolean wasEntryViewed(Book book, Entry entry) {
        return viewedEntries.containsKey(LavenderClient.currentWorldId())
                && viewedEntries.get(LavenderClient.currentWorldId()).containsKey(book.id())
                && viewedEntries.get(LavenderClient.currentWorldId()).get(book.id()).contains(entry.id());
    }

    public static void markEntryViewed(Book book, Entry entry) {
        viewedEntries.computeIfAbsent(LavenderClient.currentWorldId(), $ -> new HashMap<>()).computeIfAbsent(book.id(), $ -> new HashSet<>()).add(entry.id());
        save();
    }

    private static Set<Identifier> getOpenedBooksSet() {
        return openedBooks.computeIfAbsent(LavenderClient.currentWorldId(), $ -> new HashSet<>());
    }

    private static void save() {
        try {
            var data = new JsonObject();
            data.add("bookmarks", GSON.toJsonTree(bookmarks, BOOKMARKS_TYPE.getType()));
            data.add("opened_books", GSON.toJsonTree(openedBooks, OPENED_BOOKS_TYPE.getType()));
            data.add("viewed_entries", GSON.toJsonTree(viewedEntries, VIEWED_ENTRIES_TYPE.getType()));

            Files.writeString(storageFile(), GSON.toJson(data));
        } catch (IOException e) {
            Lavender.LOGGER.warn("Failed to save Lavender client data", e);
        }
    }

    private static Path storageFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavender_client_storage.json");
    }

    public record Bookmark(Type type, Identifier id) {
        public enum Type {
            ENTRY, CATEGORY;
        }

        public @Nullable Book.BookmarkableElement tryResolve(Book book) {
            return switch (this.type) {
                case CATEGORY -> book.categoryById(this.id);
                case ENTRY -> book.entryById(this.id);
            };
        }
    }
}