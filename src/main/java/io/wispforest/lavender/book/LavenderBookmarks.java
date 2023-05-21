package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

public class LavenderBookmarks {

    private static final TypeToken<Map<UUID, Map<Identifier, List<Bookmark>>>> BOOKMARKS_TYPE = new TypeToken<>() {};
    private static Map<UUID, Map<Identifier, List<Bookmark>>> bookmarks;

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Identifier.class, new Identifier.Serializer()).setPrettyPrinting().create();

    static {
        try {
            bookmarks = GSON.fromJson(Files.readString(bookmarksFile()), BOOKMARKS_TYPE);
        } catch (Exception e) {
            bookmarks = new HashMap<>();
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

    private static void save() {
        try {
            Files.writeString(bookmarksFile(), GSON.toJson(bookmarks, BOOKMARKS_TYPE.getType()));
        } catch (IOException e) {
            Lavender.LOGGER.warn("Failed to save bookmarks", e);
        }
    }

    private static Path bookmarksFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("lavender_bookmarks.json");
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