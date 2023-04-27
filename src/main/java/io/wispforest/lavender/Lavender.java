package io.wispforest.lavender;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class Lavender implements ModInitializer {

    public static final String MOD_ID = "lavender";

    @Override
    public void onInitialize() {

    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
