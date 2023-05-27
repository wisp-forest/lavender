package io.wispforest.lavender.md;

import io.wispforest.lavender.md.Lexer.*;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Parser implements MarkdownExtension.NodeRegistrar {

    private final Map<BiFunction<Token, ListNibbler<Token>, ?>, ParseFunction<?>> parseFunctions = new HashMap<>();

    public Parser() {
        this.registerDefaultNodes();
    }

    @Override
    public <T extends Token> void registerNode(ParseFunction<T> parser, BiFunction<Token, ListNibbler<Token>, @Nullable T> trigger) {
        this.parseFunctions.put(trigger, parser);
    }

    private void registerDefaultNodes() {
        this.registerNode((parser, text, tokens) -> {
            var content = text.content();
            if (tokens.peek(-2) == null || tokens.peek(-2) instanceof NewlineToken) {
                content = content.stripLeading();
            }

            if (tokens.peek() instanceof NewlineToken newline && !newline.isBoundary()) {
                content = content.stripTrailing();
            }

            return new TextNode(content);
        }, (token, tokens) -> token instanceof TextToken text ? text : null);

        this.registerNode((parser, left, tokens) -> {
            int pointer = tokens.pointer();
            var content = parser.parseUntil(tokens, StarToken.class);

            if (tokens.peek() instanceof StarToken right && right.leftAdjacent) {
                tokens.nibble();

                if (content instanceof StarNode star) {
                    if (star.canIncrementStarCount()) {
                        return star.incrementStarCount();
                    } else {
                        return new TextNode("*").addChild(content).addChild(new TextNode("*"));
                    }
                } else {
                    return new StarNode().addChild(content);
                }
            } else {
                tokens.setPointer(pointer);
                return new TextNode(left.content());
            }
        }, (token, tokens) -> token instanceof StarToken star && star.rightAdjacent ? star : null);

        this.registerNode((parser, left, tokens) -> {
            int pointer = tokens.pointer();
            var content = parser.parseUntil(tokens, CloseLinkToken.class);

            if (tokens.peek() instanceof CloseLinkToken right) {
                tokens.nibble();
                return new FormattingNode(style -> style.withClickEvent(
                        new ClickEvent(ClickEvent.Action.OPEN_URL, right.link)
                ).withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(right.link))
                ).withColor(Formatting.BLUE)).addChild(content);
            } else {
                tokens.setPointer(pointer);
                return new TextNode(left.content());
            }
        }, (token, tokens) -> token instanceof OpenLinkToken link ? link : null);

        this.registerNode((parser, left, tokens) -> {
            int pointer = tokens.pointer();
            var content = parser.parseUntil(tokens, CloseColorToken.class);

            if (tokens.peek() instanceof CloseColorToken) {
                tokens.nibble();
                return new FormattingNode(left.style).addChild(content);
            } else {
                tokens.setPointer(pointer);
                return new TextNode(left.content());
            }
        }, (token, tokens) -> token instanceof OpenColorToken color ? color : null);

        this.registerNode(
                (parser, current, tokens) -> new ListNode(current.ordinal).addChild(parser.parseUntil(tokens, $ -> $.isBoundary() && !($ instanceof ListToken list && list.depth > current.depth), $ -> false)),
                (token, tokens) -> token instanceof ListToken list ? list : null
        );

        this.registerNode(
                (parser, image, tokens) -> new ImageNode(image.identifier, image.description, image.fit),
                (token, tokens) -> token instanceof ImageToken image ? image : null
        );

        this.registerNode(
                (parser, current, tokens) -> new QuotationNode().addChild(parser.parseUntil(tokens, $ -> $.isBoundary() && (!($ instanceof QuotationToken) || ((QuotationToken) $).depth < current.depth), $ -> $ instanceof QuotationToken quote && quote.depth == current.depth)),
                (token, tokens) -> token instanceof QuotationToken current && (tokens.peek(-2) == null || tokens.peek(-2) instanceof NewlineToken) ? current : null
        );

        this.registerNode(
                (parser, rule, tokens) -> new HorizontalRuleNode(),
                (token, tokens) -> token instanceof HorizontalRuleToken rule ? rule : null
        );

        this.registerDoubleFormattingToken(TildeToken.class, style -> style.withStrikethrough(true));
        this.registerDoubleFormattingToken(UnderscoreToken.class, style -> style.withUnderline(true));
    }

    private void registerDoubleFormattingToken(Class<? extends Token> tokenClass, UnaryOperator<Style> formatting) {
        this.registerNode((parser, left1, tokens) -> {
            var left2 = tokens.nibble();

            int pointer = tokens.pointer();
            var content = parseUntil(tokens, tokenClass);

            if (tokenClass.isInstance(tokens.peek()) && tokenClass.isInstance(tokens.peek(1))) {
                tokens.skip(2);
                return new FormattingNode(formatting).addChild(content);
            } else {
                tokens.setPointer(pointer);
                return new TextNode(left1.content() + left2.content());
            }
        }, (token, tokens) -> tokenClass.isInstance(token) && tokenClass.isInstance(tokens.peek()) ? tokenClass.cast(token) : null);
    }

    @FunctionalInterface
    public interface ParseFunction<T extends Token> {
        Node parse(Parser parser, T trigger, ListNibbler<Token> tokens);
    }

    public Node parse(List<Token> tokens) {
        var tokenNibbler = new ListNibbler<>(tokens);

        var node = Node.empty();
        while (tokenNibbler.hasElements()) {
            node.addChild(parseNode(tokenNibbler));
        }

        return node;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @NotNull Node parseNode(ListNibbler<Token> tokens) {
        var token = tokens.nibble();

        for (var function : this.parseFunctions.entrySet()) {
            var first = function.getKey().apply(token, tokens);
            if (first == null) continue;

            return ((ParseFunction) function.getValue()).parse(this, token, tokens);
        }

        if (token != null) {
            return new TextNode(token.content());
        }

        return Node.empty();
    }

    private Node parseUntil(ListNibbler<Token> tokens, Class<? extends Token> until) {
        return this.parseUntil(tokens, token -> token.isBoundary() || until.isInstance(token), token -> false);
    }

    private Node parseUntil(ListNibbler<Token> tokens, Predicate<Token> until, Predicate<Token> skip) {
        var node = this.parseNode(tokens);
        while (tokens.hasElements()) {
            var next = tokens.peek();

            if (skip.test(next)) {
                tokens.nibble();
                continue;
            }

            if (until.test(next)) break;

            node.addChild(this.parseNode(tokens));
        }

        return node;
    }

    public abstract static class Node {

        protected final List<Node> children = new ArrayList<>();

        public Node addChild(Node child) {
            this.children.add(child);
            return this;
        }

        public void visit(MarkdownCompiler<?> compiler) {
            this.visitStart(compiler);
            for (var child : this.children) {
                child.visit(compiler);
            }
            this.visitEnd(compiler);
        }

        protected abstract void visitStart(MarkdownCompiler<?> compiler);

        protected abstract void visitEnd(MarkdownCompiler<?> compiler);

        public static Node empty() {
            return new Node() {
                @Override
                protected void visitStart(MarkdownCompiler<?> compiler) {}

                @Override
                protected void visitEnd(MarkdownCompiler<?> compiler) {}
            };
        }
    }

    public static final class TextNode extends Node {
        private final String content;

        public TextNode(String content) {
            this.content = content;
        }

        @Override
        public void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitText(this.content);
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }

    public static class FormattingNode extends Node {
        private final UnaryOperator<Style> formatting;

        public FormattingNode(UnaryOperator<Style> formatting) {
            this.formatting = formatting;
        }

        @Override
        public void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitStyle(this::applyStyle);
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {
            compiler.visitStyleEnd();
        }

        protected Style applyStyle(Style style) {
            return this.formatting.apply(style);
        }
    }

    public static class StarNode extends FormattingNode {

        private int starCount = 1;

        public StarNode() {
            super(style -> style);
        }

        @Override
        protected Style applyStyle(Style style) {
            return style.withItalic(this.starCount % 2 == 1 ? true : null).withBold(this.starCount > 1 ? true : null);
        }

        public StarNode incrementStarCount() {
            this.starCount++;
            return this;
        }

        public boolean canIncrementStarCount() {
            return this.starCount < 3;
        }
    }

    public static class QuotationNode extends Node {
        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitQuotation();
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {
            compiler.visitQuotationEnd();
        }
    }

    public static class ListNode extends Node {

        private final OptionalInt ordinal;

        public ListNode(OptionalInt ordinal) {
            this.ordinal = ordinal;
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitListItem(this.ordinal);
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {
            compiler.visitListItemEnd();
        }
    }

    public static class ImageNode extends Node {

        private final String identifier, description;
        private final boolean fit;

        public ImageNode(String identifier, String description, boolean fit) {
            this.identifier = identifier;
            this.description = description;
            this.fit = fit;
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitImage(new Identifier(this.identifier), this.description, this.fit);
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }

    public static class HorizontalRuleNode extends Node {
        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitHorizontalRule();
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }

}
