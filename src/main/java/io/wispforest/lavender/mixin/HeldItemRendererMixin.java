package io.wispforest.lavender.mixin;

import io.wispforest.lavender.book.BookItem;
import io.wispforest.lavender.client.BookScreen;
import io.wispforest.lavender.client.OffhandBookRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Unique
    private static final ItemStack FILLED_MAP = new ItemStack(Items.FILLED_MAP);

    @Unique
    private ItemStack cachedItem = null;

    @ModifyVariable(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z", ordinal = 0), argsOnly = true)
    private ItemStack injectMap(ItemStack stack, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand) {
        if (!(stack.getItem() instanceof BookItem) || BookItem.bookOf(stack) == null || hand == Hand.MAIN_HAND || MinecraftClient.getInstance().currentScreen instanceof BookScreen) {
            return stack;
        }

        this.cachedItem = stack;
        return FILLED_MAP;
    }

    @ModifyVariable(method = "renderFirstPersonItem", at = @At(value = "JUMP", opcode = Opcodes.IFEQ, ordinal = 4), argsOnly = true)
    private ItemStack restoreMap(ItemStack value) {
        if (this.cachedItem == null) return value;
        var item = this.cachedItem;
        this.cachedItem = null;
        return item;
    }

    @Inject(method = "renderFirstPersonMap", at = @At("HEAD"), cancellable = true)
    private void injectBook(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int swingProgress, ItemStack stack, CallbackInfo ci) {
        if (!(stack.getItem() instanceof BookItem)) return;
        ci.cancel();

        OffhandBookRenderer.render(matrices, BookItem.bookOf(stack));
    }

}
