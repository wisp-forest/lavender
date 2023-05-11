package io.wispforest.lavender.md.compiler;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.Entry;
import io.wispforest.lavender.client.BookScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import io.wispforest.owo.ui.util.Drawer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BookCompiler extends OwoUICompiler {

    private static final Style UNICODE_FONT_STYLE = Style.EMPTY.withFont(MinecraftClient.UNICODE_FONT_ID);

    private final FlowLayout resultContainer = Containers.verticalFlow(Sizing.content(), Sizing.content());

    public BookCompiler() {
        this.push(Containers.verticalFlow(Sizing.content(), Sizing.content()));
    }

    @Override
    protected LabelComponent makeLabel(MutableText text) {
        return new BookLabelComponent(text.styled(style -> style.withParent(UNICODE_FONT_STYLE))).color(Color.BLACK).lineHeight(7);
    }

    @Override
    public void visitHorizontalRule() {
        this.append(UIModelLoader.get(Lavender.id("book_components")).expandTemplate(Component.class, "horizontal-rule", Map.of()));
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
            this.textClickHandler(style -> {
                if (style == null || this.owner == null) return false;

                var clickEvent = style.getClickEvent();
                if (clickEvent != null && clickEvent.getAction() == ClickEvent.Action.OPEN_URL && clickEvent.getValue().startsWith("^")) {
                    var linkTarget = this.resolveLinkTarget(clickEvent.getValue());
                    if (linkTarget != null) {
                        this.owner.navPush(this.owner.new EntryPageSupplier(linkTarget));
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

        protected @Nullable Entry resolveLinkTarget(String link) {
            if (this.owner == null) return null;

            var entryId = Identifier.tryParse(link.substring(1));
            if (entryId == null) return null;

            return this.owner.book.entryById(entryId);
        }

        @Override
        protected Style styleAt(int mouseX, int mouseY) {
            var style = super.styleAt(mouseX, mouseY);
            if (style == null) return null;

            var event = style.getHoverEvent();
            if (this.owner != null && event != null && event.getAction() == HoverEvent.Action.SHOW_TEXT && event.getValue(HoverEvent.Action.SHOW_TEXT).getString().startsWith("^")) {
                var rawLink = event.getValue(HoverEvent.Action.SHOW_TEXT).getString();
                var linkTarget = this.resolveLinkTarget(rawLink);

                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, linkTarget != null
                        ? Text.literal(linkTarget.title())
                        : Text.literal("invalid internal link: " + rawLink).formatted(Formatting.RED)
                ));
            }

            return style;
        }
    }
}
