package io.wispforest.lavender.md;

import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import io.wispforest.lavender.md.compiler.OwoUICompiler;
import io.wispforest.lavender.md.compiler.TextCompiler;
import io.wispforest.owo.ui.core.Component;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public class MarkdownProcessor<R> {

    public static final MarkdownProcessor<Component> OWO_UI = new MarkdownProcessor<>(OwoUICompiler::new);
    public static final MarkdownProcessor<Text> TEXT = new MarkdownProcessor<>(TextCompiler::new);

    private final Supplier<MarkdownCompiler<R>> compilerFactory;

    public MarkdownProcessor(Supplier<MarkdownCompiler<R>> compilerFactory) {
        this.compilerFactory = compilerFactory;
    }

    public R process(String markdown) {
        var compiler = this.compilerFactory.get();
        Parser.parse(Lexer.lex(markdown)).visit(compiler);

        return compiler.compile();
    }
}
