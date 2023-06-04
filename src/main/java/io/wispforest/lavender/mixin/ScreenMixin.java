package io.wispforest.lavender.mixin;

import io.wispforest.lavender.client.AssociatedEntryTooltipComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.WeakReference;
import java.util.List;

@Mixin(Screen.class)
public class ScreenMixin {

    @Shadow
    @Nullable
    protected MinecraftClient client;

    @Inject(method = "getTooltipFromItem", at = @At("HEAD"))
    private static void captureTooltipStack(MinecraftClient client, ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        AssociatedEntryTooltipComponent.tooltipStack = new WeakReference<>(stack);
    }
}
