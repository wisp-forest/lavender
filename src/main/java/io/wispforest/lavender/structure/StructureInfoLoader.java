package io.wispforest.lavender.structure;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.wispforest.lavender.Lavender;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StructureInfoLoader {

    private static final Map<Identifier, JsonObject> PENDING_STRUCTURES = new HashMap<>();
    private static final BiMap<Identifier, StructureInfo> LOADED_STRUCTURES = HashBiMap.create();

    private static boolean tagsAvailable = false;

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ReloadListener());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tagsAvailable = false);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            tagsAvailable = true;
            tryParseStructures();
        });
    }

    public static Set<Identifier> loadedStructures() {
        return LOADED_STRUCTURES.keySet();
    }

    public static @Nullable StructureInfo get(Identifier structure) {
        return LOADED_STRUCTURES.get(structure);
    }

    public static @Nullable Identifier getId(StructureInfo structure) {
        return LOADED_STRUCTURES.inverse().get(structure);
    }

    private static void tryParseStructures() {
        LOADED_STRUCTURES.clear();
        PENDING_STRUCTURES.forEach((identifier, pending) -> {
            try {
                LOADED_STRUCTURES.put(identifier, StructureInfo.parse(pending));
            } catch (JsonParseException e) {
                Lavender.LOGGER.warn("Failed to load structure info {}", identifier, e);
            }
        });
    }

    private static class ReloadListener extends JsonDataLoader implements IdentifiableResourceReloadListener {
        public ReloadListener() {
            super(new GsonBuilder().setLenient().disableHtmlEscaping().create(), "lavender/structures");
        }

        @Override
        public Identifier getFabricId() {
            return Lavender.id("structure_info_loader");
        }

        @Override
        protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
            PENDING_STRUCTURES.clear();

            prepared.forEach((resourceId, jsonElement) -> {
                if (!jsonElement.isJsonObject()) return;
                PENDING_STRUCTURES.put(resourceId, jsonElement.getAsJsonObject());
            });

            if (tagsAvailable) tryParseStructures();
        }
    }

}
