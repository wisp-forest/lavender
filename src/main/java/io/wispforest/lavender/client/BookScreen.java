package io.wispforest.lavender.client;

import com.google.common.collect.Iterables;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.*;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.extensions.*;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class BookScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    private static final Map<Identifier, List<NavFrame.Replicator>> NAV_TRAILS = new HashMap<>();

    private static final Supplier<UIModel> BOOK_COMPONENTS = () -> UIModelLoader.get(Lavender.id("book_components"));
    private static final MarkdownProcessor<ParentComponent> PROCESSOR = new MarkdownProcessor<>(
            BookCompiler::new,
            new BlockStateExtension(), new ItemStackExtension(), new EntityExtension(),
            new PageBreakExtension(), new OwoUITemplateExtension(), new RecipeExtension(),
            new StructureExtension(), new KeybindExtension()
    );

    public final Book book;

    private Window window;
    private int scaleFactor;

    private ButtonComponent previousButton;
    private ButtonComponent returnButton;
    private ButtonComponent nextButton;

    private FlowLayout leftPageAnchor;
    private FlowLayout rightPageAnchor;
    private FlowLayout bookmarkPanel;

    private final Deque<NavFrame> navStack = new ArrayDeque<>();

    public BookScreen(Book book) {
        super(FlowLayout.class, Lavender.id("book"));
        this.book = book;
    }

    @Override
    protected void init() {
        this.window = this.client.getWindow();
        double gameScale = this.window.getScaleFactor();

        this.scaleFactor = this.window.calculateScaleFactor(this.client.options.getGuiScale().getValue(), true);
        this.window.setScaleFactor(this.scaleFactor);

        this.width = this.window.getScaledWidth();
        this.height = this.window.getScaledHeight();

        super.init();

        this.window.setScaleFactor(gameScale);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.leftPageAnchor = this.component(FlowLayout.class, "left-page-anchor");
        this.rightPageAnchor = this.component(FlowLayout.class, "right-page-anchor");
        this.bookmarkPanel = this.component(FlowLayout.class, "bookmark-panel");

        (this.previousButton = this.component(ButtonComponent.class, "previous-button")).onPress(buttonComponent -> this.turnPage(true));
        (this.returnButton = this.component(ButtonComponent.class, "back-button")).onPress(buttonComponent -> this.navPop());
        (this.nextButton = this.component(ButtonComponent.class, "next-button")).onPress(buttonComponent -> this.turnPage(false));

        var navTrail = getNavTrail(this.book);
        for (int i = navTrail.size() - 1; i >= 0; i--) {
            var frame = navTrail.get(i).createFrame(this);
            if (frame == null) continue;

            this.navPush(frame, true);
        }

        if (this.navStack.isEmpty()) this.navPush(new NavFrame(new IndexPageSupplier(this), 0), true);
        this.rebuildContent(Lavender.ITEM_BOOK_OPEN);
    }

    private void rebuildContent(@Nullable SoundEvent sound) {
        if (sound != null) this.client.player.playSound(sound, 1f, 1f);

        var pageSupplier = this.currentNavFrame().pageSupplier;
        int selectedPage = this.currentNavFrame().selectedPage;

        this.returnButton.active(this.navStack.size() > 1);
        this.previousButton.active(selectedPage > 0);
        this.nextButton.active(selectedPage + 2 < pageSupplier.pageCount());

        int index = 0;
        while (index < 2) {
            var anchor = index == 0 ? this.leftPageAnchor : this.rightPageAnchor;
            anchor.clearChildren();

            if (selectedPage + index < pageSupplier.pageCount()) {
                anchor.child(pageSupplier.getPageContent(selectedPage + index));
            } else {
                anchor.child(this.model.expandTemplate(Component.class, "empty-page-content", Map.of()));
            }

            index++;
        }

        this.bookmarkPanel.<FlowLayout>configure(bookmarkContainer -> {
            bookmarkContainer.clearChildren();

            var bookmarks = LavenderBookmarks.getBookmarks(this.book);
            for (var bookmark : bookmarks) {
                var element = bookmark.tryResolve(this.book);
                if (element == null) continue;

                var bookmarkComponent = this.createBookmarkButton("bookmark");
                bookmarkComponent.childById(ItemComponent.class, "bookmark-preview").stack(element.icon().getDefaultStack());
                bookmarkComponent.childById(ButtonComponent.class, "bookmark-button").<ButtonComponent>configure(bookmarkButton -> {
                    bookmarkButton.tooltip(List.of(Text.literal(element.title()), Text.translatable("text.lavender.book.bookmark.remove_hint")));
                    bookmarkButton.onPress($ -> {
                        if (Screen.hasShiftDown()) {
                            LavenderBookmarks.removeBookmark(this.book, bookmark);
                            this.rebuildContent(null);
                        } else if (element instanceof Entry entry) {
                            this.navPush(new EntryPageSupplier(this, entry));
                        } else if (element instanceof Category category) {
                            this.navPush(new CategoryPageSupplier(this, category));
                        }
                    });
                });

                bookmarkContainer.child(bookmarkComponent);
            }

            if (this.currentNavFrame().pageSupplier instanceof PageSupplier.Bookmarkable bookmarkable) {
                var addBookmarkButton = this.createBookmarkButton("add-bookmark");
                addBookmarkButton.childById(ButtonComponent.class, "bookmark-button").<ButtonComponent>configure(bookmarkButton -> {
                    bookmarkButton.tooltip(Text.translatable("text.lavender.book.bookmark.add"));
                    bookmarkButton.onPress($ -> {
                        bookmarkable.addBookmark();
                        this.rebuildContent(null);
                        this.component(ScrollContainer.class, "bookmark-scroll").scrollTo(1);
                    });
                });

                bookmarkContainer.child(addBookmarkButton);
            }
        });
    }

    protected ParentComponent createBookmarkButton(String templateName) {
        var bookmark = this.model.expandTemplate(ParentComponent.class, templateName, Map.of());
        bookmark.mouseEnter().subscribe(() -> bookmark.margins(Insets.none()));
        bookmark.mouseLeave().subscribe(() -> bookmark.margins(Insets.top(1)));
        return bookmark;
    }

    protected NavFrame currentNavFrame() {
        return this.navStack.peek();
    }

    private void turnPage(boolean left) {
        var frame = this.currentNavFrame();
        frame.selectedPage = Math.max(0, Math.min(frame.selectedPage + (left ? -2 : 2), frame.pageSupplier.pageCount() - 1)) / 2 * 2;

        this.rebuildContent(SoundEvents.ITEM_BOOK_PAGE_TURN);
    }

    public void navPush(PageSupplier supplier) {
        this.navPush(new NavFrame(supplier, 0));
    }

    public void navPush(NavFrame frame) {
        this.navPush(frame, false);
    }

    public void navPush(NavFrame frame, boolean suppressUpdate) {
        var topFrame = this.navStack.peek();
        if (topFrame != null && frame.pageSupplier.canMerge(topFrame.pageSupplier)) {
            topFrame.selectedPage = frame.selectedPage;
        } else {
            if (frame.selectedPage >= frame.pageSupplier.pageCount() - 1) {
                frame.selectedPage = frame.pageSupplier.pageCount() / 2 * 2;
            }

            this.navStack.push(frame);
        }

        if (!suppressUpdate) this.rebuildContent(SoundEvents.ITEM_BOOK_PAGE_TURN);
    }

    public void navPop() {
        if (this.navStack.size() <= 1) return;

        this.navStack.pop();
        this.rebuildContent(SoundEvents.ITEM_BOOK_PAGE_TURN);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        mouseX = (int) (mouseX * this.window.getScaleFactor() / this.scaleFactor);
        mouseY = (int) (mouseY * this.window.getScaleFactor() / this.scaleFactor);

        double gameScale = this.window.getScaleFactor();
        this.window.setScaleFactor(this.scaleFactor);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(
                0,
                this.window.getFramebufferWidth() / (float) this.scaleFactor,
                this.window.getFramebufferHeight() / (float) this.scaleFactor,
                0,
                1000,
                3000
        ));

        super.render(matrices, mouseX, mouseY, delta);

        this.window.setScaleFactor(gameScale);
        RenderSystem.restoreProjectionMatrix();
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
        mouseX = mouseX * this.window.getScaleFactor() / this.scaleFactor;
        mouseY = mouseY * this.window.getScaleFactor() / this.scaleFactor;

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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        mouseX = mouseX * this.window.getScaleFactor() / this.scaleFactor;
        mouseY = mouseY * this.window.getScaleFactor() / this.scaleFactor;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = mouseX * this.window.getScaleFactor() / this.scaleFactor;
        mouseY = mouseY * this.window.getScaleFactor() / this.scaleFactor;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        mouseX = mouseX * this.window.getScaleFactor() / this.scaleFactor;
        mouseY = mouseY * this.window.getScaleFactor() / this.scaleFactor;
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void removed() {
        super.removed();

        var trail = new ArrayList<NavFrame.Replicator>();
        while (!this.navStack.isEmpty()) trail.add(this.navStack.pop().replicator());

        NAV_TRAILS.put(this.book.id(), trail);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected static List<NavFrame.Replicator> getNavTrail(Book book) {
        return NAV_TRAILS.computeIfAbsent(book.id(), $ -> Util.make(
                new ArrayList<>(),
                trail -> trail.add(0, new NavFrame.Replicator(IndexPageSupplier::new, 0))
        ));
    }

    public static void pushEntry(Book book, Entry entry) {
        getNavTrail(book).add(0, new NavFrame.Replicator(screen -> new EntryPageSupplier(screen, entry), 0));
    }

    public static abstract class PageSupplier {

        protected final BookScreen context;
        protected final List<Component> pages = new ArrayList<>();

        protected PageSupplier(BookScreen context) {
            this.context = context;
        }

        public int pageCount() {
            return this.pages.size();
        }

        public Component getPageContent(int pageIndex) {
            return this.pages.get(pageIndex);
        }

        abstract boolean canMerge(PageSupplier other);

        abstract Function<BookScreen, @Nullable PageSupplier> replicator();

        // --- prebuilt utility ---

        protected FlowLayout pageWithHeader(@NotNull String title) {
            var container = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
            container.child(this.context.model.expandTemplate(Component.class, "page-title-header", Map.of("page-title", title)));

            return container;
        }

        protected ParentComponent parseMarkdown(String markdown) {
            var component = PROCESSOR.process(markdown);
            component.forEachDescendant(descendant -> {
                if (descendant instanceof BookCompiler.BookLabelComponent label) {
                    label.setOwner(this.context);
                }
            });

            return component;
        }

        protected List<FlowLayout> buildEntryIndex(Collection<Entry> entries, int... maxEntriesPerPage) {
            var indexSections = new ArrayList<FlowLayout>();
            indexSections.add(Containers.verticalFlow(Sizing.fill(100), Sizing.content()));

            for (var entry : entries) {
                if (entry == this.context.book.landingPage() || !entry.canPlayerView(this.context.client.player)) {
                    continue;
                }

                var indexItem = this.context.model.expandTemplate(ParentComponent.class, "index-item", Map.of());
                indexItem.childById(ItemComponent.class, "icon").stack(entry.icon().getDefaultStack());

                var label = indexItem.childById(LabelComponent.class, "index-label");

                label.text(Text.literal(entry.title()).styled($ -> $.withFont(MinecraftClient.UNICODE_FONT_ID)));
                label.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

                    this.context.navPush(new EntryPageSupplier(this.context, entry));
                    UISounds.playInteractionSound();
                    return true;
                });

                var animation = label.color().animate(150, Easing.SINE, Color.ofFormatting(Formatting.GOLD));
                label.mouseEnter().subscribe(animation::forwards);
                label.mouseLeave().subscribe(animation::backwards);

                int sectionIndex = indexSections.size() - 1;
                if (indexSections.get(sectionIndex).children().size() >= (sectionIndex < maxEntriesPerPage.length ? maxEntriesPerPage[sectionIndex] : 12)) {
                    indexSections.add(Containers.verticalFlow(Sizing.fill(100), Sizing.content()));
                }

                Iterables.getLast(indexSections).child(indexItem);
            }

            return indexSections;
        }

        public interface Bookmarkable {
            void addBookmark();
        }
    }

    public static class IndexPageSupplier extends PageSupplier {

        public IndexPageSupplier(BookScreen context) {
            super(context);

            var book = this.context.book;
            var landingPageEntry = book.landingPage();

            if (landingPageEntry != null) {
                var landingPage = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
                landingPage.child(this.context.model.expandTemplate(Component.class, "landing-page-header", Map.of("page-title", landingPageEntry.title())));
                landingPage.child(this.parseMarkdown(landingPageEntry.content()));

                if (book.displayCompletion()) {
                    var completionBar = this.context.model.expandTemplate(
                            FlowLayout.class,
                            "completion-bar",
                            Map.of("progress", String.valueOf((int) (100 * (book.countVisibleEntries(this.context.client.player) / (float) book.entries().size()))))
                    );

                    completionBar.childById(LabelComponent.class, "completion-label")
                            .text(Text.translatable("text.lavender.book.unlock_progress", book.countVisibleEntries(this.context.client.player), book.entries().size()));

                    landingPage.child(completionBar);
                }

                this.pages.add(landingPage);
            } else {
                this.pages.add(this.context.model.expandTemplate(Component.class, "empty-page-content", Map.of()));
            }

            var indexPage = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            this.pages.add(indexPage);

            if (!book.categories().isEmpty()) {
                var categories = this.pageWithHeader("Categories");
                categories.verticalSizing(Sizing.content());

                var categoryContainer = Containers.ltrTextFlow(Sizing.fill(100), Sizing.content()).gap(4);
                categories.child(categoryContainer);

                for (var category : book.categories()) {
                    if (!book.shouldDisplayCategory(category, this.context.client.player)) continue;

                    categoryContainer.child(Components.item(category.icon().getDefaultStack()).<ItemComponent>configure(categoryButton -> {
                        categoryButton
                                .tooltip(Text.literal(category.title()))
                                .margins(Insets.of(4))
                                .cursorStyle(CursorStyle.HAND);

                        categoryButton.mouseDown().subscribe((mouseX, mouseY, button) -> {
                            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

                            this.context.navPush(new CategoryPageSupplier(this.context, category));
                            UISounds.playInteractionSound();
                            return true;
                        });
                    }));
                }

                indexPage.child(categories);
            }

            indexPage.child(book.categories().isEmpty()
                    ? this.context.model.expandTemplate(Component.class, "page-title-header", Map.of("page-title", "Index"))
                    : BOOK_COMPONENTS.get().expandTemplate(Component.class, "horizontal-rule", Map.of()).margins(Insets.vertical(6))
            );

            int entriesOnCategoryPage = book.categories().size() > 0
                    ? 12 - 3 - MathHelper.ceilDiv(book.categories().size() - 1, 4) * 2
                    : 12;

            var orphanedEntries = this.buildEntryIndex(book.orphanedEntries(), entriesOnCategoryPage);
            indexPage.child(orphanedEntries.remove(0));
            this.pages.addAll(orphanedEntries);
        }

        @Override
        public boolean canMerge(PageSupplier other) {
            return other instanceof IndexPageSupplier;
        }

        @Override
        public Function<BookScreen, @Nullable PageSupplier> replicator() {
            return IndexPageSupplier::new;
        }
    }

    public static class CategoryPageSupplier extends PageSupplier implements PageSupplier.Bookmarkable {

        private final Category category;

        public CategoryPageSupplier(BookScreen context, Category category) {
            super(context);
            this.category = category;

            // --- landing page ---

            var parsedLandingPage = this.parseMarkdown(category.content());

            var landingPageContent = parsedLandingPage.children().get(0);
            parsedLandingPage.removeChild(landingPageContent);

            this.pages.add(this.pageWithHeader(category.title()).child(landingPageContent));

            // --- entry index ---

            var entries = this.context.book.entriesByCategory(this.category);
            if (entries != null) {
                var indexPages = this.buildEntryIndex(entries);
                for (int i = 0; i < indexPages.size(); i++) {
                    this.pages.add(i == 0 ? this.pageWithHeader("Index").child(indexPages.get(0)) : indexPages.get(i));
                }
            }
        }

        @Override
        public boolean canMerge(PageSupplier other) {
            return other instanceof CategoryPageSupplier supplier && supplier.category.id().equals(this.category.id());
        }

        @Override
        public Function<BookScreen, @Nullable PageSupplier> replicator() {
            return context -> {
                var category = context.book.categoryById(this.category.id());
                return category != null && context.book.shouldDisplayCategory(category, context.client.player) ? new CategoryPageSupplier(context, category) : null;
            };
        }

        @Override
        public void addBookmark() {
            LavenderBookmarks.addBookmark(this.context.book, this.category);
        }
    }

    public static class EntryPageSupplier extends PageSupplier implements PageSupplier.Bookmarkable {

        private final Entry entry;

        public EntryPageSupplier(BookScreen context, Entry entry) {
            super(context);
            this.entry = entry;

            var pages = this.parseMarkdown(entry.content());
            boolean firstPage = true;

            while (!pages.children().isEmpty()) {
                var component = pages.children().get(0);
                pages.removeChild(component);

                if (firstPage) {
                    firstPage = false;
                    this.pages.add(this.pageWithHeader(entry.title()).child(component));
                } else {
                    this.pages.add(component);
                }
            }
        }

        @Override
        public boolean canMerge(PageSupplier other) {
            return other instanceof EntryPageSupplier supplier && supplier.entry.id().equals(this.entry.id());
        }

        @Override
        public Function<BookScreen, @Nullable PageSupplier> replicator() {
            return context -> {
                var entry = context.book.entryById(this.entry.id());
                return entry != null && entry.canPlayerView(context.client.player) ? new EntryPageSupplier(context, entry) : null;
            };
        }

        @Override
        public void addBookmark() {
            LavenderBookmarks.addBookmark(this.context.book, this.entry);
        }
    }

    public static class NavFrame {
        public final PageSupplier pageSupplier;
        public int selectedPage;

        public NavFrame(PageSupplier pageSupplier, int selectedPage) {
            this.pageSupplier = pageSupplier;
            this.selectedPage = selectedPage;
        }

        public Replicator replicator() {
            return new Replicator(this.pageSupplier.replicator(), this.selectedPage);
        }

        public record Replicator(Function<BookScreen, @Nullable PageSupplier> pageSupplier, int selectedPage) {
            public @Nullable NavFrame createFrame(BookScreen screen) {
                var supplier = this.pageSupplier.apply(screen);
                if (supplier == null) return null;

                return new NavFrame(supplier, this.selectedPage);
            }
        }
    }

    static {
        UIParsing.registerFactory("lavender.structure", StructureComponent::parse);
    }
}
