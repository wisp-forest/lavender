package io.wispforest.lavender.book;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Book {

    private final Identifier id;
    private final @Nullable Identifier texture;

    private final @Nullable Identifier extend;
    private @Nullable Book resolvedExtend = null;

    private final Map<Identifier, Entry> entries = new HashMap<>();
    private final Collection<Entry> entriesView = Collections.unmodifiableCollection(this.entries.values());

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

    public @Nullable Entry entryById(Identifier entryId) {
        return this.entries.get(entryId);
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
            this.entries.put(entry.id(), entry);
        }
    }

    boolean tryResolveExtension() {
        if (this.extend == null) return true;

        this.resolvedExtend = BookLoader.get(this.extend);
        return this.resolvedExtend != null;
    }
}
