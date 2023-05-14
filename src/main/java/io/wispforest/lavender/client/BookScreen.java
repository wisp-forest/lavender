package io.wispforest.lavender.client;

import com.google.common.collect.Iterables;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Book;
import io.wispforest.lavender.book.Category;
import io.wispforest.lavender.book.Entry;
import io.wispforest.lavender.book.StructureComponent;
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
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
            new PageBreakExtension(), new OwoUITemplateExtension(), new RecipeExtension()
    );

    public final Book book;
    private int previousUiScale;

    private ButtonComponent previousButton;
    private ButtonComponent returnButton;
    private ButtonComponent nextButton;

    private FlowLayout leftPageAnchor;
    private FlowLayout rightPageAnchor;

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

        this.leftPageAnchor = this.component(FlowLayout.class, "left-page-anchor");
        this.rightPageAnchor = this.component(FlowLayout.class, "right-page-anchor");

        (this.previousButton = this.component(ButtonComponent.class, "previous-button")).onPress(buttonComponent -> this.turnPage(true));
        (this.returnButton = this.component(ButtonComponent.class, "back-button")).onPress(buttonComponent -> this.navPop());
        (this.nextButton = this.component(ButtonComponent.class, "next-button")).onPress(buttonComponent -> this.turnPage(false));

        var navTrail = getNavTrail(this.book);
        for (int i = navTrail.size() - 1; i >= 0; i--) {
            var frame = navTrail.get(i).createFrame(this);
            if (frame == null) continue;

            this.navPush(frame, true);
        }

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
        this.navPush(new NavFrame(supplier));
    }

    public void navPush(NavFrame frame) {
        this.navPush(frame, false);
    }

    public void navPush(NavFrame frame, boolean suppressUpdate) {
        var topFrame = this.navStack.peek();
        if (topFrame != null && frame.pageSupplier.canMerge(topFrame.pageSupplier)) {
            topFrame.selectedPage = frame.selectedPage;
        } else {
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

        protected List<FlowLayout> buildEntryIndex(Collection<Entry> entries, int... maxEntriesPerPage) {
            var indexSections = new ArrayList<FlowLayout>();
            indexSections.add(Containers.verticalFlow(Sizing.fill(100), Sizing.content()));

            for (var entry : entries) {
                if (entry == this.context.book.landingPage() || !entry.canPlayerView(this.context.client.player))
                    continue;

                var indexItem = this.context.model.expandTemplate(ParentComponent.class, "index-item", Map.of());
                indexItem.childById(ItemComponent.class, "icon").stack(entry.icon().getDefaultStack());

                var label = indexItem.childById(LabelComponent.class, "index-label");

                label.text(Text.translatable(Util.createTranslationKey("entry", entry.id())).styled($ -> $.withFont(MinecraftClient.UNICODE_FONT_ID)));
                label.mouseDown().subscribe((mouseX, mouseY, button) -> {
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
    }

    public static class IndexPageSupplier extends PageSupplier {

        public IndexPageSupplier(BookScreen context) {
            super(context);

            var book = this.context.book;
            var landingPageEntry = book.landingPage();

            if (landingPageEntry != null) {
                var landingPage = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
                landingPage.child(this.context.model.expandTemplate(Component.class, "landing-page-header", Map.of("page-title", landingPageEntry.title())));
                landingPage.child(PROCESSOR.process(landingPageEntry.content()));

                // TODO this should use a builtin book component
                if (book.displayCompletion()) {
                    landingPage.child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                            .child(BOOK_COMPONENTS.get().expandTemplate(Component.class, "horizontal-rule", Map.of()).margins(Insets.bottom(4)))
                            .child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                                    .child(Components.label(Text.literal("Unlocked: " + book.countVisibleEntries(this.context.client.player) + "/" + book.entries().size()).styled($ -> $.withFont(MinecraftClient.UNICODE_FONT_ID).withFormatting(Formatting.DARK_GRAY))))
                                    .child(Containers.verticalFlow(Sizing.content(), Sizing.content())
                                            .child(Components.texture(Lavender.id("textures/gui/book.png"), 268, 129, 100, 5, 512, 256))
                                            .child(Components.texture(Lavender.id("textures/gui/book.png"), 268, 134, 100, 5, 512, 256)
                                                    .visibleArea(PositionedRectangle.of(0, 0, (int) (100 * (book.countVisibleEntries(this.context.client.player) / (float) book.entries().size())), 5))
                                                    .positioning(Positioning.absolute(0, 0)))))
                            .horizontalAlignment(HorizontalAlignment.CENTER)
                            .positioning(Positioning.relative(50, 100))
                            .margins(Insets.bottom(3)));
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
                                .tooltip(Text.translatable(Util.createTranslationKey("category", category.id())))
                                .margins(Insets.of(4))
                                .cursorStyle(CursorStyle.HAND);

                        categoryButton.mouseDown().subscribe((mouseX, mouseY, button) -> {
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

    public static class CategoryPageSupplier extends PageSupplier {

        private final Category category;

        public CategoryPageSupplier(BookScreen context, Category category) {
            super(context);
            this.category = category;

            // --- landing page ---

            var parsedLandingPage = PROCESSOR.process(category.content());

            var landingPageContent = parsedLandingPage.children().get(0);
            parsedLandingPage.removeChild(landingPageContent);

            this.pages.add(this.pageWithHeader(Language.getInstance().get(Util.createTranslationKey("category", category.id()))).child(landingPageContent));

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
    }

    public static class EntryPageSupplier extends PageSupplier {

        private final Entry entry;

        public EntryPageSupplier(BookScreen context, Entry entry) {
            super(context);
            this.entry = entry;

            var pages = PROCESSOR.process(entry.content());
            boolean firstPage = true;

            while (!pages.children().isEmpty()) {
                var component = pages.children().get(0);
                pages.removeChild(component);

                if (component instanceof ParentComponent parent) {
                    parent.forEachDescendant(descendant -> {
                        if (descendant instanceof BookCompiler.BookLabelComponent label) {
                            label.setOwner(this.context);
                        }
                    });
                }

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

        public record Replicator(Function<BookScreen, @Nullable PageSupplier> pageSupplier, int selectedPage) {
            public @Nullable NavFrame createFrame(BookScreen screen) {
                var supplier = this.pageSupplier.apply(screen);
                if (supplier == null) return null;

                var frame = new NavFrame(supplier);
                frame.selectedPage = this.selectedPage;
                return frame;
            }
        }
    }

    static {
        UIParsing.registerFactory("lavender.structure", StructureComponent::parse);
    }
}
