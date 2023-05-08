package io.wispforest.lavender.md;

import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import io.wispforest.lavender.md.compiler.OwoUICompiler;
import io.wispforest.lavender.md.compiler.TextCompiler;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.ParentComponent;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class MarkdownProcessor<R> {

    public static final MarkdownProcessor<ParentComponent> OWO_UI = new MarkdownProcessor<>(OwoUICompiler::new);
    public static final MarkdownProcessor<Text> TEXT = new MarkdownProcessor<>(TextCompiler::new);

    private final Supplier<MarkdownCompiler<R>> compilerFactory;

    private final List<MarkdownExtension> extensions;

    private final Lexer lexer;
    private final Parser parser;

    public MarkdownProcessor(Supplier<MarkdownCompiler<R>> compilerFactory, MarkdownExtension... extensions) {
        this.compilerFactory = compilerFactory;

        this.extensions = Arrays.asList(extensions);

        var testCompiler = this.compilerFactory.get();
        for (var extension : this.extensions) {
            if (!extension.supportsCompiler(testCompiler)) {
                throw new IllegalStateException("Extension '" + extension.name() + "' is incompatible with compiler '" + testCompiler.name() + "'");
            }
        }

        this.lexer = new Lexer();
        this.parser = new Parser();

        for (var extension : extensions) {
            extension.registerTokens(this.lexer);
            extension.registerNodes(this.parser);
        }
    }

    public Collection<MarkdownExtension> extensions() {
        return this.extensions;
    }

    public boolean hasExtension(Class<?> extensionClass) {
        for (var extension : this.extensions) {
            if (extensionClass.isInstance(extension)) {
                return true;
            }
        }

        return false;
    }

    public R process(String markdown) {
        var compiler = this.compilerFactory.get();

        this.parser.parse(this.lexer.lex(markdown)).visit(compiler);
        return compiler.compile();
    }
}
