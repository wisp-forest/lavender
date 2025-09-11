package io.wispforest.lavender;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.wispforest.endec.Endec;
import io.wispforest.endec.impl.BuiltInEndecs;
import io.wispforest.endec.impl.StructEndecBuilder;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.serialization.CodecUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import org.slf4j.Logger;

import java.util.UUID;

public class Lavender implements ModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "lavender";
    public static final SoundEvent ITEM_BOOK_OPEN = SoundEvent.of(id("item.book.open"));

    public static final OwoNetChannel CHANNEL = OwoNetChannel.create(Lavender.id("main"));

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("dynamic_book"), LavenderBookItem.DYNAMIC_BOOK);
        Registry.register(Registries.SOUND_EVENT, ITEM_BOOK_OPEN.id(), ITEM_BOOK_OPEN);

        PayloadTypeRegistry.playS2C().register(WorldUUIDPayload.ID, CodecUtils.toPacketCodec(WorldUUIDPayload.ENDEC));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new WorldUUIDPayload(server.getOverworld().getPersistentStateManager().getOrCreate(WorldUUIDState.TYPE).id));
        });

        LavenderClientRecipeCache.initialize();
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static class WorldUUIDState extends PersistentState {

		public static final Codec<WorldUUIDState> CODEC = RecordCodecBuilder.create(
				instance -> instance.ap(WorldUUIDState::new, Uuids.INT_STREAM_CODEC.fieldOf("UUID").forGetter(w -> w.id))
		);

        public static final PersistentStateType<WorldUUIDState> TYPE = new PersistentStateType<>("lavender_world_id", () -> {
            var state = new WorldUUIDState(UUID.randomUUID());
            state.markDirty();
            return state;
        }, CODEC, DataFixTypes.LEVEL);

        public final UUID id;

        private WorldUUIDState(UUID id) {
            this.id = id;
        }
    }

    public record WorldUUIDPayload(UUID worldUuid) implements CustomPayload {
        public static final CustomPayload.Id<WorldUUIDPayload> ID = new CustomPayload.Id<>(Lavender.id("world_uuid"));
        public static final Endec<WorldUUIDPayload> ENDEC = StructEndecBuilder.of(
                BuiltInEndecs.UUID.fieldOf("world_uuid", WorldUUIDPayload::worldUuid),
                WorldUUIDPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
