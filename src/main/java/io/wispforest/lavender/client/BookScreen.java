package io.wispforest.lavender.client;

import com.google.common.collect.Iterables;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.*;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.extensions.*;
import io.wispforest.owo.Owo;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
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

public class BookScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    private static final Identifier DEFAULT_BOOK_TEXTURE = Lavender.id("textures/gui/brown_book.png");
    private static final Map<Identifier, Map<RecipeType<?>, RecipeExtension.RecipeHandler<?>>> RECIPE_HANDLERS = new HashMap<>();

    private static final Map<Identifier, List<NavFrame.Replicator>> NAV_TRAILS = new HashMap<>();

    private final BookCompiler.ComponentSource bookComponentSource = BookScreen.this::template;

    public final Book book;
    public final boolean isOverlay;

    private final MarkdownProcessor<ParentComponent> processor;
    private Window window;
    private int scaleFactor;

    private ButtonComponent previousButton;
    private ButtonComponent returnButton;
    private ButtonComponent nextButton;
    private TextBoxComponent searchBox;

    private FlowLayout leftPageAnchor;
    private FlowLayout rightPageAnchor;
    private FlowLayout bookmarkPanel;

    private final Deque<NavFrame> navStack = new ArrayDeque<>();

    public BookScreen(Book book, boolean isOverlay) {
        super(FlowLayout.class, Lavender.id("book"));
        this.book = book;
        this.isOverlay = isOverlay;

        this.processor = new MarkdownProcessor<>(
                () -> new BookCompiler(this.bookComponentSource),
                new BlockStateExtension(), new ItemStackExtension(), new EntityExtension(),
                new PageBreakExtension(), new OwoUITemplateExtension(this.bookComponentSource),
                new RecipeExtension(this.bookComponentSource, RECIPE_HANDLERS.get(this.book.id())),
                new StructureExtension(this.bookComponentSource), new KeybindExtension()
        );
    }

    public BookScreen(Book book) {
        this(book, false);
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

    protected <C extends Component> C template(Class<C> expectedComponentClass, String name) {
        return this.template(expectedComponentClass, name, Map.of());
    }

    protected <C extends Component> C template(Class<C> expectedComponentClass, String name, Map<String, String> parameters) {
        return this.template(this.model, expectedComponentClass, name, parameters);
    }

    protected <C extends Component> C template(UIModel model, Class<C> expectedComponentClass, String name, Map<String, String> parameters) {
        var params = new HashMap<String, String>();
        params.put("book-texture", (this.book.texture() != null ? this.book.texture() : DEFAULT_BOOK_TEXTURE).toString());
        params.putAll(parameters);

        return model.expandTemplate(expectedComponentClass, name, params);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        if (this.isOverlay) rootComponent.surface(Surface.BLANK);
        rootComponent.child(this.template(Component.class, "primary-panel"));

        this.leftPageAnchor = this.component(FlowLayout.class, "left-page-anchor");
        this.rightPageAnchor = this.component(FlowLayout.class, "right-page-anchor");
        this.bookmarkPanel = this.component(FlowLayout.class, "bookmark-panel");

        (this.previousButton = this.component(ButtonComponent.class, "previous-button")).onPress(button -> this.turnPage(true));
        (this.nextButton = this.component(ButtonComponent.class, "next-button")).onPress(button -> this.turnPage(false));
        (this.returnButton = this.component(ButtonComponent.class, "back-button")).onPress(button -> {
            if (Screen.hasShiftDown()) {
                while (this.navStack.size() > 1) this.navStack.pop();
                this.rebuildContent(SoundEvents.ITEM_BOOK_PAGE_TURN);
            } else {
                this.navPop();
            }
        });

        if (Owo.DEBUG && !this.isOverlay) {
            this.component(FlowLayout.class, "primary-panel").child(
                    this.template(ButtonComponent.class, "reload-button").onPress(buttonComponent -> {
                        BookLoader.reload(this.client.getResourceManager());
                        BookContentLoader.reloadContents(this.client.getResourceManager());

                        var newBook = BookLoader.get(this.book.id());
                        if (newBook != null) {
                            this.client.setScreen(new BookScreen(newBook));
                        } else {
                            this.client.setScreen(null);
                        }
                    })
            );
        }

        this.searchBox = this.component(TextBoxComponent.class, "search-box");
        searchBox.visible = searchBox.active = false;
        searchBox.onChanged().subscribe(value -> {
            var frame = this.currentNavFrame();

            this.navStack.pop();
            this.navPush(new NavFrame(frame.pageSupplier.replicator().apply(this), frame.selectedPage), true);

            this.rebuildContent(null);
        });

        var navTrail = getNavTrail(this.book);
        for (int i = navTrail.size() - 1; i >= 0; i--) {
            var frame = navTrail.get(i).createFrame(this);
            if (frame == null) continue;

            this.navPush(frame, true);
        }

        if (this.navStack.isEmpty()) this.navPush(new NavFrame(new LandingPageSupplier(this), 0), true);
        this.rebuildContent(!this.isOverlay ? Lavender.ITEM_BOOK_OPEN : null);
    }

    private void rebuildContent(@Nullable SoundEvent sound) {
        if (sound != null) this.client.player.playSound(sound, 1f, 1f);

        var pageSupplier = this.currentNavFrame().pageSupplier;
        int selectedPage = this.currentNavFrame().selectedPage;

        if (selectedPage >= pageSupplier.pageCount()) {
            selectedPage = this.currentNavFrame().selectedPage = (pageSupplier.pageCount() - 1) / 2 * 2;
        }

        if (!this.isOverlay) {
            this.returnButton.active(this.navStack.size() > 1);
            this.previousButton.active(selectedPage > 0);
            this.nextButton.active(selectedPage + 2 < pageSupplier.pageCount());
        }

        searchBox.visible = searchBox.active = pageSupplier.searchable();

        int index = 0;
        while (index < 2) {
            var anchor = index == 0 ? this.leftPageAnchor : this.rightPageAnchor;
            anchor.clearChildren();

            if (selectedPage + index < pageSupplier.pageCount()) {
                anchor.child(pageSupplier.getPageContent(selectedPage + index));
            } else {
                anchor.child(this.template(Component.class, "empty-page-content"));
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
        var bookmark = this.template(ParentComponent.class, templateName);
        bookmark.mouseEnter().subscribe(() -> bookmark.margins(Insets.none()));
        bookmark.mouseLeave().subscribe(() -> bookmark.margins(Insets.top(1)));
        return bookmark;
    }

    protected NavFrame currentNavFrame() {
        return this.navStack.peek();
    }

    private void turnPage(boolean left) {
        var frame = this.currentNavFrame();

        int previousPage = frame.selectedPage;
        frame.selectedPage = Math.max(0, Math.min(frame.selectedPage + (left ? -2 : 2), frame.pageSupplier.pageCount() - 1)) / 2 * 2;

        if (frame.selectedPage != previousPage) {
            this.rebuildContent(SoundEvents.ITEM_BOOK_PAGE_TURN);
        }
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

        RenderSystem.restoreProjectionMatrix();
        this.window.setScaleFactor(gameScale);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (super.charTyped(chr, modifiers)) return true;

        if (chr == 'e' && (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            this.navPush(new EditorPageSupplier(this));
            return true;
        }

        this.searchBox.focusHandler().focus(this.searchBox, Component.FocusSource.MOUSE_CLICK);
        this.searchBox.charTyped(chr, modifiers);

        return true;
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

        if (super.mouseScrolled(mouseX, mouseY, amount)) return true;
        this.turnPage(amount < 0);

        return true;
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

    public OwoUIAdapter<?> adapter() {
        return this.uiAdapter;
    }

    protected static List<NavFrame.Replicator> getNavTrail(Book book) {
        return NAV_TRAILS.computeIfAbsent(book.id(), $ -> Util.make(
                new ArrayList<>(),
                trail -> trail.add(0, new NavFrame.Replicator(LandingPageSupplier::new, 0))
        ));
    }

    public static void pushEntry(Book book, Entry entry) {
        getNavTrail(book).add(0, new NavFrame.Replicator(screen -> new EntryPageSupplier(screen, entry), 0));
    }

    public static <R extends Recipe<?>> void registerRecipeHandler(Identifier bookId, RecipeType<R> recipeType, RecipeExtension.RecipeHandler<R> handler) {
        RECIPE_HANDLERS.computeIfAbsent(bookId, $ -> new HashMap<>()).put(recipeType, handler);
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

        public boolean searchable() {
            return false;
        }

        abstract boolean canMerge(PageSupplier other);

        abstract Function<BookScreen, @Nullable PageSupplier> replicator();

        // --- prebuilt utility ---

        protected FlowLayout pageWithHeader(@NotNull Text title) {
            return Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100)).child(this.pageTitleHeader(title));
        }

        protected Component pageTitleHeader(Text text) {
            var component = this.context.template(ParentComponent.class, "page-title-header");
            component.childById(LabelComponent.class, "title-label").text(text);
            return component;
        }

        protected ParentComponent parseMarkdown(String markdown) {
            var component = this.context.processor.process(markdown);
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

            var searchText = this.context.searchBox.getText().strip();
            var filter = searchText.isEmpty() ? new String[0] : searchText.split(" ");
            for (int i = 0; i < filter.length; i++) {
                filter[i] = filter[i].strip().toLowerCase(Locale.ROOT);
            }

            entries.stream()
                    .sorted((o1, o2) -> AlphanumComparator.compare(o1.title(), o2.title()))
                    .sorted(Comparator.comparing(entry -> !entry.canPlayerView(this.context.client.player)))
                    .forEach(entry -> {
                        boolean entryVisible = entry.canPlayerView(this.context.client.player);
                        if (entry.secret() && !entryVisible) {
                            return;
                        }

                        if (filter.length > 0) {
                            if (!entryVisible) return;

                            var entryTitle = entry.title().toLowerCase(Locale.ROOT);
                            for (var term : filter) {
                                if (!entryTitle.contains(term)) return;
                            }
                        }

                        ParentComponent indexItem;
                        if (entryVisible) {
                            indexItem = this.context.template(ParentComponent.class, "index-item");
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
                        } else {
                            indexItem = this.context.template(ParentComponent.class, "locked-index-item");
                            indexItem.childById(LabelComponent.class, "index-label").text(Text.translatable("text.lavender.entry.locked"));
                        }

                        int sectionIndex = indexSections.size() - 1;
                        if (indexSections.get(sectionIndex).children().size() >= (sectionIndex < maxEntriesPerPage.length ? maxEntriesPerPage[sectionIndex] : 12)) {
                            indexSections.add(Containers.verticalFlow(Sizing.fill(100), Sizing.content()));
                        }

                        Iterables.getLast(indexSections).child(indexItem);
                    });

            return indexSections;
        }

        public interface Bookmarkable {
            void addBookmark();
        }
    }

    public static class LandingPageSupplier extends PageSupplier {

        public LandingPageSupplier(BookScreen context) {
            super(context);

            var book = this.context.book;
            var landingPageEntry = book.landingPage();

            if (landingPageEntry != null) {
                var landingPage = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
                landingPage.child(this.context.template(Component.class, "landing-page-header", Map.of("page-title", landingPageEntry.title())));
                landingPage.child(this.parseMarkdown(landingPageEntry.content()));

                if (book.displayCompletion()) {
                    var completionBar = this.context.template(
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
                this.pages.add(this.context.template(Component.class, "empty-page-content"));
            }

            var indexPage = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            this.pages.add(indexPage);

            if (!book.categories().isEmpty()) {
                var categories = this.pageWithHeader(Text.translatable("text.lavender.categories"));
                categories.verticalSizing(Sizing.content());

                var categoryContainer = Containers.ltrTextFlow(Sizing.fill(100), Sizing.content()).gap(4);
                categories.child(categoryContainer);

                book.categories().stream().sorted(Comparator.comparing($ -> !book.shouldDisplayCategory($, this.context.client.player))).forEach(category -> {
                    if (book.shouldDisplayCategory(category, this.context.client.player)) {
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
                    } else if (!category.secret()) {
                        categoryContainer.child(this.context.template(Component.class, "locked-category-button"));
                    }
                });

                categoryContainer.child(Components.item(BookItem.itemOf(this.context.book)).<ItemComponent>configure(categoryButton -> {
                    categoryButton
                            .tooltip(Text.translatable("text.lavender.index_category"))
                            .margins(Insets.of(4))
                            .cursorStyle(CursorStyle.HAND);

                    categoryButton.mouseDown().subscribe((mouseX, mouseY, button) -> {
                        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

                        this.context.navPush(new IndexPageSupplier(this.context));
                        UISounds.playInteractionSound();
                        return true;
                    });
                }));

                indexPage.child(categories);
            }

            indexPage.child(book.categories().isEmpty()
                    ? this.pageTitleHeader(Text.translatable("text.lavender.index"))
                    : this.context.bookComponentSource.builtinTemplate(Component.class, "horizontal-rule").margins(Insets.vertical(6))
            );

            int entriesOnCategoryPage = book.categories().size() > 0
                    ? 12 - 3 - MathHelper.ceilDiv(book.categories().size() - 1, 4) * 2
                    : 12;

            var orphanedEntries = this.buildEntryIndex(book.orphanedEntries(), entriesOnCategoryPage);
            indexPage.child(orphanedEntries.remove(0));
            this.pages.addAll(orphanedEntries);
        }

        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean canMerge(PageSupplier other) {
            return other instanceof LandingPageSupplier;
        }

        @Override
        public Function<BookScreen, @Nullable PageSupplier> replicator() {
            return LandingPageSupplier::new;
        }
    }

    public static class IndexPageSupplier extends PageSupplier {

        public IndexPageSupplier(BookScreen context) {
            super(context);

            var entries = this.buildEntryIndex(this.context.book.entries(), 10);
            this.pages.add(this.pageWithHeader(Text.translatable("text.lavender.index_category.title")).child(entries.remove(0)));
            this.pages.addAll(entries);
        }

        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        boolean canMerge(PageSupplier other) {
            return other instanceof IndexPageSupplier;
        }

        @Override
        Function<BookScreen, @Nullable PageSupplier> replicator() {
            return IndexPageSupplier::new;
        }
    }

    public static class EditorPageSupplier extends PageSupplier {

        private static String editorTextCache = "";

        protected EditorPageSupplier(BookScreen context) {
            super(context);
            var editorComponent = Components.textArea(Sizing.fill(100), Sizing.fill(100)).<TextAreaComponent>configure(editor -> {
                editor.onChanged().subscribe(value -> {
                    editorTextCache = value;

                    if (this.pages.size() > 1) {
                        this.pages.subList(1, this.pages.size()).clear();
                    }

                    var compiled = this.context.processor.process(editorTextCache);
                    boolean firstPage = true;

                    while (!compiled.children().isEmpty()) {
                        var component = compiled.children().get(0);
                        compiled.removeChild(component);

                        if (firstPage) {
                            firstPage = false;
                            this.pages.add(this.pageWithHeader(Text.literal("Title Here")).child(component));
                        } else {
                            this.pages.add(component);
                        }
                    }

                    this.context.rebuildContent(null);
                });
            });

            this.pages.add(editorComponent);
            editorComponent.text(editorTextCache);
        }

        @Override
        public Component getPageContent(int pageIndex) {
            return pageIndex % 2 == 0
                    ? super.getPageContent(0)
                    : super.getPageContent(pageIndex / 2 + 1);
        }

        @Override
        public int pageCount() {
            return Math.max(1, (super.pageCount() - 1) * 2);
        }

        @Override
        boolean canMerge(PageSupplier other) {
            return other instanceof EditorPageSupplier;
        }

        @Override
        Function<BookScreen, @Nullable PageSupplier> replicator() {
            return EditorPageSupplier::new;
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

            var landingPage = this.pageWithHeader(Text.literal(category.title())).child(landingPageContent);
            this.pages.add(landingPage);

            // --- entry index ---

            var entries = this.context.book.entriesByCategory(this.category);
            if (entries != null) {
                var indexPages = this.buildEntryIndex(entries);
                for (int i = 0; i < indexPages.size(); i++) {
                    this.pages.add(i == 0 ? this.pageWithHeader(Text.translatable("text.lavender.index")).child(indexPages.get(0)) : indexPages.get(i));
                }

                if (this.context.book.displayCompletion()) {
                    var completionBar = this.context.template(
                            FlowLayout.class,
                            "completion-bar",
                            Map.of("progress", String.valueOf((int) (100 * (this.countVisibleEntries(entries, this.context.client.player) / (float) entries.size()))))
                    );

                    completionBar.childById(LabelComponent.class, "completion-label")
                            .text(Text.translatable("text.lavender.book.unlock_progress", this.countVisibleEntries(entries, this.context.client.player), entries.size()));

                    landingPage.child(completionBar);
                }
            }
        }

        protected int countVisibleEntries(Collection<Entry> entries, ClientPlayerEntity player) {
            int visible = 0;
            for (var entry : entries) {
                if (!entry.canPlayerView(player)) continue;
                visible++;
            }

            return visible;
        }

        @Override
        public boolean searchable() {
            return true;
        }

        @Override
        public boolean canMerge(PageSupplier other) {
            return other instanceof CategoryPageSupplier supplier && supplier.category.id().equals(this.category.id());
        }

        @Override
        public Function<BookScreen, @Nullable PageSupplier> replicator() {
            var categoryId = this.category.id();
            return context -> {
                var category = context.book.categoryById(categoryId);
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
                    this.pages.add(this.pageWithHeader(Text.literal(entry.title())).child(component));
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
            var entryId = this.entry.id();
            return context -> {
                var entry = context.book.entryById(entryId);
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

