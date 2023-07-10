package io.wispforest.lavender.mixin;

import io.wispforest.lavender.book.LavenderBookItem;
import io.wispforest.lavender.book.BookLoader;
import io.wispforest.lavender.client.AssociatedEntryTooltipComponent;
import io.wispforest.lavender.client.LavenderBookScreen;
import io.wispforest.owo.ui.util.Delta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DrawContext.class)
public class DrawContextMixin {

    @Unique
    private static float entryTriggerProgress = 0f;

    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V", at = @At("HEAD"))
    private void injectTooltipComponents(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y, TooltipPositioner positioner, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();

        if (AssociatedEntryTooltipComponent.tooltipStack != null && AssociatedEntryTooltipComponent.tooltipStack.get() != null) {
            var stack = AssociatedEntryTooltipComponent.tooltipStack.get();
            AssociatedEntryTooltipComponent.tooltipStack = null;

            for (var book : BookLoader.loadedBooks()) {
                var associatedEntry = book.entryByAssociatedItem(stack.getItem());
                if (associatedEntry == null || !associatedEntry.canPlayerView(client.player)) continue;

                int bookIndex = -1;
                for (int i = 0; i < 9; i++) {
                    if (LavenderBookItem.bookOf(client.player.getInventory().getStack(i)) == book) {
                        bookIndex = i;
                        break;
                    }
                }

                if (LavenderBookItem.bookOf(client.player.getOffHandStack()) == book) {
                    bookIndex = -69;
                }

                if (bookIndex == -1) return;

                components.add(new AssociatedEntryTooltipComponent(LavenderBookItem.itemOf(book), associatedEntry, entryTriggerProgress));
                entryTriggerProgress += Delta.compute(entryTriggerProgress, Screen.hasAltDown() ? 1.35f : 0f, client.getLastFrameDuration() * .125);

                if (entryTriggerProgress >= .95) {
                    LavenderBookScreen.pushEntry(book, associatedEntry);
                    client.setScreen(new LavenderBookScreen(book));

                    if (bookIndex >= 0) {
                        client.player.getInventory().selectedSlot = bookIndex;
                    }

                    entryTriggerProgress = 0f;
                }

                return;
            }
        }

        entryTriggerProgress += Delta.compute(entryTriggerProgress, 0f, client.getLastFrameDuration() * .125);
    }
}
