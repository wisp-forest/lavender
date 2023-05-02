package io.wispforest.lavender;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

public class Lavender implements ModInitializer {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String MOD_ID = "lavender";

    @Override
    public void onInitialize() {

    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
