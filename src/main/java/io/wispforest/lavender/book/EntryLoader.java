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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EntryLoader implements SynchronousResourceReloader, IdentifiableResourceReloadListener {

    private static final ResourceFinder ENTRY_FINDER = new ResourceFinder("lavender/entries", ".md");
    private static final Gson GSON = new GsonBuilder().setLenient().disableHtmlEscaping().create();

    private static final Map<Identifier, Entry> LOADED_ENTRIES = new HashMap<>();

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new EntryLoader());
    }

    public static @Nullable Entry getEntry(Identifier entryId) {
        return LOADED_ENTRIES.get(entryId);
    }

    public static Set<Identifier> loadedEntries() {
        return Collections.unmodifiableSet(LOADED_ENTRIES.keySet());
    }

    @Override
    public Identifier getFabricId() {
        return Lavender.id("entry_loader");
    }

    @Override
    public void reload(ResourceManager manager) {
        LOADED_ENTRIES.clear();
        ENTRY_FINDER.findResources(manager).forEach((resourceId, resource) -> {
            try {
                var rawEntry = IOUtils.toString(resource.getInputStream()).strip();
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

                LOADED_ENTRIES.put(
                        new Identifier(resourceId.getNamespace(), resourceId.getPath().substring("lavender/entries/".length(), resourceId.getPath().length() - 3)),
                        new Entry(title, icon, rawEntry)
                );
            } catch (Exception e) {
                Lavender.LOGGER.warn("Could not load entry {}", resourceId, e);
            }
        });
    }
}
