package io.wispforest.lavender.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wispforest.lavender.client.LavenderClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderPhase.class)
public class RenderPhaseMixin {
    @ModifyReturnValue(method = {"method_68490", "method_68488", "method_68485"}, at = @At("RETURN"))
    private static Framebuffer injectProperRenderTarget(Framebuffer original) {
        if (LavenderClient.mainTargetOverride != null) {
            return LavenderClient.mainTargetOverride;
        }

        return original;
    }
}
