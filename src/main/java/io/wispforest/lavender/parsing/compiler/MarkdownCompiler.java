package io.wispforest.lavender.parsing.compiler;

import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

import java.util.function.UnaryOperator;

public interface MarkdownCompiler<R> {

    void visitText(String text);

    void visitStyle(UnaryOperator<Style> style);

    void visitStyleEnd();

    void visitQuotation();

    void visitQuotationEnd();

    void visitHorizontalRule();

    void visitImage(Identifier image, String description);

    R compile();
}
