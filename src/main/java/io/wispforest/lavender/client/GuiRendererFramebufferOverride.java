package io.wispforest.lavender.client;

import net.minecraft.client.gl.Framebuffer;

public interface GuiRendererFramebufferOverride {
	void lavender$setOverride(Framebuffer override);
	Framebuffer lavender$getOverride();
}
