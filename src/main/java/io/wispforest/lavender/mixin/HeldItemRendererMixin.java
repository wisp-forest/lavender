package io.wispforest.lavender.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.lavender.client.OffhandBookRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyExpressionValue(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z", ordinal = 0))
    private boolean injectMap(boolean original, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack) {
        if (!(stack.getItem() instanceof LavenderBookItem) || LavenderBookItem.bookOf(stack) == null || hand == Hand.MAIN_HAND || MinecraftClient.getInstance().currentScreen instanceof LavenderBookScreen) {
            return original;
        }

        return true;
    }

    @Inject(method = "renderFirstPersonMap", at = @At("HEAD"), cancellable = true)
    private void injectBook(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int swingProgress, ItemStack stack, CallbackInfo ci) {
        if (!(stack.getItem() instanceof LavenderBookItem)) return;
        ci.cancel();

        OffhandBookRenderer.render(matrices, LavenderBookItem.bookOf(stack));
    }

}
