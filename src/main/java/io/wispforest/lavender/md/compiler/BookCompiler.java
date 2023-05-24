package io.wispforest.lavender.md.compiler;

import com.google.common.primitives.Ints;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Entry;
import io.wispforest.lavender.client.BookScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIModel;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import io.wispforest.owo.ui.util.Drawer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BookCompiler extends OwoUICompiler {

    private static final Style UNICODE_FONT_STYLE = Style.EMPTY.withFont(MinecraftClient.UNICODE_FONT_ID);

    private final FlowLayout resultContainer = Containers.verticalFlow(Sizing.content(), Sizing.content());
    private final ComponentSource bookComponentSource;

    public BookCompiler(ComponentSource bookComponentSource) {
        this.push(Containers.verticalFlow(Sizing.content(), Sizing.content()));
        this.bookComponentSource = bookComponentSource;
    }

    @Override
    protected LabelComponent makeLabel(MutableText text) {
        return new BookLabelComponent(text.styled(style -> style.withParent(UNICODE_FONT_STYLE))).color(Color.BLACK).lineHeight(7);
    }

    @Override
    public void visitHorizontalRule() {
        this.append(this.bookComponentSource.builtinTemplate(Component.class, "horizontal-rule"));
    }

    public void visitPageBreak() {
        this.resultContainer.child(components.peek());
        this.pop();
        this.push(Containers.verticalFlow(Sizing.content(), Sizing.content()));
    }

    @Override
    public ParentComponent compile() {
        this.pop();
        return super.compile();
    }

    @Override
    public String name() {
        return "lavender_builtin_book";
    }

    public static class BookLabelComponent extends LabelComponent {

        private @Nullable BookScreen owner;

        protected BookLabelComponent(Text text) {
            super(text);
            this.margins(Insets.horizontal(1));
            this.textClickHandler(style -> {
                if (style == null || this.owner == null) return false;

                var clickEvent = style.getClickEvent();
                if (clickEvent != null && clickEvent.getAction() == ClickEvent.Action.OPEN_URL && clickEvent.getValue().startsWith("^")) {
                    var linkTarget = this.resolveLinkTarget(clickEvent.getValue());
                    if (linkTarget != null && linkTarget.entry != null) {
                        this.owner.navPush(new BookScreen.NavFrame(new BookScreen.EntryPageSupplier(this.owner, linkTarget.entry), linkTarget.page));
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    return Drawer.utilityScreen().handleTextClick(style);
                }
            });
        }

        public void setOwner(@NotNull BookScreen screen) {
            this.owner = screen;
        }

        protected @Nullable LinkTarget resolveLinkTarget(String link) {
            if (this.owner == null) return null;

            var rawLinkText = link.substring(1);
            int targetPage = 0;

            int pageSeparatorIndex = rawLinkText.indexOf('#');
            if (pageSeparatorIndex > 0) {
                var parsed = Ints.tryParse(rawLinkText.substring(pageSeparatorIndex + 1));
                if (parsed == null) return null;

                targetPage = Math.max(0, (parsed - 1) / 2 * 2);
                rawLinkText = rawLinkText.substring(0, pageSeparatorIndex);
            }

            var entryId = Identifier.tryParse(rawLinkText);
            if (entryId == null) return null;

            var entry = this.owner.book.entryById(entryId);
            if (entry == null) return null;

            return new LinkTarget(entry.canPlayerView(MinecraftClient.getInstance().player) ? entry : null, targetPage);
        }

        @Override
        protected Style styleAt(int mouseX, int mouseY) {
            var style = super.styleAt(mouseX, mouseY);
            if (style == null) return null;

            var event = style.getHoverEvent();
            if (this.owner != null && event != null && event.getAction() == HoverEvent.Action.SHOW_TEXT && event.getValue(HoverEvent.Action.SHOW_TEXT).getString().startsWith("^")) {
                var rawLink = event.getValue(HoverEvent.Action.SHOW_TEXT).getString();
                var linkTarget = this.resolveLinkTarget(rawLink);

                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, linkTarget != null
                        ? linkTarget.entry != null ? Text.literal(linkTarget.entry.title()) : Text.translatable("text.lavender.locked_internal_link")
                        : Text.translatable("text.lavender.invalid_internal_link", rawLink)
                ));
            }

            return style;
        }

        private record LinkTarget(Entry entry, int page) {}
    }

    @FunctionalInterface
    public interface ComponentSource {
        <C extends Component> C template(UIModel model, Class<C> expectedComponentClass, String name, Map<String, String> params);

        default <C extends Component> C builtinTemplate(Class<C> expectedComponentClass, String name, Map<String, String> params) {
            return this.template(UIModelLoader.get(Lavender.id("book_components")), expectedComponentClass, name, params);
        }

        default <C extends Component> C builtinTemplate(Class<C> expectedComponentClass, String name) {
            return this.builtinTemplate(expectedComponentClass, name, Map.of());
        }

        default <C extends Component> C template(UIModel model, Class<C> expectedComponentClass, String name) {
            return this.template(model, expectedComponentClass, name, Map.of());
        }
    }
}
