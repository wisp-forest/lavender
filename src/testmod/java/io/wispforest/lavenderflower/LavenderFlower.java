package io.wispforest.lavenderflower;

import io.wispforest.lavender.book.LavenderBookItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

public class LavenderFlower implements ModInitializer {

    public static final String MOD_ID = "lavender-flower";

    @Override
    public void onInitialize() {
        LavenderBookItem.registerForBook(id("the_book"), new Item.Settings());
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
