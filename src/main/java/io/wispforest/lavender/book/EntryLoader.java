package io.wispforest.lavender.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.wispforest.lavender.Lavender;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.item.Items;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

public class EntryLoader implements SynchronousResourceReloader, IdentifiableResourceReloadListener {

    private static final ResourceFinder ENTRY_FINDER = new ResourceFinder("lavender/entries", ".md");
    private static final Gson GSON = new GsonBuilder().setLenient().disableHtmlEscaping().create();

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new EntryLoader());
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
        ENTRY_FINDER.findResources(manager).forEach((resourceId, resource) -> {
            var entryId = ENTRY_FINDER.toResourceId(resourceId);

            Book book = null;
            for (var candidate : BookLoader.loadedBooks()) {
                if (!entryId.getNamespace().equals(candidate.id().getNamespace())) continue;
                if (!entryId.getPath().startsWith(candidate.id().getPath() + "/")) continue;

                book = candidate;
                break;
            }

            if (book == null) return;

            try {
                var rawEntry = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8).strip();
                JsonObject meta;

                if (rawEntry.startsWith("```json")) {
                    rawEntry = rawEntry.substring("```json".length());
                    int frontmatterEnd = rawEntry.indexOf("```");
                    if (frontmatterEnd == -1) {
                        throw new RuntimeException("Unterminated entry meta");
                    }

                    meta = GSON.fromJson(rawEntry.substring(0, frontmatterEnd), JsonObject.class);
                    rawEntry = rawEntry.substring(frontmatterEnd + 3).stripLeading();
                } else {
                    throw new RuntimeException("Missing entry meta");
                }

                var title = JsonHelper.getString(meta, "title");
                var icon = JsonHelper.getItem(meta, "icon", Items.AIR);

                var entry = new Entry(new Identifier(entryId.getNamespace(), entryId.getPath().substring(book.id().getPath().length() + 1)),
                        title,
                        icon,
                        rawEntry
                );

                if (entry.id().getPath().equals("landing_page")) {
                    book.setLandingPage(entry);
                } else {
                    book.addEntry(entry);
                }
            } catch (Exception e) {
                Lavender.LOGGER.warn("Could not load entry {}", resourceId, e);
            }
        });
    }
}
