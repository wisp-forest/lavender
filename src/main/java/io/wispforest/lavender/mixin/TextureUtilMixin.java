package io.wispforest.lavender.mixin;

import com.mojang.blaze3d.platform.TextureUtil;
import io.wispforest.lavender.client.LavenderClient;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureUtil.class)
public class TextureUtilMixin {

    @Inject(method = "prepareImage(Lnet/minecraft/client/texture/NativeImage$InternalFormat;IIII)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;bind(I)V"))
    private static void captureTextureSize(NativeImage.InternalFormat internalFormat, int id, int maxLevel, int width, int height, CallbackInfo ci) {
        LavenderClient.registerTextureSize(id, width, height);
    }

}
