package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Entry;
import io.wispforest.lavender.book.EntryLoader;
import io.wispforest.lavender.book.StructureComponent;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.extensions.*;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import io.wispforest.owo.ui.util.Drawer;
import io.wispforest.owo.ui.util.UISounds;
import io.wispforest.owo.util.Observable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class BookScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    private static final MarkdownProcessor<ParentComponent> PROCESSOR = new MarkdownProcessor<>(
            BookCompiler::new,
            new BlockStateExtension(), new ItemStackExtension(), new EntityExtension(),
            new PageBreakExtension(), new OwoUITemplateExtension(), new RecipeExtension()
    );

    private int previousUiScale;

    private ButtonComponent previousButton;
    private ButtonComponent returnButton;
    private ButtonComponent nextButton;

    private FlowLayout leftPageAnchor;
    private FlowLayout rightPageAnchor;

    private final OpenObservable<Integer> basePageIndex = new OpenObservable<>(-1);
    private final Deque<NavFrame> navStack = new ArrayDeque<>();

    public BookScreen() {
        super(FlowLayout.class, Lavender.id("book"));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.previousUiScale = this.client.options.getGuiScale().getValue();
        this.client.options.getGuiScale().setValue(MathHelper.ceilDiv(this.client.options.getGuiScale().getValue(), 2) * 2);
        this.client.onResolutionChanged();

        this.leftPageAnchor = rootComponent.childById(FlowLayout.class, "left-page-anchor");
        this.rightPageAnchor = rootComponent.childById(FlowLayout.class, "right-page-anchor");

        (this.previousButton = rootComponent.childById(ButtonComponent.class, "previous-button")).onPress(buttonComponent -> {
            this.turnPage(true);
        });

        (this.returnButton = rootComponent.childById(ButtonComponent.class, "back-button")).onPress(buttonComponent -> {
            this.navPop();
        });

        (this.nextButton = rootComponent.childById(ButtonComponent.class, "next-button")).onPress(buttonComponent -> {
            this.turnPage(false);
        });

        this.basePageIndex.observe(this::updateForPageChange);
        this.navPush(new IndexPageSupplier());
    }

    private void turnPage(boolean left) {
        this.basePageIndex.set(Math.max(0, Math.min(this.basePageIndex.get() + (left ? -2 : 2), this.pageSupplier().pageCount() - 1)));
    }

    private void updateForPageChange(int basePageIndex) {
        var pageSupplier = this.pageSupplier();

        this.returnButton.active(this.navStack.size() > 1);
        this.previousButton.active(basePageIndex > 0);
        this.nextButton.active(basePageIndex + 2 < pageSupplier.pageCount());

        int index = 0;
        while (index < 2) {
            var anchor = index == 0 ? this.leftPageAnchor : this.rightPageAnchor;
            anchor.clearChildren();

            if (basePageIndex + index < pageSupplier.pageCount()) {
                anchor.child(pageSupplier.getPage(basePageIndex + index));
            } else {
                anchor.child(this.model.expandTemplate(Component.class, "empty-page", Map.of()));
            }

            index++;
        }
    }

    protected PageSupplier pageSupplier() {
        return this.navStack.peek().pageSupplier;
    }

    protected void navPush(PageSupplier supplier) {
        if (this.navStack.peek() != null) {
            this.navStack.peek().selectedPage = this.basePageIndex.get();
        }

        this.navStack.push(new NavFrame(supplier));
        this.basePageIndex.update(0);
    }

    protected void navPop() {
        if (this.navStack.size() <= 1) return;

        this.navStack.pop();
        this.basePageIndex.update(this.navStack.peek().selectedPage);
    }

    protected ParentComponent makePageContainer(@Nullable String title, Component content) {
        var page = this.model.expandTemplate(ParentComponent.class, title != null ? "page-with-title" : "simple-page", title == null ? Map.of() : Map.of("page-title", title));
        page.childById(FlowLayout.class, "content-anchor").child(content);
        return page;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            this.navPop();
        } else if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.turnPage(true);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            this.turnPage(false);
        } else {
            return false;
        }

        return true;
    }

    @Override
    public void close() {
        super.close();

        this.client.options.getGuiScale().setValue(this.previousUiScale);
        this.client.onResolutionChanged();
    }

    public interface PageSupplier {
        Component getPage(int pageIndex);

        int pageCount();
    }

    public class IndexPageSupplier implements PageSupplier {

        @Override
        public Component getPage(int pageIndex) {
            var component = BookScreen.this.model.expandTemplate(ParentComponent.class, "index-page", Map.of());
            component.childById(FlowLayout.class, "index").<FlowLayout>configure(index -> {
                EntryLoader.loadedEntries().forEach(entryId -> {
                    var indexItem = BookScreen.this.model.expandTemplate(ParentComponent.class, "index-item", Map.of());
                    indexItem.childById(ItemComponent.class, "icon").stack(EntryLoader.getEntry(entryId).icon().getDefaultStack());

                    var label = indexItem.childById(LabelComponent.class, "index-label");

                    label.text(Text.translatable(Util.createTranslationKey("entry", entryId)).styled($ -> $.withFont(MinecraftClient.UNICODE_FONT_ID)));
                    label.mouseDown().subscribe((mouseX, mouseY, button) -> {
                        BookScreen.this.navPush(new EntryPageSupplier(EntryLoader.getEntry(entryId)));
                        UISounds.playInteractionSound();
                        return true;
                    });

                    var animation = label.color().animate(150, Easing.SINE, Color.ofFormatting(Formatting.GOLD));
                    label.mouseEnter().subscribe(animation::forwards);
                    label.mouseLeave().subscribe(animation::backwards);

                    index.child(indexItem);
                });
            });

            return BookScreen.this.makePageContainer("Index", component);
        }

        @Override
        public int pageCount() {
            return 1;
        }
    }

    public class EntryPageSupplier implements PageSupplier {

        private final Entry entry;
        private final List<Component> pages = new ArrayList<>();

        public EntryPageSupplier(Entry entry) {
            this.entry = entry;

            var pages = PROCESSOR.process(entry.content());
            boolean firstPage = true;

            while (!pages.children().isEmpty()) {
                var component = pages.children().get(0);
                pages.removeChild(component);

                if (component instanceof ParentComponent parent) {
                    parent.forEachDescendant(descendant -> {
                        if (!(descendant instanceof LabelComponent label)) return;
                        label.textClickHandler(style -> {
                            var clickEvent = style.getClickEvent();
                            if (clickEvent != null && clickEvent.getAction() == ClickEvent.Action.OPEN_URL && clickEvent.getValue().startsWith("^")) {
                                BookScreen.this.navPush(new EntryPageSupplier(EntryLoader.getEntry(new Identifier(clickEvent.getValue().substring(1)))));
                                return true;
                            } else {
                                return Drawer.utilityScreen().handleTextClick(style);
                            }
                        });
                    });
                }

                if (firstPage) {
                    firstPage = false;
                    this.pages.add(BookScreen.this.makePageContainer(entry.title().getString(), component));
                } else {
                    this.pages.add(BookScreen.this.makePageContainer(null, component));
                }
            }
        }

        @Override
        public Component getPage(int pageIndex) {
            return this.pages.get(pageIndex);
        }

        @Override
        public int pageCount() {
            return this.pages.size();
        }
    }

    private static class OpenObservable<T> extends Observable<T> {
        public OpenObservable(T initial) {
            super(initial);
        }

        public void update(T value) {
            this.value = value;
            this.notifyObservers(this.value);
        }
    }

    private static class NavFrame {
        public final PageSupplier pageSupplier;
        public int selectedPage = 0;

        private NavFrame(PageSupplier pageSupplier) {
            this.pageSupplier = pageSupplier;
        }
    }

    static {
        UIParsing.registerFactory("lavender.structure", StructureComponent::parse);
    }
}
