package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.EntryLoader;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.extensions.BlockStateExtension;
import io.wispforest.lavender.md.extensions.ItemStackExtension;
import io.wispforest.lavender.md.extensions.PageBreakExtension;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class BookScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    private static final MarkdownProcessor<ParentComponent> PROCESSOR = new MarkdownProcessor<>(BookCompiler::new, new BlockStateExtension(), new ItemStackExtension(), new PageBreakExtension());

    private int previousUiScale;

    public BookScreen() {
        super(FlowLayout.class, Lavender.id("book"));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.previousUiScale = this.client.options.getGuiScale().getValue();
        this.client.options.getGuiScale().setValue(MathHelper.ceilDiv(this.client.options.getGuiScale().getValue(), 2) * 2);
        this.client.onResolutionChanged();

        var entry = EntryLoader.getEntry(Lavender.id("page"));
        if (entry != null) {
            var pages = PROCESSOR.process(entry.content());

            int pageCount = 0;
            while (pages.children().size() > 0 && pageCount < 2) {
                var page = pages.children().get(0);
                pages.removeChild(page);

                rootComponent.childById(FlowLayout.class, "page-" + (pageCount + 1) + "-content").child(page);
                pageCount++;
            }

            if (entry.meta() != null && entry.meta().has("title")) {
                rootComponent.childById(LabelComponent.class, "title-label").text(Text.literal(entry.meta().get("title").getAsString()));
            }
        }
    }

    @Override
    public void close() {
        super.close();

        this.client.options.getGuiScale().setValue(this.previousUiScale);
        this.client.onResolutionChanged();
    }
}
