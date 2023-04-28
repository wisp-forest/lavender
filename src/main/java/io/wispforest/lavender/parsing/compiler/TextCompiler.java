package io.wispforest.lavender.parsing.compiler;

import io.wispforest.lavender.parsing.TextBuilder;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.UnaryOperator;

public class TextCompiler implements MarkdownCompiler<Text> {

    private final TextBuilder builder = new TextBuilder();

    @Override
    public void visitText(String text) {
        this.builder.append(Text.literal(text));
    }

    @Override
    public void visitStyle(UnaryOperator<Style> style) {
        this.builder.pushStyle(style);
    }

    @Override
    public void visitStyleEnd() {
        this.builder.popStyle();
    }

    @Override
    public void visitQuotation() {
        this.builder.append(Text.literal("\n >> ").formatted(Formatting.DARK_GRAY));
        this.builder.pushStyle(style -> style.withColor(Formatting.GRAY).withItalic(true));
    }

    @Override
    public void visitQuotationEnd() {
        this.builder.popStyle();
        this.builder.append(Text.literal("\n"));
    }

    @Override
    public void visitHorizontalRule() {
        this.builder.append(Text.literal("-".repeat(50)).formatted(Formatting.DARK_GRAY));
    }

    @Override
    public Text compile() {
        return this.builder.build();
    }
}
