package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.wispforest.lavender.Lavender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LavenderBookmarks {

    private static final TypeToken<Map<Identifier, List<Bookmark>>> BOOKMARKS_TYPE = new TypeToken<>() {};
    private static Map<Identifier, List<Bookmark>> bookmarks;

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
        return bookmarks.containsKey(book.id())
                ? Collections.unmodifiableList(bookmarks.get(book.id()))
                : List.of();
    }

    public static void addBookmark(Book book, Entry entry) {
        bookmarks.computeIfAbsent(book.id(), $ -> new ArrayList<>()).add(new Bookmark(Bookmark.Type.ENTRY, entry.id()));
        save();
    }

    public static void addBookmark(Book book, Category entry) {
        bookmarks.computeIfAbsent(book.id(), $ -> new ArrayList<>()).add(new Bookmark(Bookmark.Type.CATEGORY, entry.id()));
        save();
    }

    public static void removeBookmark(Book book, Bookmark bookmark) {
        bookmarks.computeIfAbsent(book.id(), $ -> new ArrayList<>()).remove(bookmark);
        save();
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