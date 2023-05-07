package io.wispforest.lavender.md.compiler;

import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;
import java.util.function.UnaryOperator;

public interface MarkdownCompiler<R> {

    void visitText(String text);

    void visitStyle(UnaryOperator<Style> style);

    void visitStyleEnd();

    void visitQuotation();

    void visitQuotationEnd();

    void visitHorizontalRule();

    void visitImage(Identifier image, String description);

    void visitListItem(OptionalInt ordinal);

    void visitListItemEnd();

    R compile();
}
