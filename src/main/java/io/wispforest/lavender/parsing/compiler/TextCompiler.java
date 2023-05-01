package io.wispforest.lavender.parsing.compiler;

import io.wispforest.lavender.parsing.TextBuilder;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.function.UnaryOperator;

public class TextCompiler implements MarkdownCompiler<Text> {

    private final TextBuilder builder = new TextBuilder();
    private int quoteDepth = 0;

    @Override
    public void visitText(String text) {
        if (this.quoteDepth != 0 && text.contains("\n")) {
            for (var line : text.split("\n")) {
                this.builder.append(Text.literal("\n >" + ">".repeat(this.quoteDepth) + " ").formatted(Formatting.DARK_GRAY).append(Text.literal(line)));
            }
        } else {
            this.builder.append(Text.literal(text));
        }
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
        this.quoteDepth++;
        this.builder.append(Text.literal("\n >" + ">".repeat(this.quoteDepth) + " ").formatted(Formatting.DARK_GRAY));
        this.builder.pushStyle(style -> style.withColor(Formatting.GRAY).withItalic(true));
    }

    @Override
    public void visitQuotationEnd() {
        this.builder.popStyle();
        this.quoteDepth--;

        if (this.quoteDepth > 0) {
            this.builder.append(Text.literal("\n >" + ">".repeat(this.quoteDepth) + " ").formatted(Formatting.DARK_GRAY));
        } else {
            this.builder.append(Text.literal("\n"));
        }
    }

    @Override
    public void visitHorizontalRule() {
        this.builder.append(Text.literal("-".repeat(50)).formatted(Formatting.DARK_GRAY));
    }

    @Override
    public void visitImage(Identifier image, String description) {
        this.builder.append(Text.literal("[" + description + "]").formatted(Formatting.YELLOW));
    }

    @Override
    public Text compile() {
        return this.builder.build();
    }
}
