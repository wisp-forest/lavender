package io.wispforest.lavender.parsing;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.StringReader;
import it.unimi.dsi.fastutil.chars.Char2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.function.CharPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class Lexer {

    private static final Map<String, Formatting> FORMATTING_COLORS = Stream.of(Formatting.values())
            .filter(Formatting::isColor)
            .collect(ImmutableMap.toImmutableMap(formatting -> formatting.getName().toLowerCase(Locale.ROOT), Function.identity()));

    private static final Char2ObjectMap<BiFunction<StringReader, Queue<Token>, Boolean>> LEX_FUNCTIONS = new Char2ObjectLinkedOpenHashMap<>();

    static {
        LEX_FUNCTIONS.put(']', CloseLinkToken::lex);
        LEX_FUNCTIONS.put('[', OpenLinkToken::lex);
        LEX_FUNCTIONS.put('*', StarToken::lex);
        LEX_FUNCTIONS.put('{', OpenColorToken::lex);
    }

    public static Queue<Token> lex(String input) {
        var tokens = new ArrayDeque<Token>();
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

        return tokens;
    }

    private static String readTextUntil(StringReader reader, CharPredicate until) {
        var text = new StringBuilder();
        while (reader.canRead() && !until.test(reader.peek())) {
            text.append(reader.read());
        }

        return text.toString();
    }

    // --- Tokens with lexing implementation ---

    public abstract static class Token {
        protected final String content;

        protected Token(String content) {
            this.content = content;
        }

        public String content() {
            return this.content;
        }
    }

    public static final class TextToken extends Token {
        public TextToken(String content) {
            super(content);
        }
    }

    public static final class StarToken extends Token {

        public StarToken(String content) {
            super(content);
        }

        private static boolean lex(StringReader reader, Queue<Token> tokens) {
            reader.skip();

            if (!((reader.canRead() && reader.peek() != ' ') || (reader.getCursor() - 2 >= 0 && reader.peek(-2) != ' '))) {
                return false;
            }

            tokens.add(new StarToken("*"));
            return true;
        }
    }

    public static final class OpenLinkToken extends Token {

        public OpenLinkToken() {
            super("[");
        }

        private static boolean lex(StringReader reader, Queue<Token> tokens) {
            reader.skip();
            tokens.add(new OpenLinkToken());

            return true;
        }
    }

    public static final class CloseLinkToken extends Token {

        public final @NotNull String link;

        public CloseLinkToken(@NotNull String link) {
            super("](" + link + ")");
            this.link = link;
        }

        private static boolean lex(StringReader reader, Queue<Token> tokens) {
            reader.skip();

            if (!reader.canRead() || reader.peek() != '(') {
                return false;
            }

            reader.skip();
            var link = readTextUntil(reader, c -> c == ')');

            if (!reader.canRead()) return false;
            reader.skip();

            tokens.add(new CloseLinkToken(link));
            return true;
        }
    }

    public static final class OpenColorToken extends Token {

        public final @NotNull UnaryOperator<Style> style;

        public OpenColorToken(String content, @NotNull UnaryOperator<Style> style) {
            super(content);
            this.style = style;
        }

        private static boolean lex(StringReader reader, Queue<Token> tokens) {
            reader.skip();
            if (!reader.canRead()) return false;

            if (reader.peek() == '}') {
                reader.skip();
                tokens.add(new CloseColorToken());
            } else {
                if (reader.peek() == '#') {
                    reader.skip();

                    var color = readTextUntil(reader, c -> c == '}');
                    if (!reader.canRead()) return false;
                    reader.skip();

                    if (!color.matches("[0-9a-fA-F]{6}")) return false;
                    tokens.add(new OpenColorToken("{#" + color + "}", style -> style.withColor(Integer.parseInt(color, 16))));
                } else {
                    var color = readTextUntil(reader, c -> c == '}');
                    if (!reader.canRead()) return false;
                    reader.skip();

                    if (!FORMATTING_COLORS.containsKey(color)) return false;
                    tokens.add(new OpenColorToken("{" + color + "}", style -> style.withFormatting(FORMATTING_COLORS.get(color))));
                }
            }

            return true;
        }
    }

    public static final class CloseColorToken extends Token {

        private CloseColorToken() {
            super("{}");
        }
    }
}
