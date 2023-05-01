package io.wispforest.lavender.parsing;

import io.wispforest.lavender.parsing.Lexer.*;
import io.wispforest.lavender.parsing.compiler.MarkdownCompiler;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Parser {

    public static Node parse(List<Token> tokens) {
        var tokenNibbler = new ListNibbler<>(tokens);

        var node = Node.empty();
        while (tokenNibbler.hasElements()) {
            node.addChild(parseNode(tokenNibbler));
        }

        return node;
    }

    private static @NotNull Node parseNode(ListNibbler<Token> tokens) {
        var token = tokens.nibble();
        if (token instanceof TextToken text) {
            var content = text.content();
            if (tokens.peek(-2) == null || tokens.peek(-2) instanceof NewlineToken) {
                content = content.stripLeading();
            }

            if (tokens.peek() instanceof NewlineToken newline && !newline.isBoundary()) {
                content = content.stripTrailing();
            }

            return new TextNode(content);
        }

        if (token instanceof StarToken left && left.rightAdjacent) {
            int pointer = tokens.pointer();
            var content = parseUntil(tokens, StarToken.class);

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
        }

        if (token instanceof TildeToken left1 && tokens.peek() instanceof TildeToken left2) {
            tokens.nibble();

            int pointer = tokens.pointer();
            var content = parseUntil(tokens, TildeToken.class);

            if (tokens.peek() instanceof TildeToken && tokens.peek(1) instanceof TildeToken) {
                tokens.skip(2);
                return new FormattingNode(style -> style.withStrikethrough(true)).addChild(content);
            } else {
                tokens.setPointer(pointer);
                return new TextNode(left1.content() + left2.content());
            }
        }

        if (token instanceof UnderscoreToken left1 && tokens.peek() instanceof UnderscoreToken left2) {
            tokens.nibble();

            int pointer = tokens.pointer();
            var content = parseUntil(tokens, UnderscoreToken.class);

            if (tokens.peek() instanceof UnderscoreToken && tokens.peek(1) instanceof UnderscoreToken) {
                tokens.skip(2);
                return new FormattingNode(style -> style.withUnderline(true)).addChild(content);
            } else {
                tokens.setPointer(pointer);
                return new TextNode(left1.content() + left2.content());
            }
        }

        if (token instanceof OpenLinkToken left) {
            int pointer = tokens.pointer();
            var content = parseUntil(tokens, CloseLinkToken.class);

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
        }

        if (token instanceof OpenColorToken left) {
            int pointer = tokens.pointer();
            var content = parseUntil(tokens, CloseColorToken.class);

            if (tokens.peek() instanceof CloseColorToken) {
                tokens.nibble();
                return new FormattingNode(left.style).addChild(content);
            } else {
                tokens.setPointer(pointer);
                return new TextNode(left.content());
            }
        }

        if (token instanceof ImageToken image) {
            return new ImageNode(image.identifier, image.description);
        }

        if (token instanceof QuotationToken current && (tokens.peek(-2) == null || tokens.peek(-2) instanceof NewlineToken)) {
            return new QuotationNode().addChild(parseUntil(tokens, $ -> $.isBoundary() && (!($ instanceof QuotationToken) || ((QuotationToken) $).depth < current.depth), $ -> $ instanceof QuotationToken quote && quote.depth == current.depth));
        }

        if (token instanceof HorizontalRuleToken) {
            return new HorizontalRuleNode();
        }

        if (token != null) {
            return new TextNode(token.content());
        }

        return Node.empty();
    }

    private static Node parseUntil(ListNibbler<Token> tokens, Class<? extends Token> until) {
        return parseUntil(tokens, token -> token.isBoundary() || until.isInstance(token), token -> false);
    }

    private static Node parseUntil(ListNibbler<Token> tokens, Predicate<Token> until, Predicate<Token> skip) {
        var node = parseNode(tokens);
        while (tokens.hasElements()) {
            var next = tokens.peek();

            if (skip.test(next)) {
                tokens.nibble();
                continue;
            }

            if (until.test(next)) break;

            node.addChild(parseNode(tokens));
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

    public static class ImageNode extends Node {

        private final String identifier, description;

        public ImageNode(String identifier, String description) {
            this.identifier = identifier;
            this.description = description;
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitImage(new Identifier(this.identifier), this.description);
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
