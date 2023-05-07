package io.wispforest.lavender.md.extensions;

import io.wispforest.lavender.md.Lexer;
import io.wispforest.lavender.md.MarkdownExtension;
import io.wispforest.lavender.md.Parser;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;

public class PageBreakExtension implements MarkdownExtension {

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
        registrar.registerToken((lexer, reader, tokens) -> {
            if (reader.getCursor() - 2 < 0 || reader.peek(-1) != '\n' || reader.peek(-2) != '\n') return false;
            if (!lexer.expectString(reader, ";;;;;\n\n")) return false;

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
            super(";;;\n\n");
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
