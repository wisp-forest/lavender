package io.wispforest.lavender.client;

import io.wispforest.lavender.book.Entry;
import io.wispforest.owo.ops.TextOps;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AssociatedEntryTooltipComponent implements TooltipComponent {

    private final FlowLayout layout;

    public AssociatedEntryTooltipComponent(ItemStack book, Entry entry, float progress) {
        this.layout = Containers.horizontalFlow(Sizing.content(), Sizing.content()).gap(2);

        this.layout.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(Components.item(entry.icon().getDefaultStack()).margins(Insets.of(2)))
                .child(Components.item(book).sizing(Sizing.fixed(8)).positioning(Positioning.absolute(11, 11)).zIndex(50)));

        this.layout.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                .child(Components.label(Text.literal(entry.title()).formatted(Formatting.GRAY)))
                .child(Components.label(TextOps.withFormatting(
                        "Hold §Alt §[§" + "|".repeat((int) (30 * progress)) + "§" + "|".repeat((int) Math.ceil(30 * (1 - progress))) + "§]",
                        Formatting.DARK_GRAY, Formatting.GOLD, Formatting.GREEN, Formatting.GRAY, Formatting.DARK_GRAY, Formatting.GREEN)
                )));
        this.layout.verticalAlignment(VerticalAlignment.CENTER);

        this.layout.inflate(Size.of(1000, 1000));
        this.layout.mount(null, 0, 0);
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, MatrixStack matrices, ItemRenderer itemRenderer) {
        this.layout.moveTo(x, y);
        this.layout.draw(matrices, 0, 0, 0, 0);
    }

    @Override
    public int getHeight() {
        return this.layout.height();
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        return this.layout.width();
    }
}
