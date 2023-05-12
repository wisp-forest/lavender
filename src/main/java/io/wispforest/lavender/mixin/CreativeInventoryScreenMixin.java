package io.wispforest.lavender.mixin;

import io.wispforest.lavender.LavenderScreenExtension;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeInventoryScreen.class)
public class CreativeInventoryScreenMixin {
    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void captureTooltipStack(MatrixStack matrices, ItemStack stack, int x, int y, CallbackInfo ci) {
        ((LavenderScreenExtension) this).lavender$setTooltipContextStack(stack);
    }
}
