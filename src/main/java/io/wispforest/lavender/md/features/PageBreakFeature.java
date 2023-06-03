package io.wispforest.lavender.md.features;

import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavendermd.Lexer;
import io.wispforest.lavendermd.MarkdownFeature;
import io.wispforest.lavendermd.Parser;
import io.wispforest.lavendermd.compiler.MarkdownCompiler;

public class PageBreakFeature implements MarkdownFeature {

    @Override
    public String name() {
        return "book_page_breaks";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof BookCompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((nibbler, tokens) -> {
            if (!nibbler.expect(-1, '\n') || !nibbler.expect(-2, '\n')) return false;
            if (!nibbler.tryConsume(";;;;;\n\n")) return false;

            tokens.add(new PageBreakToken());
            return true;
        }, ';');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, trigger, tokens) -> new PageBreakNode(),
                (token, tokenListNibbler) -> token instanceof PageBreakToken pageBreak ? pageBreak : null
        );
    }

    private static class PageBreakToken extends Lexer.Token {
        public PageBreakToken() {
            super(";;;;;\n\n");
        }

        @Override
        public boolean isBoundary() {
            return true;
        }
    }

    private static class PageBreakNode extends Parser.Node {
        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            ((BookCompiler) compiler).visitPageBreak();
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }
}
