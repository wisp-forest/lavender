package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.wispforest.lavender.Lavender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LavenderBookmarks {

    private static final TypeToken<Map<Identifier, List<Identifier>>> BOOKMARKS_TYPE = new TypeToken<>() {};
    private static Map<Identifier, List<Identifier>> bookmarks;

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(Identifier.class, new Identifier.Serializer()).setPrettyPrinting().create();

    static {
        try {
            bookmarks = GSON.fromJson(Files.readString(bookmarksFile()), BOOKMARKS_TYPE);
        } catch (IOException e) {
            bookmarks = new HashMap<>();
            save();
        }
    }

    public static List<Entry> getBookmarks(Book book) {
        return bookmarks.containsKey(book.id())
                ? bookmarks.get(book.id()).stream().map(book::entryById).toList()
                : List.of();
    }

    public static void addBookmark(Book book, Entry entry) {
        bookmarks.computeIfAbsent(book.id(), $ -> new ArrayList<>()).add(entry.id());
        save();
    }

    public static void removeBookmark(Book book, Entry entry) {
        bookmarks.computeIfAbsent(book.id(), $ -> new ArrayList<>()).remove(entry.id());
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
}