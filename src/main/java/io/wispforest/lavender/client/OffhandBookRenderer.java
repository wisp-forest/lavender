package io.wispforest.lavender.client;

import com.google.common.base.Suppliers;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Book;
import io.wispforest.owo.ui.event.WindowResizeCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.*;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class OffhandBookRenderer {

	// Seems to render just fine without this pipeline. Also, Iris Shaders doesn't like it.
	// So, I just got rid of the double buffering. I'll keep it here just in case though.
	/*
	private static final RenderPipeline BACK_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder()
			.withLocation(Lavender.id("pipeline/back_buffer"))
			.withVertexShader("core/blit_screen")
			.withFragmentShader(Lavender.id("core/blit_cutout"))
			.withSampler("InSampler")
			.withBlend(BlendFunction.ENTITY_OUTLINE_BLIT)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withColorWrite(true, true)
			.withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.QUADS)
			.build()
	);
	 */

	private static final Supplier<LavenderFramebuffer> SCREEN_BUFFER = Suppliers.memoize(() -> {
		var window = MinecraftClient.getInstance().getWindow();

		return new LavenderFramebuffer("Lavender Book Screen Buffer", window.getFramebufferWidth(), window.getFramebufferHeight(), true);
	});

    private static LavenderBookScreen cachedScreen = null;
    private static boolean cacheExpired = true;

    public static void initialize() {
        WindowResizeCallback.EVENT.register((client, window) -> {
            SCREEN_BUFFER.get().resize(window.getFramebufferWidth(), window.getFramebufferHeight());
            cachedScreen = null;
        });
    }

    public static void beginFrame(@Nullable Book book, MinecraftClient client, GuiRenderState guiState, GuiRenderer guiRenderer, FogRenderer fogRenderer) {
	    cacheExpired = false;

        if (book == null) return;
        var screenBuffer = SCREEN_BUFFER.get();

	    // --- render book screen to separate framebuffer ---

	    var screen = cachedScreen;
	    if (screen == null || screen.book != book) {
	        cachedScreen = screen = new LavenderBookScreen(book, true);
	        screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());

	        // we dispose the ui adapter here to
	        // stop it from messing with and/or
	        // leaking GLFW cursor objects
	        screen.adapter().dispose();
	    }

	    var override = (GuiRendererFramebufferOverride) guiRenderer;

	    LavenderClient.mainTargetOverride = screenBuffer;
	    override.lavender$setOverride(screenBuffer);
	    screenBuffer.clear();

	    screen.render(new DrawContext(client, guiState), -69, -69, 0);
	    guiRenderer.render(fogRenderer.getFogBuffer(FogRenderer.FogType.NONE));

	    guiState.clear();
	    override.lavender$setOverride(null);
	    LavenderClient.mainTargetOverride = null;
    }

    public static void render(MatrixStack matrices, int light) {
        cacheExpired = true;
        var client = MinecraftClient.getInstance();

        // --- draw color attachment in place of map texture ---

        var framebuffer = SCREEN_BUFFER.get();

        var texture = new FramebufferTexture(framebuffer);
        client.getTextureManager().registerTexture(Lavender.id("offhand_book_framebuffer"), texture);

        var rightHanded = client.player.getMainArm() == Arm.RIGHT;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rightHanded ? 15 : -15));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10));

        matrices.scale(1 * (framebuffer.textureWidth / (float) framebuffer.textureHeight), 1f, 1f);
        matrices.translate(rightHanded ? -.4f : -.6f, -.35f, -.165f);

        var buffer = client.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getText(Lavender.id("offhand_book_framebuffer")));
        var matrix = matrices.peek().getPositionMatrix();

        buffer.vertex(matrix, 0, 1, 0).color(1f, 1f, 1f, 1f).texture(0, 1).light(light);
        buffer.vertex(matrix, 0, 0, 0).color(1f, 1f, 1f, 1f).texture(0, 0).light(light);
        buffer.vertex(matrix, 1, 0, 0).color(1f, 1f, 1f, 1f).texture(1, 0).light(light);
        buffer.vertex(matrix, 1, 1, 0).color(1f, 1f, 1f, 1f).texture(1, 1).light(light);

        client.getBufferBuilders().getEntityVertexConsumers().draw();

        matrices.pop();
    }

    public static void endFrame() {
        if (cacheExpired) cachedScreen = null;
    }

    private static class FramebufferTexture extends AbstractTexture {

		public FramebufferTexture(Framebuffer framebuffer) {
			this.glTexture = framebuffer.getColorAttachment();
			this.glTextureView = framebuffer.getColorAttachmentView();
		}

        @Override
        public void close() {}
    }
}
