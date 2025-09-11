package io.wispforest.lavender.book;

import io.wispforest.lavender.structure.StructureTemplate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

public record StructureComponentRenderState(
		StructureTemplate structure,
		int displayAngle,
		float rotation,
		int visibleLayer,
		ScreenRect bounds,
		ScreenRect scissorArea
) implements SpecialGuiElementRenderState {
	@Override
	public int x1() {
		return this.bounds.getLeft();
	}

	@Override
	public int x2() {
		return this.bounds.getRight();
	}

	@Override
	public int y1() {
		return this.bounds.getTop();
	}

	@Override
	public int y2() {
		return this.bounds.getBottom();
	}

	@Override
	public float scale() {
		return 1;
	}

	@Override
	public @Nullable ScreenRect scissorArea() {
		return this.scissorArea;
	}

	@Override
	public @Nullable ScreenRect bounds() {
		return this.scissorArea != null ? this.scissorArea.intersection(this.bounds) : this.bounds;
	}

	public static class Renderer extends SpecialGuiElementRenderer<StructureComponentRenderState> {

		public Renderer(VertexConsumerProvider.Immediate vertexConsumers) {
			super(vertexConsumers);
		}

		@Override
		public Class<StructureComponentRenderState> getElementClass() {
			return StructureComponentRenderState.class;
		}

		@Override
		protected void render(StructureComponentRenderState state, MatrixStack matrices) {
			MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ITEMS_3D);

			var width = state.bounds.width();
			var height = state.bounds.height();

			var structure = state.structure;
			float scale = Math.min(width, height);
			scale /= Math.max(structure.xSize, Math.max(structure.ySize, structure.zSize));
			scale /= 1.625f;

			matrices.translate(0, -height / 2f, 100);
			matrices.scale(scale, -scale, scale);

			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.displayAngle));
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.rotation));

			matrices.translate(structure.xSize / -2f, structure.ySize / -2f, structure.zSize / -2f);

			var client = MinecraftClient.getInstance();

			structure.forEachPredicate((blockPos, predicate) -> {
				if (state.visibleLayer != -1 && state.visibleLayer != blockPos.getY()) return;

				matrices.push();
				matrices.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

				client.getBlockRenderManager().renderBlockAsEntity(
						predicate.preview(), matrices, vertexConsumers,
						LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE,
						OverlayTexture.DEFAULT_UV
				);

				matrices.pop();
			});
		}

		@Override
		protected String getName() {
			return "lavender_structure";
		}
	}
}
