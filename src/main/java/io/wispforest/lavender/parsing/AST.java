package io.wispforest.lavender.parsing;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.UnaryOperator;

public class AST {

    public static List<Node> parse(Queue<Lexer.Token> tokens) {
        var nodes = new ArrayList<Node>();

        while (!tokens.isEmpty()) {
            nodes.add(parseNode(tokens));
        }

        return nodes;
    }

    private static @NotNull Node parseNode(Queue<Lexer.Token> tokens) {
        var token = tokens.poll();
        if (token instanceof Lexer.TextToken text) return new TextNode(text.content());
        if (token instanceof Lexer.StarToken) {
            var tokensBefore = new ArrayDeque<>(tokens);
            var content = parseUntil(tokens, Lexer.StarToken.class);

            if (tokens.peek() instanceof Lexer.StarToken) {
                tokens.poll();

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
                tokens.clear();
                tokens.addAll(tokensBefore);
                return new TextNode("*");
            }
        }

        if (token instanceof Lexer.OpenLinkToken left) {
            var tokensBefore = new ArrayDeque<>(tokens);
            var content = parseUntil(tokens, Lexer.CloseLinkToken.class);

            if (tokens.peek() instanceof Lexer.CloseLinkToken right) {
                tokens.poll();
                return new FormattingNode(style -> style.withClickEvent(
                        new ClickEvent(ClickEvent.Action.OPEN_URL, right.link)
                ).withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(right.link))
                ).withColor(Formatting.BLUE)).addChild(content);
            } else {
                tokens.clear();
                tokens.addAll(tokensBefore);
                return new TextNode(left.content());
            }
        }

        if (token instanceof Lexer.OpenColorToken left) {
            var tokensBefore = new ArrayDeque<>(tokens);
            var content = parseUntil(tokens, Lexer.CloseColorToken.class);

            if (tokens.peek() instanceof Lexer.CloseColorToken) {
                tokens.poll();
                return new FormattingNode(left.style).addChild(content);
            } else {
                tokens.clear();
                tokens.addAll(tokensBefore);
                return new TextNode(left.content());
            }
        }

        if (token != null) {
            return new TextNode(token.content());
        }

        return Node.EMPTY;
    }

    private static Node parseUntil(Queue<Lexer.Token> tokens, Class<? extends Lexer.Token> until) {
        var node = parseNode(tokens);
        while (!tokens.isEmpty() && !until.isInstance(tokens.peek())) node.addChild(parseNode(tokens));

        return node;
    }

    public abstract static class Node {

        public static final Node EMPTY = new Node() {
            @Override
            protected void applyOpen(TextBuilder builder) {}

            @Override
            protected void applyClose(TextBuilder builder) {}
        };

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
