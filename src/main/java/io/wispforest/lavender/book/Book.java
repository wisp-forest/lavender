package io.wispforest.lavender.book;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Book {

    private final Identifier id;
    private final @Nullable Identifier texture;
    private final @Nullable Identifier dynamicBookModel;
    private final boolean displayCompletion;

    private final @Nullable Identifier extend;
    private @Nullable Book resolvedExtend = null;

    private final Map<Identifier, Category> categories = new HashMap<>();
    private final Collection<Category> categoriesView = Collections.unmodifiableCollection(this.categories.values());

    private final Map<Identifier, Entry> entriesById = new HashMap<>();
    private final Collection<Entry> entriesView = Collections.unmodifiableCollection(this.entriesById.values());

    private final Map<Category, List<Entry>> entriesByCategory = new HashMap<>();
    private final Map<Item, Entry> entriesByAssociatedItem = new HashMap<>();

    private final List<Entry> orphanedEntries = new ArrayList<>();
    private final Collection<Entry> orphanedEntriesView = Collections.unmodifiableCollection(this.orphanedEntries);

    private @Nullable Entry landingPage = null;

    public Book(Identifier id, @Nullable Identifier extend, @Nullable Identifier texture, @Nullable Identifier dynamicBookModel, boolean displayCompletion) {
        this.id = id;
        this.extend = extend;
        this.texture = texture;
        this.dynamicBookModel = dynamicBookModel;
        this.displayCompletion = displayCompletion;
    }

    public Identifier id() {
        return id;
    }

    public boolean displayCompletion() {
        return this.displayCompletion;
    }

    public Collection<Entry> entries() {
        return this.entriesView;
    }

    public Collection<Entry> orphanedEntries() {
        return this.orphanedEntriesView;
    }

    public @Nullable Entry entryById(Identifier entryId) {
        return this.entriesById.get(entryId);
    }

    public @Nullable Entry entryByAssociatedItem(Item associatedItem) {
        return this.entriesByAssociatedItem.get(associatedItem);
    }

    public @Nullable Collection<Entry> entriesByCategory(Category category) {
        var entries = this.entriesByCategory.get(category);
        if (entries == null) return null;

        return Collections.unmodifiableCollection(entries);
    }

    public Collection<Category> categories() {
        return this.categoriesView;
    }

    public @Nullable Category categoryById(Identifier categoryId) {
        return this.categories.get(categoryId);
    }

    public boolean shouldDisplayCategory(Category category, ClientPlayerEntity player) {
        var entries = this.entriesByCategory(category);
        if (entries == null) return false;

        boolean anyVisible = false;
        for (var entry : entries) {
            if (entry.canPlayerView(player)) anyVisible = true;
        }

        return anyVisible;
    }

    public @Nullable Entry landingPage() {
        return this.landingPage;
    }

    public @Nullable Identifier texture() {
        return this.texture;
    }

    public @Nullable Identifier dynamicBookModel() {
        return this.dynamicBookModel;
    }

    public int countVisibleEntries(ClientPlayerEntity player) {
        int visible = 0;
        for (var entry : this.entriesById.values()) {
            if (!entry.canPlayerView(player)) continue;
            visible++;
        }

        return visible;
    }

    void setLandingPage(@NotNull Entry landingPage) {
        this.landingPage = landingPage;
    }

    void addEntry(Entry entry) {
        if (this.resolvedExtend != null) {
            this.resolvedExtend.addEntry(entry);
        } else {
            this.entriesById.put(entry.id(), entry);
            entry.associatedItems().forEach(item -> this.entriesByAssociatedItem.put(item, entry));

            if (this.categories.containsKey(entry.category())) {
                this.entriesByCategory
                        .computeIfAbsent(this.categories.get(entry.category()), $ -> new ArrayList<>())
                        .add(entry);
            } else if (entry.category() == null) {
                this.orphanedEntries.add(entry);
            } else {
                throw new RuntimeException("Could not load entry '" + entry.id() + "' because category '" + entry.category() + "' was not found in book '" + this.effectiveId() + "'");
            }
        }
    }

    void addCategory(Category category) {
        if (this.resolvedExtend != null) {
            this.resolvedExtend.addCategory(category);
        } else {
            this.categories.put(category.id(), category);
        }
    }

    boolean tryResolveExtension() {
        if (this.extend == null) return true;

        this.resolvedExtend = BookLoader.get(this.extend);
        return this.resolvedExtend != null;
    }

    Identifier effectiveId() {
        return this.resolvedExtend != null ? this.resolvedExtend.effectiveId() : this.id;
    }

    public interface BookmarkableElement {
        String title();
        Item icon();
    }
}
