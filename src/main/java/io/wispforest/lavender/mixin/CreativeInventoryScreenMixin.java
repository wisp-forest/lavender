package io.wispforest.lavender.mixin;

import io.wispforest.lavender.client.AssociatedEntryTooltipComponent;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.WeakReference;
import java.util.List;

@Mixin(CreativeInventoryScreen.class)
public class CreativeInventoryScreenMixin {

    @Inject(method = "getTooltipFromItem", at = @At("HEAD"))
    private void captureTooltipStack(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        AssociatedEntryTooltipComponent.tooltipStack = new WeakReference<>(stack);
    }

}
