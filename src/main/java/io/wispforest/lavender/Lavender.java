package io.wispforest.lavender;

import com.mojang.logging.LogUtils;
import io.wispforest.lavender.book.BookItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;

import java.util.UUID;

public class Lavender implements ModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "lavender";
    public static final SoundEvent ITEM_BOOK_OPEN = SoundEvent.of(id("item.book.open"));

    public static final Identifier WORLD_ID_CHANNEL = Lavender.id("world_id_channel");

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("dynamic_book"), BookItem.DYNAMIC_BOOK);
        Registry.register(Registries.SOUND_EVENT, ITEM_BOOK_OPEN.getId(), ITEM_BOOK_OPEN);

        CommandRegistrationCallback.EVENT.register(LavenderCommands::register);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var data = PacketByteBufs.create();
            data.writeUuid(server.getOverworld().getPersistentStateManager().getOrCreate(WorldUUIDState::read, () -> {
                var state = new WorldUUIDState(UUID.randomUUID());
                state.markDirty();
                return state;
            }, "lavender_world_id").id);

            sender.sendPacket(WORLD_ID_CHANNEL, data);
        });
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    public static class WorldUUIDState extends PersistentState {

        public final UUID id;

        private WorldUUIDState(UUID id) {
            this.id = id;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.putUuid("UUID", id);
            return nbt;
        }

        public static WorldUUIDState read(NbtCompound nbt) {
            return new WorldUUIDState(nbt.contains("UUID", NbtElement.INT_ARRAY_TYPE) ? nbt.getUuid("UUID") : null);
        }
    }
}
