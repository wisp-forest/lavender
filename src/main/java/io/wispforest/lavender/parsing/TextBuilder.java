package io.wispforest.lavender.parsing;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.UnaryOperator;

public class TextBuilder {

    private final Deque<MutableText> text;

    public TextBuilder() {
        this.text = new ArrayDeque<>();
        this.text.push(Text.empty());
    }

    public void append(Text text) {
        this.text.peek().append(text);
    }

    public void pushStyle(UnaryOperator<Style> style) {
        var top = this.text.peek();
        var newTop = Text.empty().setStyle(style.apply(top.getStyle()));
        top.append(newTop);
        this.text.push(newTop);
    }

    public void popStyle() {
        this.text.pop();
    }

    public Text build() {
        return this.text.getLast().copy();
    }
}
