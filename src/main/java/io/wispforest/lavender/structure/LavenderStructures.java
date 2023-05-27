package io.wispforest.lavender.structure;

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LavenderStructures {

    private static final Map<Identifier, JsonObject> PENDING_STRUCTURES = new HashMap<>();
    private static final Map<Identifier, StructureTemplate> LOADED_STRUCTURES = new HashMap<>();

    private static boolean tagsAvailable = false;

    @ApiStatus.Internal
    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new ReloadListener());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tagsAvailable = false);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            tagsAvailable = true;
            tryParseStructures();
        });
    }

    /**
     * @return A view over the identifiers of all currently loaded structures
     */
    public static Set<Identifier> loadedStructures() {
        return Collections.unmodifiableSet(LOADED_STRUCTURES.keySet());
    }

    /**
     * @return The structure currently associated with the given id,
     * or {@code null} if no such structure is loaded
     */
    public static @Nullable StructureTemplate get(Identifier structureId) {
        return LOADED_STRUCTURES.get(structureId);
    }

    private static void tryParseStructures() {
        LOADED_STRUCTURES.clear();
        PENDING_STRUCTURES.forEach((identifier, pending) -> {
            try {
                LOADED_STRUCTURES.put(identifier, StructureTemplate.parse(identifier, pending));
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
