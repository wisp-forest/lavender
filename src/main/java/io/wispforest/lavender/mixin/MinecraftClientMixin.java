package io.wispforest.lavender.mixin;

import io.wispforest.lavender.client.OffhandBookRenderer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void onFrameStart(boolean tick, CallbackInfo ci) {
        OffhandBookRenderer.beginFrame();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onFrameEnd(boolean tick, CallbackInfo ci) {
        OffhandBookRenderer.endFrame();
    }
}
