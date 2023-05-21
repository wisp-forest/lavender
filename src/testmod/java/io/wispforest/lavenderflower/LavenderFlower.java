package io.wispforest.lavenderflower;

import io.wispforest.lavender.book.BookItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class LavenderFlower implements ModInitializer {

    public static final String MOD_ID = "lavender-flower";

    @Override
    public void onInitialize() {
        BookItem.registerForBook(id("the_book"));
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
