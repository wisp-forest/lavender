package io.wispforest.lavender.book;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wispforest.lavender.Lavender;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
    public void reload(ResourceManager manager) {
        reloadContents(manager);
    }

    public static void reloadContents(ResourceManager manager) {
        var entries = findResources(manager, ENTRY_FINDER);
        var categories = findResources(manager, CATEGORY_FINDER);

        for (var book : BookLoader.loadedBooks()) {
            forResourceOfBook(categories, book, "category", (identifier, resource) -> {
                var markdown = parseMarkdown(identifier, resource);
                if (markdown == null) return;

                book.addCategory(new Category(
                        identifier,
                        JsonHelper.getString(markdown.meta, "title"),
                        JsonHelper.getItem(markdown.meta, "icon"),
                        JsonHelper.getBoolean(markdown.meta, "secret", false),
                        markdown.content
                ));
            });
        }

        for (var book : BookLoader.loadedBooks()) {
            forResourceOfBook(entries, book, "entry", (identifier, resource) -> {
                var markdown = parseMarkdown(identifier, resource);
                if (markdown == null) return;

                var category = JsonHelper.getString(markdown.meta, "category", null);
                var categoryId = category != null
                        ? category.indexOf(':') > 0 ? Identifier.tryParse(category) : new Identifier(identifier.getNamespace(), category)
                        : null;

                var title = JsonHelper.getString(markdown.meta, "title");
                var icon = JsonHelper.getItem(markdown.meta, "icon", Items.AIR);
                var secret = JsonHelper.getBoolean(markdown.meta, "secret", false);

                var associatedItems = new ImmutableSet.Builder<Item>();
                for (var itemElement : JsonHelper.getArray(markdown.meta, "associated_items", new JsonArray())) {
                    associatedItems.add(JsonHelper.asItem(itemElement, "associated_items entry"));
                }

                var requiredAdvancements = new ImmutableSet.Builder<Identifier>();
                for (var advancementElement : JsonHelper.getArray(markdown.meta, "required_advancements", new JsonArray())) {
                    if (!advancementElement.isJsonPrimitive()) continue;

                    var advancementId = Identifier.tryParse(advancementElement.getAsString());
                    if (advancementId == null) {
                        Lavender.LOGGER.warn("Did not add advancement '{}' as requirement to entry '{}' as it is not a valid advancement identifier", advancementElement.getAsString(), identifier);
                        continue;
                    }

                    requiredAdvancements.add(advancementId);
                }

                var entry = new Entry(identifier, categoryId, title, icon, secret, requiredAdvancements.build(), associatedItems.build(), markdown.content);
                if (entry.id().getPath().equals("landing_page")) {
                    book.setLandingPage(entry);
                } else {
                    book.addEntry(entry);
                }
            });
        }
    }

    private static Map<String, Map<String, Resource>> findResources(ResourceManager manager, ResourceFinder finder) {
        var resources = new HashMap<String, Map<String, Resource>>();
        finder.findResources(manager).forEach((identifier, resource) -> {
            var resourceId = finder.toResourceId(identifier);
            resources.computeIfAbsent(resourceId.getNamespace(), s -> new HashMap<>()).put(resourceId.getPath(), resource);
        });

        return resources;
    }

    private static void forResourceOfBook(Map<String, Map<String, Resource>> resources, Book book, String resourceType, BiConsumer<Identifier, Resource> action) {
        if (!resources.containsKey(book.id().getNamespace())) return;

        var bookPrefix = book.id().getPath() + "/";
        resources.get(book.id().getNamespace()).forEach((path, resource) -> {
            if (!path.startsWith(bookPrefix)) return;

            var resourceId = new Identifier(book.id().getNamespace(), path.substring(bookPrefix.length()));
            try {
                action.accept(resourceId, resource);
            } catch (RuntimeException e) {
                Lavender.LOGGER.warn("Could not load {} '{}'", resourceType, resourceId, e);
            }
        });
    }

    private static @Nullable MarkdownResource parseMarkdown(Identifier resourceId, Resource resource) {
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

                return ResourceConditions.objectMatchesConditions(meta)
                        ? new MarkdownResource(meta, content)
                        : null;
            } else {
                throw new RuntimeException("Missing markdown meta");
            }
        } catch (Exception e) {
            Lavender.LOGGER.warn("Could not load markdown file {}", resourceId, e);
            return null;
        }
    }

    private record MarkdownResource(JsonObject meta, String content) {}
}
