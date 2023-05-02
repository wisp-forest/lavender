package io.wispforest.lavender.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.structure.StructureInfo;
import io.wispforest.lavender.structure.StructureInfoLoader;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.event.WindowResizeCallback;
import io.wispforest.owo.ui.hud.Hud;
import io.wispforest.owo.ui.util.Delta;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
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

    private static final Map<BlockPos, OverlayEntry> ACTIVE_OVERLAYS = new HashMap<>();

    private static final Identifier HUD_COMPONENT_ID = Lavender.id("structure_overlay");
    private static final Identifier BARS_TEXTURE = new Identifier("textures/gui/bars.png");

    public static void addOverlay(BlockPos anchorPoint, Identifier structure) {
        ACTIVE_OVERLAYS.put(anchorPoint, new OverlayEntry(structure));
    }

    public static void clearOverlays() {
        ACTIVE_OVERLAYS.clear();
    }

    public static void initialize() {
        Hud.add(HUD_COMPONENT_ID, () -> Containers.verticalFlow(Sizing.content(), Sizing.content()).gap(15).positioning(Positioning.relative(5, 100)));

        WorldRenderEvents.LAST.register(context -> {
            if (!(Hud.getComponent(HUD_COMPONENT_ID) instanceof FlowLayout hudComponent)) {
                return;
            }

            var matrices = context.matrixStack();
            matrices.push();

            matrices.translate(-context.camera().getPos().x, -context.camera().getPos().y, -context.camera().getPos().z);

            var client = MinecraftClient.getInstance();
            var testPos = new BlockPos.Mutable();

            hudComponent.<FlowLayout>configure(layout -> {
                layout.clearChildren().padding(Insets.bottom((client.getWindow().getScaledWidth() - 182) / 2 < 200 ? 50 : 5));

                ACTIVE_OVERLAYS.keySet().removeIf(anchor -> {
                    var entry = ACTIVE_OVERLAYS.get(anchor);
                    var structure = entry.fetchStructure();
                    if (structure == null) return true;

                    // --- overlay rendering ---

                    var hasInvalidBlock = new MutableBoolean();

                    if (entry.decayTime < 0) {
                        matrices.push();
                        matrices.translate(anchor.getX(), anchor.getY(), anchor.getZ());

                        structure.forEachPredicate((pos, predicate) -> {
                            var state = context.world().getBlockState(testPos.set(anchor).move(pos));
                            if (predicate.test(state)) {
                                return;
                            } else if (!state.isAir()) {
                                hasInvalidBlock.setTrue();
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
                    }

                    // --- hud setup ---

                    var valid = structure.countValidStates(client.world, anchor);
                    var total = structure.nonNullPredicates;
                    if (entry.decayTime >= 0) valid = total;

                    int barTextureOffset = 0;
                    if (hasInvalidBlock.booleanValue()) barTextureOffset = 10;
                    if (valid == total) barTextureOffset = 20;

                    entry.visualCompleteness += Delta.compute(entry.visualCompleteness, valid / (float) total, client.getLastFrameDuration());
                    layout.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                            .child(Components.label(Text.translatable(Util.createTranslationKey("structure", entry.structureId)).append(Text.literal(": " + valid + " / " + total))).shadow(true))
                            .child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                                    .child(Components.texture(BARS_TEXTURE, 0, 10 + barTextureOffset, 182, 5))
                                    .child(Components.texture(BARS_TEXTURE, 0, 15 + barTextureOffset, Math.round(182 * entry.visualCompleteness), 5).positioning(Positioning.absolute(0, 0)))
                                    .child(Components.texture(BARS_TEXTURE, 0, 115, 182, 5).blend(true).positioning(Positioning.absolute(0, 0))))
                            .gap(2)
                            .horizontalAlignment(HorizontalAlignment.CENTER)
                            .margins(Insets.bottom((int) (Easing.CUBIC.apply((Math.max(0, entry.decayTime - 30) + client.getTickDelta()) / 20f) * -32))));

                    if (entry.decayTime < 0 && valid == total) {
                        entry.decayTime = 0;
                        client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, .75f);
                    } else if (entry.decayTime >= 0) {
                        entry.decayTime += client.getLastFrameDuration();
                    }

                    return entry.decayTime >= 50;
                });
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

    private static class OverlayEntry {

        public final Identifier structureId;

        public float decayTime = -1;
        public float visualCompleteness = 0f;

        public OverlayEntry(Identifier structureId) {
            this.structureId = structureId;
        }

        public @Nullable StructureInfo fetchStructure() {
            return StructureInfoLoader.get(this.structureId);
        }
    }

}
