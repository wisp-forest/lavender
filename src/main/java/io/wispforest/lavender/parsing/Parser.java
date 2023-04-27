package io.wispforest.lavender.parsing;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.StringReader;
import it.unimi.dsi.fastutil.chars.Char2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.function.CharPredicate;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class Parser {

    private static final Map<String, Formatting> FORMATTING_COLORS = Stream.of(Formatting.values())
            .filter(Formatting::isColor)
            .collect(ImmutableMap.toImmutableMap(formatting -> formatting.getName().toLowerCase(Locale.ROOT), Function.identity()));

    private static final Char2ObjectMap<BiFunction<StringReader, List<Token>, Boolean>> LEX_FUNCTIONS = new Char2ObjectLinkedOpenHashMap<>();

    static {
        LEX_FUNCTIONS.put('[', LinkToken::lexOpen);
        LEX_FUNCTIONS.put(']', LinkToken::lexClose);
        LEX_FUNCTIONS.put('*', StarToken::lex);
        LEX_FUNCTIONS.put('{', ColorToken::lex);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Text parse(String input) {
        var tokens = new ArrayList<Token>();
        var reader = new StringReader(input);

        while (reader.canRead()) {
            char current = reader.peek();
            if (LEX_FUNCTIONS.containsKey(current)) {
                int cursorBefore = reader.getCursor();
                if (!LEX_FUNCTIONS.get(current).apply(reader, tokens)) {
                    tokens.add(new TextToken(reader.getRead().substring(cursorBefore)));
                }
            } else {
                tokens.add(new TextToken(readTextUntil(reader, LEX_FUNCTIONS.keySet()::contains)));
            }
        }

        var spanOperators = new HashMap<Class<?>, List<SpanOperator>>();
        for (var token : tokens) {
            if (!(token instanceof SpanOperator<?> operator)) continue;
            spanOperators.computeIfAbsent(operator.getClass(), $ -> new ArrayList<>()).add(operator);
        }

        spanOperators.forEach((operatorClass, operators) -> {
            if (operators.size() % 2 != 0) {
                var lastOperator = operators.remove(operators.size() - 1);
                tokens.set(tokens.indexOf(lastOperator), new TextToken(lastOperator.asPlainText()));
            }

            // TODO operators isolated by nature of not validating must also be correctly unparsed
            for (int i = operators.size() - 1; i >= 1; i -= 1) {
                var left = operators.get(i - 1);
                var right = operators.get(i);

                if (left.validate(left, right)) {
                    left.processPair(left, right);
                } else {
                    tokens.set(tokens.lastIndexOf(right), new TextToken(right.asPlainText()));
                }
            }
        });

        var output = new TextBuilder();
        tokens.forEach(token -> token.apply(output));
        return output.build();
    }

    private static String readTextUntil(StringReader reader, CharPredicate until) {
        var text = new StringBuilder();
        while (reader.canRead() && !until.test(reader.peek())) {
            text.append(reader.read());
        }

        return text.toString();
    }

    private interface Token {
        void apply(TextBuilder builder);
    }

    // --- Tokens with lexing & parsing implementation ---
    // TODO token should be an abstract class and store original text

    private interface SpanOperator<S extends SpanOperator<S>> extends Token {
        String asPlainText();

        default boolean validate(S left, S right) {
            return true;
        }

        default void processPair(S left, S right) {}
    }

    private record TextToken(String content) implements Token {
        @Override
        public void apply(TextBuilder builder) {
            builder.append(Text.literal(this.content));
        }
    }

    private record StarToken(int starCount, Predicate<Style> predicate,
                             BiFunction<Style, Boolean, Style> applyFunction) implements SpanOperator<StarToken> {

        private static boolean lex(StringReader reader, List<Token> tokens) {
            int starCount = readTextUntil(reader, c -> c != '*').length();

            if (starCount > 3 || !((reader.canRead() && reader.peek() != ' ') || (reader.getCursor() - starCount - 1 >= 0 && reader.peek(-starCount - 1) != ' '))) {
                return false;
            } else {
                switch (starCount) {
                    case 1 -> tokens.add(new StarToken(starCount, Style::isItalic, Style::withItalic));
                    case 2 -> tokens.add(new StarToken(starCount, Style::isBold, Style::withBold));
                    case 3 ->
                            tokens.add(new StarToken(starCount, style -> style.isBold() && style.isItalic(), (style, value) -> style.withBold(value).withItalic(value)));
                }

                return true;
            }
        }

        @Override
        public boolean validate(StarToken left, StarToken right) {
            return left.starCount == right.starCount;
        }

        @Override
        public void apply(TextBuilder builder) {
            if (!this.predicate.test(builder.style())) {
                builder.pushStyle(style -> this.applyFunction.apply(style, true));
            } else {
                builder.popStyle();
            }
        }

        @Override
        public String asPlainText() {
            return "*".repeat(this.starCount);
        }
    }

    private static class LinkToken implements SpanOperator<LinkToken> {

        private @Nullable String link;

        public LinkToken(@Nullable String link) {
            this.link = link;
        }

        private static boolean lexOpen(StringReader reader, List<Token> tokens) {
            reader.skip();
            tokens.add(new LinkToken(null));

            return true;
        }

        private static boolean lexClose(StringReader reader, List<Token> tokens) {
            reader.skip();

            if (!reader.canRead() || reader.peek() != '(') {
                return false;
            }

            reader.skip();
            tokens.add(new LinkToken(readTextUntil(reader, c -> c == ')')));
            reader.skip();

            return true;
        }

        @Override
        public void apply(TextBuilder builder) {
            if (this.link != null) {
                builder.pushStyle(style -> style.withClickEvent(
                        new ClickEvent(ClickEvent.Action.OPEN_URL, this.link)
                ).withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(this.link))
                ).withColor(Formatting.BLUE));
            } else {
                builder.popStyle();
            }
        }

        @Override
        public void processPair(LinkToken left, LinkToken right) {
            left.link = right.link;
            right.link = null;
        }

        @Override
        public String asPlainText() {
            return this.link != null
                    ? "](" + this.link + ")"
                    : "[";
        }
    }

    private record ColorToken(String content,
                              @Nullable UnaryOperator<Style> applyFunction) implements SpanOperator<ColorToken> {

        private static boolean lex(StringReader reader, List<Token> tokens) {
            reader.skip();
            if (!reader.canRead()) return false;

            if (reader.peek() == '}') {
                reader.skip();
                tokens.add(new ColorToken(null, null));
            } else {
                if (reader.peek() == '#') {
                    reader.skip();

                    var color = readTextUntil(reader, c -> c == '}');
                    reader.skip();

                    if (!color.matches("[0-9a-fA-F]{6}")) return false;
                    tokens.add(new ColorToken(color, style -> style.withColor(Integer.parseInt(color, 16))));
                } else {
                    var color = readTextUntil(reader, c -> c == '}');
                    reader.skip();

                    if (!FORMATTING_COLORS.containsKey(color)) return false;
                    tokens.add(new ColorToken(color, style -> style.withFormatting(FORMATTING_COLORS.get(color))));
                }
            }

            return true;
        }

        @Override
        public boolean validate(ColorToken left, ColorToken right) {
            return left.applyFunction != null && left.content != null && right.applyFunction == null && right.content == null;
        }

        @Override
        public void apply(TextBuilder builder) {
            if (this.applyFunction != null) {
                builder.pushStyle(this.applyFunction);
            } else {
                builder.popStyle();
            }
        }

        @Override
        public String asPlainText() {
            return this.applyFunction != null
                    ? "{" + this.content + "}"
                    : "{}";
        }
    }

    private static class TextBuilder {

        private final Deque<MutableText> text;

        private TextBuilder() {
            this.text = new ArrayDeque<>();
            this.text.push(Text.empty());
        }

        public void append(Text text) {
            this.text.peek().append(text);
        }

        public void pushStyle(UnaryOperator<Style> style) {
            var top = this.text.peek();
            var newTop = Text.empty().setStyle(style.apply(top.getStyle()));
            top.append(newTop);
            this.text.push(newTop);
        }

        public Style style() {
            return this.text.peek().getStyle();
        }

        public void popStyle() {
            this.text.pop();
        }

        public Text build() {
            var result = this.text.getLast().copy();
            result.setStyle(result.getStyle().withFont(MinecraftClient.UNICODE_FONT_ID));

            return result;
        }
    }

    private static <T> int lastIndexOf(List<T> list, Predicate<T> predicate) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (predicate.test(list.get(i))) return i;
        }

        throw new NoSuchElementException();
    }
}
