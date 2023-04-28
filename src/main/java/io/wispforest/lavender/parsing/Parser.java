package io.wispforest.lavender.parsing;

import io.wispforest.lavender.parsing.Lexer.*;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class Parser {

    public static Text markdownToText(String markdown) {
        var result = new TextBuilder();
        parse(Lexer.lex(markdown)).forEach(node -> node.apply(result));

        return result.build();
    }

    public static List<Node> parse(List<Token> tokens) {
        var nodes = new ArrayList<Node>();
        var tokenNibbler = new ListNibbler<>(tokens);

        while (tokenNibbler.hasElements()) {
            nodes.add(parseNode(tokenNibbler));
        }

        return nodes;
    }

    private static @NotNull Node parseNode(ListNibbler<Token> tokens) {
        var token = tokens.nibble();
        if (token instanceof TextToken text) return new TextNode(text.content());
        if (token instanceof StarToken left) {
            int pointer = tokens.pointer();
            var content = parseUntil(tokens, StarToken.class);

            if (tokens.peek() instanceof StarToken) {
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

        if (token instanceof QuotationToken && (tokens.peek(-2) == null || tokens.peek(-2) instanceof NewlineToken)) {
            var content = parseUntil(tokens, $ -> $ instanceof NewlineToken newline && newline.isBoundary());

            var node = new FormattingNode(style -> style.withFormatting(Formatting.OBFUSCATED)).addChild(content);
            if (tokens.nibble() instanceof NewlineToken newline) node.addChild(new TextNode(newline.content()));

            return node;
        }

        if (token != null) {
            return new TextNode(token.content());
        }

        return Node.empty();
    }

    private static Node parseUntil(ListNibbler<Token> tokens, Class<? extends Token> until) {
        return parseUntil(tokens, until::isInstance);
    }

    private static Node parseUntil(ListNibbler<Token> tokens, Predicate<Token> until) {
        var node = parseNode(tokens);
        while (tokens.hasElements()) {
            var next = tokens.peek();
            if (next.isBoundary() || until.test(next)) break;

            node.addChild(parseNode(tokens));
        }

        return node;
    }

    public abstract static class Node {

        private final List<Node> children = new ArrayList<>();

        public Node addChild(Node child) {
            this.children.add(child);
            return this;
        }

        public void apply(TextBuilder builder) {
            this.applyOpen(builder);
            for (var child : this.children) {
                child.apply(builder);
            }
            this.applyClose(builder);
        }

        protected abstract void applyOpen(TextBuilder builder);

        protected abstract void applyClose(TextBuilder builder);

        public static Node empty() {
            return new Node() {
                @Override
                protected void applyOpen(TextBuilder builder) {}

                @Override
                protected void applyClose(TextBuilder builder) {}
            };
        }
    }

    public static final class TextNode extends Node {
        private final String content;

        public TextNode(String content) {
            this.content = content;
        }

        @Override
        public void applyOpen(TextBuilder builder) {
            builder.append(Text.literal(this.content));
        }

        @Override
        protected void applyClose(TextBuilder builder) {}
    }

    public static class FormattingNode extends Node {
        private final UnaryOperator<Style> formatting;

        public FormattingNode(UnaryOperator<Style> formatting) {
            this.formatting = formatting;
        }

        @Override
        public void applyOpen(TextBuilder builder) {
            builder.pushStyle(this::applyStyle);
        }

        @Override
        protected void applyClose(TextBuilder builder) {
            builder.popStyle();
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

}
