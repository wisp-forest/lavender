package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.Entry;
import io.wispforest.lavender.book.StructureComponent;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.extensions.*;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import io.wispforest.owo.ui.util.Drawer;
import io.wispforest.owo.ui.util.UISounds;
import io.wispforest.owo.util.Observable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;

public class BookScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    private static final Map<Identifier, List<NavFrame.Replicator>> NAV_TRAILS = new HashMap<>();

    private static final MarkdownProcessor<ParentComponent> PROCESSOR = new MarkdownProcessor<>(
            BookCompiler::new,
            new BlockStateExtension(), new ItemStackExtension(), new EntityExtension(),
            new PageBreakExtension(), new OwoUITemplateExtension(), new RecipeExtension()
    );

    public final Book book;
    private int previousUiScale;

    private ButtonComponent previousButton;
    private ButtonComponent returnButton;
    private ButtonComponent nextButton;

    private FlowLayout leftPageAnchor;
    private FlowLayout rightPageAnchor;

    private final OpenObservable<Integer> basePageIndex = new OpenObservable<>(-1);
    private final Deque<NavFrame> navStack = new ArrayDeque<>();

    public BookScreen(Book book) {
        super(FlowLayout.class, Lavender.id("book"));
        this.book = book;
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

        var navTrail = NAV_TRAILS.get(this.book.id());
        if (navTrail != null) {
            for (int i = navTrail.size() - 1; i > 0; i--) {
                this.navStack.push(navTrail.get(i).createFrame(this));
            }

            this.navStack.push(navTrail.get(0).createFrame(this));
            this.basePageIndex.update(navTrail.get(0).selectedPage);
        } else {
            this.navPush(new IndexPageSupplier());
        }
    }

    private void updateForPageChange(int basePageIndex) {
        this.navStack.peek().selectedPage = basePageIndex;
        var pageSupplier = this.pageSupplier();

        this.returnButton.active(this.navStack.size() > 1);
        this.previousButton.active(basePageIndex > 0);
        this.nextButton.active(basePageIndex + 2 < pageSupplier.pageCount());

        int index = 0;
        while (index < 2) {
            var anchor = index == 0 ? this.leftPageAnchor : this.rightPageAnchor;
            anchor.clearChildren();

            if (basePageIndex + index < pageSupplier.pageCount()) {
                anchor.child(pageSupplier.getPageContent(basePageIndex + index));
            } else {
                anchor.child(this.model.expandTemplate(Component.class, "empty-page-content", Map.of()));
            }

            index++;
        }
    }

    private void turnPage(boolean left) {
        this.basePageIndex.set(Math.max(0, Math.min(this.basePageIndex.get() + (left ? -2 : 2), this.pageSupplier().pageCount() - 1)));
        this.client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    public void navPush(PageSupplier supplier) {
        this.navPush(new NavFrame(supplier));
    }

    public void navPush(NavFrame frame) {
        if (this.navStack.peek() != null) {
            this.navStack.peek().selectedPage = this.basePageIndex.get();
        }

        this.navStack.push(frame);
        this.basePageIndex.update(frame.selectedPage);

        this.client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    public void navPop() {
        if (this.navStack.size() <= 1) return;

        this.navStack.pop();
        this.basePageIndex.update(this.navStack.peek().selectedPage);

        this.client.player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    protected FlowLayout makePageContentWithHeader(@NotNull String title) {
        var container = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        container.child(this.model.expandTemplate(Component.class, "page-title-header", Map.of("page-title", title)));

        return container;
    }

    protected PageSupplier pageSupplier() {
        return this.navStack.peek().pageSupplier;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.navPop();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            this.turnPage(true);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            this.turnPage(false);
        } else {
            return false;
        }

        return true;
    }

    @Override
    public void removed() {
        super.removed();

        this.client.options.getGuiScale().setValue(this.previousUiScale);
        this.client.onResolutionChanged();

        var trail = new ArrayList<NavFrame.Replicator>();
        while (!this.navStack.isEmpty()) trail.add(this.navStack.pop().replicator());

        NAV_TRAILS.put(this.book.id(), trail);
    }

    public interface PageSupplier {
        Component getPageContent(int pageIndex);

        int pageCount();

        Function<BookScreen, PageSupplier> replicator();
    }

    public class IndexPageSupplier implements PageSupplier {

        private final List<Component> pages = new ArrayList<>();

        public IndexPageSupplier() {
            var landingPageEntry = book.landingPage();

            if (landingPageEntry != null) {
                var landingPage = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
                landingPage.child(model.expandTemplate(Component.class, "landing-page-header", Map.of("page-title", landingPageEntry.title())));
                landingPage.child(PROCESSOR.process(landingPageEntry.content()));

                this.pages.add(landingPage);
            } else {
                this.pages.add(model.expandTemplate(Component.class, "empty-page-content", Map.of()));
            }

            var index = makePageContentWithHeader("Index");
            book.entries().forEach(entry -> {
                if (entry == landingPageEntry) return;

                var indexItem = model.expandTemplate(ParentComponent.class, "index-item", Map.of());
                indexItem.childById(ItemComponent.class, "icon").stack(entry.icon().getDefaultStack());

                var label = indexItem.childById(LabelComponent.class, "index-label");

                label.text(Text.translatable(Util.createTranslationKey("entry", entry.id())).styled($ -> $.withFont(MinecraftClient.UNICODE_FONT_ID)));
                label.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    navPush(new EntryPageSupplier(entry));
                    UISounds.playInteractionSound();
                    return true;
                });

                var animation = label.color().animate(150, Easing.SINE, Color.ofFormatting(Formatting.GOLD));
                label.mouseEnter().subscribe(animation::forwards);
                label.mouseLeave().subscribe(animation::backwards);

                index.child(indexItem);
            });

            this.pages.add(index);
        }

        @Override
        public Component getPageContent(int pageIndex) {
            return this.pages.get(pageIndex);
        }

        @Override
        public int pageCount() {
            return 2;
        }

        @Override
        public Function<BookScreen, PageSupplier> replicator() {
            return bookScreen -> bookScreen.new IndexPageSupplier();
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
                        if (descendant instanceof BookCompiler.BookLabelComponent label) {
                            label.setOwner(BookScreen.this);
                        }
                    });
                }

                if (firstPage) {
                    firstPage = false;
                    this.pages.add(makePageContentWithHeader(entry.title()).child(component));
                } else {
                    this.pages.add(component);
                }
            }
        }

        @Override
        public Component getPageContent(int pageIndex) {
            return this.pages.get(pageIndex);
        }

        @Override
        public int pageCount() {
            return this.pages.size();
        }

        @Override
        public Function<BookScreen, PageSupplier> replicator() {
            return bookScreen -> bookScreen.new EntryPageSupplier(this.entry);
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

        public Replicator replicator() {
            return new Replicator(this.pageSupplier.replicator(), this.selectedPage);
        }

        public record Replicator(Function<BookScreen, PageSupplier> pageSupplier, int selectedPage) {
            public NavFrame createFrame(BookScreen screen) {
                var frame = new NavFrame(this.pageSupplier.apply(screen));
                frame.selectedPage = this.selectedPage;
                return frame;
            }
        }
    }

    static {
        UIParsing.registerFactory("lavender.structure", StructureComponent::parse);
    }
}
