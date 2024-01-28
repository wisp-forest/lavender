package io.wispforest.lavender.md.features;

import io.wispforest.lavendermd.Lexer;
import io.wispforest.lavendermd.MarkdownFeature;
import io.wispforest.lavendermd.Parser;
import io.wispforest.lavendermd.compiler.MarkdownCompiler;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Optional;

public class TranslationsFeature implements MarkdownFeature {

    @Override
    public String name() {
        return "translations";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return true;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((nibbler, tokens) -> {
            if (!nibbler.tryConsume("%{")) return false;

            var content = nibbler.consumeEscapedString('}', false);
            if (content == null || !nibbler.tryConsume('%')) return false;

            tokens.add(new TranslationToken(content));
            return true;
        },  '%');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode((parser, translation, tokens) -> {
            var result = Parser.Node.empty();
            Text.translatable(translation.key).visit((style, content) -> {
                result.addChild(new Parser.FormattingNode(style::withParent).addChild(new Parser.TextNode(content)));
                return Optional.empty();
            }, Style.EMPTY);

            return result;
        }, (token, tokenListNibbler) -> token instanceof TranslationToken translation ? translation : null);
    }

    private static final class TranslationToken extends Lexer.Token {

        public final String key;

        public TranslationToken(String key) {
            super("%{" + key + "}%");
            this.key = key;
        }
    }
}
