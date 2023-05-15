package io.wispforest.lavender.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.structure.BlockStatePredicate;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.lavender.structure.StructureInfo;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.event.WindowResizeCallback;
import io.wispforest.owo.ui.hud.Hud;
import io.wispforest.owo.ui.util.Delta;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
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

    private static @Nullable OverlayEntry PENDING_OVERLAY = null;

    private static final Identifier HUD_COMPONENT_ID = Lavender.id("structure_overlay");
    private static final Identifier BARS_TEXTURE = new Identifier("textures/gui/bars.png");

    public static void addPendingOverlay(Identifier structure) {
        PENDING_OVERLAY = new OverlayEntry(structure, BlockRotation.NONE);
    }

    public static void addOverlay(BlockPos anchorPoint, Identifier structure, BlockRotation rotation) {
        ACTIVE_OVERLAYS.put(anchorPoint, new OverlayEntry(structure, rotation));
    }

    public static boolean isShowingOverlay(Identifier structure) {
        if (PENDING_OVERLAY != null && structure.equals(PENDING_OVERLAY.structureId)) return true;

        for (var entry : ACTIVE_OVERLAYS.values()) {
            if (structure.equals(entry.structureId)) return true;
        }

        return false;
    }

    public static void removeAllOverlays(Identifier structure) {
        if (PENDING_OVERLAY != null && structure.equals(PENDING_OVERLAY.structureId)) {
            addPendingOverlay(null);
        }

        ACTIVE_OVERLAYS.values().removeIf(entry -> structure.equals(entry.structureId));
    }

    public static int getLayerRestriction(Identifier structure) {
        for (var entry : ACTIVE_OVERLAYS.values()) {
            if (entry.visibleLayer == -1) continue;
            return entry.visibleLayer;
        }

        return -1;
    }

    public static void restrictVisibleLayer(Identifier structure, int visibleLayer) {
        if (PENDING_OVERLAY != null && structure.equals(PENDING_OVERLAY.structureId)) {
            PENDING_OVERLAY.visibleLayer = visibleLayer;
        }

        for (var entry : ACTIVE_OVERLAYS.values()) {
            if (!structure.equals(entry.structureId)) continue;
            entry.visibleLayer = visibleLayer;
        }
    }

    public static void clearOverlays() {
        ACTIVE_OVERLAYS.clear();
    }

    public static void rotatePending(boolean clockwise) {
        if (PENDING_OVERLAY == null) return;
        PENDING_OVERLAY.rotation = PENDING_OVERLAY.rotation.rotate(clockwise ? BlockRotation.CLOCKWISE_90 : BlockRotation.COUNTERCLOCKWISE_90);
    }

    public static boolean hasPending() {
        return PENDING_OVERLAY != null;
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
                            var state = context.world().getBlockState(testPos.set(anchor).move(pos)).rotate(StructureInfo.inverse(entry.rotation));
                            if (predicate.test(state)) {
                                return;
                            } else if (!state.isAir()) {
                                hasInvalidBlock.setTrue();
                            }

                            if (entry.visibleLayer != -1 && pos.getY() != entry.visibleLayer) return;
                            renderOverlayBlock(matrices, context.consumers(), pos, predicate, entry.rotation);
                        }, entry.rotation);

                        matrices.pop();
                    }

                    // --- hud setup ---

                    var valid = structure.countValidStates(client.world, anchor, entry.rotation);
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
                        client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
                    } else if (entry.decayTime >= 0) {
                        entry.decayTime += client.getLastFrameDuration();
                    }

                    return entry.decayTime >= 50;
                });
            });

            if (PENDING_OVERLAY != null) {
                var structure = PENDING_OVERLAY.fetchStructure();
                if (structure != null) {
                    if (client.player.raycast(5, client.getTickDelta(), false) instanceof BlockHitResult target) {
                        var targetPos = target.getBlockPos().add(getPendingOffset(structure));
                        if (!client.player.isSneaking()) targetPos = targetPos.offset(target.getSide());

                        matrices.translate(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                        structure.forEachPredicate((pos, predicate) -> renderOverlayBlock(matrices, context.consumers(), pos, predicate, PENDING_OVERLAY.rotation), PENDING_OVERLAY.rotation);
                    }
                } else {
                    PENDING_OVERLAY = null;
                }
            }

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

            client.gameRenderer.blitScreenProgram.colorModulator.set(new float[]{1, 1, 1, .5f});
            framebuffer.draw(framebuffer.textureWidth, framebuffer.textureHeight, false);
            client.gameRenderer.blitScreenProgram.colorModulator.set(new float[]{1, 1, 1, 1});
        });

        WindowResizeCallback.EVENT.register((client, window) -> {
            FRAMEBUFFER.get().resize(window.getFramebufferWidth(), window.getFramebufferHeight(), MinecraftClient.IS_SYSTEM_MAC);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (PENDING_OVERLAY == null) return ActionResult.PASS;

            var structure = PENDING_OVERLAY.fetchStructure();

            var targetPos = hitResult.getBlockPos().add(getPendingOffset(structure));
            if (!player.isSneaking()) targetPos = targetPos.offset(hitResult.getSide());

            ACTIVE_OVERLAYS.put(targetPos, PENDING_OVERLAY);
            PENDING_OVERLAY = null;

            player.swingHand(hand);
            return ActionResult.FAIL;
        });
    }

    private static Vec3i getPendingOffset(StructureInfo structure) {
        if (PENDING_OVERLAY == null) return Vec3i.ZERO;
        return PENDING_OVERLAY.rotation == BlockRotation.NONE || PENDING_OVERLAY.rotation == BlockRotation.CLOCKWISE_180
                ? new Vec3i(-structure.xSize / 2, 0, -structure.zSize / 2)
                : new Vec3i(-structure.zSize / 2, 0, -structure.xSize / 2);
    }

    private static void renderOverlayBlock(MatrixStack matrices, VertexConsumerProvider consumers, BlockPos offsetInStructure, BlockStatePredicate block, BlockRotation rotation) {
        matrices.push();
        matrices.translate(offsetInStructure.getX(), offsetInStructure.getY(), offsetInStructure.getZ());

        matrices.translate(.5, .5, .5);
        matrices.scale(1.0001f, 1.0001f, 1.0001f);
        matrices.translate(-.5, -.5, -.5);

        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(
                block.preview().rotate(rotation),
                matrices,
                consumers,
                LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV
        );
        matrices.pop();
    }

    private static class OverlayEntry {

        public final Identifier structureId;

        public BlockRotation rotation;
        public int visibleLayer = -1;

        public float decayTime = -1;
        public float visualCompleteness = 0f;

        public OverlayEntry(Identifier structureId, BlockRotation rotation) {
            this.structureId = structureId;
            this.rotation = rotation;
        }

        public @Nullable StructureInfo fetchStructure() {
            return LavenderStructures.get(this.structureId);
        }
    }

}
