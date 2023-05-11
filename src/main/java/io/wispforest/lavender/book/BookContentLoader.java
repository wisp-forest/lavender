package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.wispforest.lavender.Lavender;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.item.Items;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class BookContentLoader implements SynchronousResourceReloader, IdentifiableResourceReloadListener {

    private static final ResourceFinder ENTRY_FINDER = new ResourceFinder("lavender/entries", ".md");
    private static final ResourceFinder CATEGORY_FINDER = new ResourceFinder("lavender/categories", ".md");
    private static final Gson GSON = new GsonBuilder().setLenient().disableHtmlEscaping().create();

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new BookContentLoader());
    }

    @Override
    public Identifier getFabricId() {
        return Lavender.id("entry_loader");
    }

    @Override
    public Collection<Identifier> getFabricDependencies() {
        return List.of(Lavender.id("book_loader"));
    }

    @Override
    public void reload(ResourceManager manager) {
        var entries = this.findResources(manager, ENTRY_FINDER);
        var categories = this.findResources(manager, CATEGORY_FINDER);

        for (var book : BookLoader.loadedBooks()) {
            this.forResourceOfBook(categories, book, (identifier, resource) -> {
                var markdown = this.parseMarkdown(identifier, resource);
                if (markdown == null) return;

                var icon = JsonHelper.getItem(markdown.meta, "icon");
                book.addCategory(new Category(identifier, icon, markdown.content));
            });

            this.forResourceOfBook(entries, book, (identifier, resource) -> {
                var markdown = this.parseMarkdown(identifier, resource);
                if (markdown == null) return;

                var category = JsonHelper.getString(markdown.meta, "category", null);
                var categoryId = category != null ? new Identifier(identifier.getNamespace(), category) : null;

                // TODO this needs to respect extensions
                if (categoryId != null && book.categoryById(categoryId) == null) {
                    Lavender.LOGGER.warn("Could not load entry '{}' because the category '{}' does not exist in book '{}'", identifier, categoryId, book.id());
                    return;
                }

                var title = JsonHelper.getString(markdown.meta, "title");
                var icon = JsonHelper.getItem(markdown.meta, "icon", Items.AIR);

                var entry = new Entry(identifier, categoryId, title, icon, markdown.content);
                if (entry.id().getPath().equals("landing_page")) {
                    book.setLandingPage(entry);
                } else {
                    book.addEntry(entry);
                }
            });
        }
    }

    private Map<String, Map<String, Resource>> findResources(ResourceManager manager, ResourceFinder finder) {
        var resources = new HashMap<String, Map<String, Resource>>();
        finder.findResources(manager).forEach((identifier, resource) -> {
            var resourceId = finder.toResourceId(identifier);
            resources.computeIfAbsent(resourceId.getNamespace(), s -> new HashMap<>()).put(resourceId.getPath(), resource);
        });

        return resources;
    }

    private void forResourceOfBook(Map<String, Map<String, Resource>> resources, Book book, BiConsumer<Identifier, Resource> action) {
        if (!resources.containsKey(book.id().getNamespace())) return;

        var bookPrefix = book.id().getPath() + "/";
        resources.get(book.id().getNamespace()).forEach((path, resource) -> {
            if (!path.startsWith(bookPrefix)) return;
            action.accept(new Identifier(book.id().getNamespace(), path.substring(bookPrefix.length())), resource);
        });
    }

    private @Nullable MarkdownResource parseMarkdown(Identifier resourceId, Resource resource) {
        try {
            var content = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8).strip();
            JsonObject meta;

            if (content.startsWith("```json")) {
                content = content.substring("```json".length());
                int frontmatterEnd = content.indexOf("```");
                if (frontmatterEnd == -1) {
                    throw new RuntimeException("Unterminated markdown meta");
                }

                meta = GSON.fromJson(content.substring(0, frontmatterEnd), JsonObject.class);
                content = content.substring(frontmatterEnd + 3).stripLeading();

                return new MarkdownResource(meta, content);
            } else {
                throw new RuntimeException("Missing markdown meta");
            }
        } catch (Exception e) {
            Lavender.LOGGER.warn("Could not load markdown file {}", resourceId, e);
            return null;
        }
    }

    private record MarkdownResource(JsonObject meta, String content) {
    }
}
