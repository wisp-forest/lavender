package io.wispforest.lavender.md;

import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public interface MarkdownExtension {

    String name();

    boolean supportsCompiler(MarkdownCompiler<?> compiler);

    void registerTokens(TokenRegistrar registrar);

    void registerNodes(NodeRegistrar registrar);

    @FunctionalInterface
    interface TokenRegistrar {
        void registerToken(Lexer.LexFunction lexer, char trigger);

        default void registerToken(Lexer.LexFunction lexer, char... triggers) {
            for (char trigger : triggers) {
                this.registerToken(lexer, trigger);
            }
        }
    }

    @FunctionalInterface
    interface NodeRegistrar {
        <T extends Lexer.Token> void registerNode(Parser.ParseFunction<T> parser, BiFunction<Lexer.Token, ListNibbler<Lexer.Token>, @Nullable T> trigger);
    }

}
