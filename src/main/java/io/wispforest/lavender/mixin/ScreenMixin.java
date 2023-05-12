package io.wispforest.lavender.mixin;

import io.wispforest.lavender.LavenderScreenExtension;
import io.wispforest.lavender.book.BookItem;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.client.AssociatedEntryTooltipComponent;
import io.wispforest.lavender.client.BookScreen;
import io.wispforest.owo.ui.util.Delta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.ref.WeakReference;
import java.util.List;

@Mixin(Screen.class)
public class ScreenMixin implements LavenderScreenExtension {

    @Shadow
    @Nullable
    protected MinecraftClient client;

    @Unique
    protected @Nullable WeakReference<ItemStack> tooltipStack = null;

    @Unique
    protected float entryTriggerProgress = 0f;

    @Inject(method = "getTooltipFromItem", at = @At("HEAD"))
    private void captureTooltipStack(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        this.lavender$setTooltipContextStack(stack);
    }

    @Inject(method = "renderTooltipFromComponents", at = @At("HEAD"))
    private void injectTooltipComponents(MatrixStack matrices, List<TooltipComponent> components, int x, int y, TooltipPositioner positioner, CallbackInfo ci) {
        if (this.tooltipStack != null && this.tooltipStack.get() != null) {
            var stack = this.tooltipStack.get();
            this.tooltipStack = null;

            for (var book : BookLoader.loadedBooks()) {
                var associatedEntry = book.entryByAssociatedItem(stack.getItem());
                if (associatedEntry == null || !associatedEntry.canPlayerView(this.client.player)) continue;

                components.add(new AssociatedEntryTooltipComponent(BookItem.create(book), associatedEntry, entryTriggerProgress));
                entryTriggerProgress += Delta.compute(entryTriggerProgress, Screen.hasAltDown() ? 1.35f : 0f, MinecraftClient.getInstance().getLastFrameDuration() * .125);

                if (entryTriggerProgress >= .95) {
                    BookScreen.pushEntry(book, associatedEntry);
                    MinecraftClient.getInstance().setScreen(new BookScreen(book));
                    entryTriggerProgress = 0f;
                }

                return;
            }
        }

        entryTriggerProgress += Delta.compute(entryTriggerProgress, 0f, MinecraftClient.getInstance().getLastFrameDuration() * .125);
    }

    @Override
    public void lavender$setTooltipContextStack(ItemStack stack) {
        this.tooltipStack = new WeakReference<>(stack);
    }
}
