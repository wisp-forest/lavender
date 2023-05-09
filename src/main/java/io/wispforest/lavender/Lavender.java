package io.wispforest.lavender;

import com.mojang.logging.LogUtils;
import io.wispforest.lavender.client.BookScreen;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.slf4j.Logger;

public class Lavender implements ModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String MOD_ID = "lavender";

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("book"), new Item(new Item.Settings().maxCount(1)) {
            @Override
            public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
                if (!world.isClient) return TypedActionResult.success(user.getStackInHand(hand));

                MinecraftClient.getInstance().setScreen(new BookScreen());
                return TypedActionResult.success(user.getStackInHand(hand));
            }
        });
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
