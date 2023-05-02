package io.wispforest.lavender.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.structure.StructureInfo;
import io.wispforest.owo.ui.event.WindowResizeCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL30C;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class StructureOverlayRenderer {

    private static final Supplier<Framebuffer> FRAMEBUFFER = Suppliers.memoize(() -> {
        var window = MinecraftClient.getInstance().getWindow();

        var framebuffer = new SimpleFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight(), true, MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.setClearColor(0f, 0f, 0f, 0f);
        return framebuffer;
    });

    private static final Map<BlockPos, StructureInfo> ACTIVE_OVERLAYS = new HashMap<>();

    public static void addOverlay(BlockPos anchorPoint, StructureInfo structure) {
        ACTIVE_OVERLAYS.put(anchorPoint, structure);
    }

    public static void clearOverlays(){
        ACTIVE_OVERLAYS.clear();
    }

    public static void initialize() {
        WorldRenderEvents.LAST.register(context -> {
            var matrices = context.matrixStack();
            matrices.push();

            matrices.translate(-context.camera().getPos().x, -context.camera().getPos().y, -context.camera().getPos().z);

            var client = MinecraftClient.getInstance();

            ACTIVE_OVERLAYS.forEach((anchor, structure) -> {
                matrices.push();
                matrices.translate(anchor.getX(), anchor.getY(), anchor.getZ());

                structure.forEachPreviewState((pos, state) -> {
                    matrices.push();
                    matrices.translate(pos.getX(), pos.getY(), pos.getZ());
                    client.getBlockRenderManager().renderBlockAsEntity(
                            state,
                            matrices,
                            context.consumers(),
                            LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE,
                            OverlayTexture.DEFAULT_UV
                    );
                    matrices.pop();
                });

                matrices.pop();
            });

            matrices.pop();

            var framebuffer = FRAMEBUFFER.get();
            framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            framebuffer.beginWrite(false);

            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, client.getFramebuffer().fbo);
            GL30C.glBlitFramebuffer(0, 0, framebuffer.textureWidth, framebuffer.textureHeight, 0, 0, client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight, GL30C.GL_DEPTH_BUFFER_BIT, GL30C.GL_NEAREST);

            if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
            client.getFramebuffer().beginWrite(false);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            client.gameRenderer.blitScreenProgram.colorModulator.set(new float[]{1, 1, 1, .6f + (float) (Math.sin(System.currentTimeMillis() / 1000d) / 8d)});
            framebuffer.draw(framebuffer.textureWidth, framebuffer.textureHeight, false);
        });

        WindowResizeCallback.EVENT.register((client, window) -> {
            FRAMEBUFFER.get().resize(window.getFramebufferWidth(), window.getFramebufferHeight(), MinecraftClient.IS_SYSTEM_MAC);
        });
    }

}
