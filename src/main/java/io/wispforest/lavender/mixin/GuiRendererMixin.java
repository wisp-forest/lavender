package io.wispforest.lavender.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wispforest.lavender.client.GuiRendererFramebufferOverride;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin implements GuiRendererFramebufferOverride {
	@Unique
	private Framebuffer override;

	@Override
	public void lavender$setOverride(Framebuffer override) {
		this.override = override;
	}

	@Override
	public Framebuffer lavender$getOverride() {
		return override;
	}

	@WrapOperation(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
	private Framebuffer overrideRenderFramebuffer(MinecraftClient instance, Operation<Framebuffer> original) {
		if (override != null) return override;
		return original.call(instance);
	}
}
