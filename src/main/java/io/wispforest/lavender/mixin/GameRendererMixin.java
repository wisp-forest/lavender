package io.wispforest.lavender.mixin;

import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.lavender.client.OffhandBookRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	@Final
	private GuiRenderState guiState;

	@Shadow
	@Final
	private GuiRenderer guiRenderer;

	@Shadow
	@Final
	private FogRenderer fogRenderer;

	@Inject(method = "render", at = @At(value = "NEW", target = "net/minecraft/client/gui/DrawContext"))
	private void onFrameStart(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
		var client = this.client;
		if (client.player == null) return;

		var offhandStack = client.player.getOffHandStack();
		if (offhandStack.getItem() instanceof LavenderBookItem && LavenderBookItem.bookOf(offhandStack) instanceof Book book && !(client.currentScreen instanceof LavenderBookScreen)) {
			OffhandBookRenderer.beginFrame(book, client, guiState, guiRenderer, fogRenderer);
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void onFrameEnd(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
		OffhandBookRenderer.endFrame();
	}
}
