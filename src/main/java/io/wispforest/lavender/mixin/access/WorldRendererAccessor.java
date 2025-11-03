package io.wispforest.lavender.mixin.access;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {
	@Accessor("world")
	@Nullable ClientWorld getWorld();
}
