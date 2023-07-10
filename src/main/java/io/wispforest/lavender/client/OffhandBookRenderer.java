package io.wispforest.lavender.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.book.Book;
import io.wispforest.owo.ui.event.WindowResizeCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

import java.util.function.Supplier;

public class OffhandBookRenderer {

    private static final Supplier<Framebuffer> FRAMEBUFFER = Suppliers.memoize(() -> {
        var window = MinecraftClient.getInstance().getWindow();

        var framebuffer = new SimpleFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight(), true, MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.setClearColor(0f, 0f, 0f, 0f);
        return framebuffer;
    });

    private static LavenderBookScreen cachedScreen = null;
    private static boolean cacheExpired = true;

    public static void initialize() {
        WindowResizeCallback.EVENT.register((client, window) -> {
            FRAMEBUFFER.get().resize(window.getFramebufferWidth(), window.getFramebufferHeight(), MinecraftClient.IS_SYSTEM_MAC);
            cachedScreen = null;
        });
    }

    public static void beginFrame() {
        cacheExpired = true;
    }

    public static void render(MatrixStack matrices, Book book) {
        final var client = MinecraftClient.getInstance();
        client.getBufferBuilders().getEntityVertexConsumers().draw();

        cacheExpired = false;

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

        var modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.loadIdentity();
        modelView.translate(0, 0, -2000);
        RenderSystem.applyModelViewMatrix();

        var framebuffer = FRAMEBUFFER.get();
        framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.beginWrite(false);

        screen.render(new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers()), -69, -69, 0);
        RenderSystem.disableDepthTest();

        client.getFramebuffer().beginWrite(false);

        modelView.pop();
        RenderSystem.applyModelViewMatrix();

        // --- draw color attachment in place of map texture ---

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(client.player.getMainArm() == Arm.RIGHT ? 15 : -15));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10));

        matrices.scale(1.3f * (framebuffer.textureWidth / (float) framebuffer.textureHeight), 1.35f, 1.35f);
        matrices.translate(-.5f, -.4f, -.5f);

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, framebuffer.getColorAttachment());

        var matrix = matrices.peek().getPositionMatrix();
        var buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(matrix, 0, 1, 0).texture(0, 1).next();
        buffer.vertex(matrix, 0, 0, 0).texture(0, 0).next();
        buffer.vertex(matrix, 1, 0, 0).texture(1, 0).next();
        buffer.vertex(matrix, 1, 1, 0).texture(1, 1).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        matrices.pop();
    }

    public static void endFrame() {
        if (cacheExpired) cachedScreen = null;
    }
}
