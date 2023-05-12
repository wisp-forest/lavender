package io.wispforest.lavender.book;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Book {

    private final Identifier id;
    private final @Nullable Identifier texture;

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

    public Book(Identifier id, @Nullable Identifier extend, @Nullable Identifier texture) {
        this.id = id;
        this.extend = extend;
        this.texture = texture;
    }

    public Identifier id() {
        return id;
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

    public @Nullable Entry landingPage() {
        return this.landingPage;
    }

    public @Nullable Identifier texture() {
        return texture;
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
}
