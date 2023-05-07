package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.EntryLoader;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import net.minecraft.text.Text;

public class BookScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    public BookScreen() {
        super(FlowLayout.class, Lavender.id("book"));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        var page1 = EntryLoader.getEntry(Lavender.id("page-1"));
        var page2 = EntryLoader.getEntry(Lavender.id("page-2"));

        if (page1 != null) {
            rootComponent.childById(FlowLayout.class, "page-1-content").child(MarkdownProcessor.OWO_UI.process(page1.content()));
            if (page1.meta() != null) this.client.player.sendMessage(Text.literal(page1.meta().toString()));
        }

        if (page2 != null) {
            rootComponent.childById(FlowLayout.class, "page-2-content").child(MarkdownProcessor.OWO_UI.process(page2.content()));
            if (page2.meta() != null) this.client.player.sendMessage(Text.literal(page2.meta().toString()));
        }
    }
}
