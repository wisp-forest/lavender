package io.wispforest.lavender;

import com.mojang.logging.LogUtils;
import io.wispforest.lavender.book.BookItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public class Lavender implements ModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String MOD_ID = "lavender";

    public static final Item BOOK = new BookItem();

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("book"), BOOK);
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
