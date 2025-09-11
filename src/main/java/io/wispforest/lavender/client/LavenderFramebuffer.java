package io.wispforest.lavender.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

public class LavenderFramebuffer extends SimpleFramebuffer {
	protected final RenderPipeline pipeline;

	public LavenderFramebuffer(@Nullable String name, int width, int height, boolean useDepthAttachment, RenderPipeline pipeline) {
		super(name, width, height, useDepthAttachment);
		this.pipeline = pipeline;
	}

	public LavenderFramebuffer(@Nullable String name, int width, int height, boolean useDepthAttachment) {
		this(name, width, height, useDepthAttachment, RenderPipelines.ENTITY_OUTLINE_BLIT);
	}

	public void clear() {
		if (this.useDepthAttachment) {
			RenderSystem.getDevice()
					.createCommandEncoder()
					.clearColorAndDepthTextures(colorAttachment, 0, depthAttachment, 1.0);
		} else {
			RenderSystem.getDevice().createCommandEncoder().clearColorTexture(colorAttachment, 0);
		}

		RenderSystem.getDevice()
				.createCommandEncoder()
				.clearColorAndDepthTextures(colorAttachment, 0, depthAttachment, 1.0);
	}

	@Override
	public void drawBlit(GpuTextureView texture) {
		RenderSystem.assertOnRenderThread();
		RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		GpuBuffer indexBuffer = shapeIndexBuffer.getIndexBuffer(6);
		GpuBuffer vertexBuffer = RenderSystem.getQuadVertexBuffer();

		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Blit render target", texture, OptionalInt.empty())) {
			renderPass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setVertexBuffer(0, vertexBuffer);
			renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.getIndexType());
			renderPass.bindSampler("InSampler", this.colorAttachmentView);
			renderPass.drawIndexed(0, 0, 6, 1);
		}
	}
}
