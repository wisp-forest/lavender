package io.wispforest.lavender.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.structure.StructureInfo;
import io.wispforest.lavender.structure.StructureInfoLoader;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.event.WindowResizeCallback;
import io.wispforest.owo.ui.hud.Hud;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
    private static final Identifier HUD_COMPONENT_ID = Lavender.id("structure_overlay");

    public static void addOverlay(BlockPos anchorPoint, StructureInfo structure) {
        ACTIVE_OVERLAYS.put(anchorPoint, structure);
    }

    public static void clearOverlays() {
        ACTIVE_OVERLAYS.clear();
    }

    public static void initialize() {

        // --- overlay rendering setup ---

        WorldRenderEvents.LAST.register(context -> {
            var matrices = context.matrixStack();
            matrices.push();

            matrices.translate(-context.camera().getPos().x, -context.camera().getPos().y, -context.camera().getPos().z);

            var client = MinecraftClient.getInstance();
            var testPos = new BlockPos.Mutable();

            ACTIVE_OVERLAYS.forEach((anchor, structure) -> {
                matrices.push();
                matrices.translate(anchor.getX(), anchor.getY(), anchor.getZ());

                structure.forEachPredicate((pos, predicate) -> {
                    if (predicate.test(context.world().getBlockState(testPos.set(anchor).move(pos)))) {
                        return;
                    }

                    matrices.push();
                    matrices.translate(pos.getX(), pos.getY(), pos.getZ());

                    matrices.translate(.5, .5, .5);
                    matrices.scale(1.0001f, 1.0001f, 1.0001f);
                    matrices.translate(-.5, -.5, -.5);

                    client.getBlockRenderManager().renderBlockAsEntity(
                            predicate.preview(),
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

        // --- hud setup ---

        Hud.add(HUD_COMPONENT_ID, () -> Containers.verticalFlow(Sizing.content(), Sizing.content()).gap(5).positioning(Positioning.relative(10, 50)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || !(Hud.getComponent(HUD_COMPONENT_ID) instanceof FlowLayout hudComponent)) return;

            hudComponent.<FlowLayout>configure(layout -> {
                layout.clearChildren();

                ACTIVE_OVERLAYS.forEach((anchor, structure) -> {
                    var valid = structure.countValidStates(client.world, anchor);
                    var total = structure.nonNullPredicates;

                    layout.child(Components.label(Text.literal(StructureInfoLoader.getId(structure) + ": " + valid + " / " + total)));
                });
            });

        });
    }

}
