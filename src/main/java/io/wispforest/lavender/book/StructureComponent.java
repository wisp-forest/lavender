package io.wispforest.lavender.book;

import io.wispforest.lavender.client.StructureOverlayRenderer;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.lavender.structure.StructureInfo;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.parsing.UIModelParsingException;
import io.wispforest.owo.ui.parsing.UIParsing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.w3c.dom.Element;

public class StructureComponent extends BaseComponent {

    private final StructureInfo structure;
    private int visibleLayer = -1;

    public StructureComponent(StructureInfo structure) {
        this.structure = structure;
        this.cursorStyle(CursorStyle.HAND);
    }

    @Override
    public void draw(MatrixStack matrices, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = MinecraftClient.getInstance();
        var entityBuffers = client.getBufferBuilders().getEntityVertexConsumers();

        float scale = Math.min(this.width, this.height);
        scale /= Math.max(structure.xSize, Math.max(structure.ySize, structure.zSize));
        scale /= 1.625f;

        matrices.push();
        matrices.translate(this.x + this.width / 2f, this.y + this.height / 2f, 100);
        matrices.scale(scale, -scale, scale);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(35f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (System.currentTimeMillis() / 75d % 360d)));
        matrices.translate(this.structure.xSize / -2f, this.structure.ySize / -2f, this.structure.zSize / -2f);

        structure.forEachPredicate((blockPos, predicate) -> {
            if (this.visibleLayer != -1 && this.visibleLayer != blockPos.getY()) return;

            matrices.push();
            matrices.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

            client.getBlockRenderManager().renderBlockAsEntity(
                    predicate.preview(), matrices, entityBuffers,
                    LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV
            );

            matrices.pop();
        });

        matrices.pop();

        DiffuseLighting.disableGuiDepthLighting();
        entityBuffers.draw();
        DiffuseLighting.enableGuiDepthLighting();

        if (StructureOverlayRenderer.isShowingOverlay(this.structure.id)) {
            client.textRenderer.draw(matrices, Text.translatable("text.lavender.structure_component.active_overlay_hint"), this.x + this.width - 5 - client.textRenderer.getWidth("⚓"), this.y + this.height - 9 - 5, 0);
            this.tooltip(Text.translatable("text.lavender.structure_component.hide_hint"));
        } else {
            this.tooltip(Text.translatable("text.lavender.structure_component.place_hint"));
        }
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        var result = super.onMouseDown(mouseX, mouseY, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return result;

        if (StructureOverlayRenderer.isShowingOverlay(this.structure.id)) {
            StructureOverlayRenderer.removeAllOverlays(this.structure.id);
        } else {
            StructureOverlayRenderer.addPendingOverlay(this.structure.id);
            StructureOverlayRenderer.restrictVisibleLayer(this.structure.id, this.visibleLayer);

            MinecraftClient.getInstance().setScreen(null);
        }

        return true;
    }

    public StructureComponent visibleLayer(int visibleLayer) {
        StructureOverlayRenderer.restrictVisibleLayer(this.structure.id, visibleLayer);

        this.visibleLayer = visibleLayer;
        return this;
    }

    public static StructureComponent parse(Element element) {
        UIParsing.expectAttributes(element, "structure-id");

        var structureId = Identifier.tryParse(element.getAttribute("structure-id"));
        if (structureId == null) {
            throw new UIModelParsingException("Invalid structure id '" + element.getAttribute("structure-id") + "'");
        }

        var structure = LavenderStructures.get(structureId);
        if (structure == null) throw new UIModelParsingException("Unknown structure '" + structureId + "'");

        return new StructureComponent(structure);
    }
}
