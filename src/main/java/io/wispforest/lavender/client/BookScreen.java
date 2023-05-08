package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.EntryLoader;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.extensions.*;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import io.wispforest.owo.util.Observable;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BookScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    private static final MarkdownProcessor<ParentComponent> PROCESSOR = new MarkdownProcessor<>(
            BookCompiler::new,
            new BlockStateExtension(), new ItemStackExtension(), new EntityExtension(),
            new PageBreakExtension(), new OwoUITemplateExtension(), new RecipeExtension()
    );

    private int previousUiScale;
    private String entryTitle;

    private ButtonComponent previousButton;
    private ButtonComponent nextButton;

    private FlowLayout leftPageAnchor;
    private FlowLayout rightPageAnchor;

    private final Observable<Integer> basePageIndex = Observable.of(-1);
    private final List<Component> pages = new ArrayList<>();

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
            while (!pages.children().isEmpty()) {
                var component = pages.children().get(0);

                pages.removeChild(component);
                this.pages.add(component);
            }

            if (entry.meta() != null && entry.meta().has("title")) {
                this.entryTitle = entry.meta().get("title").getAsString();
            }
        }

        this.leftPageAnchor = rootComponent.childById(FlowLayout.class, "left-page-anchor");
        this.rightPageAnchor = rootComponent.childById(FlowLayout.class, "right-page-anchor");

        (this.previousButton = rootComponent.childById(ButtonComponent.class, "previous-button")).onPress(buttonComponent -> {
            this.basePageIndex.set(Math.max(0, this.basePageIndex.get() - 2));
        });

        (this.nextButton = rootComponent.childById(ButtonComponent.class, "next-button")).onPress(buttonComponent -> {
            this.basePageIndex.set(Math.min(this.pages.size() - 1, this.basePageIndex.get() + 2));
        });

        this.basePageIndex.observe(this::updateForPageChange);
        this.basePageIndex.set(0);
    }

    private void updateForPageChange(int basePageIndex) {
        this.previousButton.active(basePageIndex > 0);
        this.nextButton.active(basePageIndex + 2 < this.pages.size());


        int index = 0;
        while (index < 2) {
            var anchor = index == 0 ? this.leftPageAnchor : this.rightPageAnchor;
            anchor.clearChildren();

            if (basePageIndex + index < this.pages.size()) {
                var pageContent = this.pages.get(basePageIndex + index);

                var page = this.model.expandTemplate(ParentComponent.class, basePageIndex + index == 0 ? "page-with-title" : "simple-page", Map.of("page-title", this.entryTitle));
                page.childById(FlowLayout.class, "content-anchor").child(pageContent);

                anchor.child(page);
            } else {
                anchor.child(this.model.expandTemplate(Component.class, "empty-page", Map.of()));
            }

            index++;
        }
    }

    @Override
    public void close() {
        super.close();

        this.client.options.getGuiScale().setValue(this.previousUiScale);
        this.client.onResolutionChanged();
    }
}
