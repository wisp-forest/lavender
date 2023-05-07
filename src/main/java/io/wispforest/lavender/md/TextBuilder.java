package io.wispforest.lavender.md;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.UnaryOperator;

public class TextBuilder {

    private final Deque<Style> styles;

    private MutableText text = Text.empty();
    private boolean empty = true;

    public TextBuilder() {
        this.styles = new ArrayDeque<>();
        this.styles.push(Style.EMPTY);
    }

    public void append(MutableText text) {
        this.text.append(text.styled(style -> style.withParent(this.styles.peek())));
        this.empty = false;
    }

    public void pushStyle(UnaryOperator<Style> style) {
        this.styles.push(style.apply(this.styles.peek()));
    }

    public void popStyle() {
        this.styles.pop();
    }

    public Text build() {
        var result = this.text;

        this.text = Text.empty();
        this.empty = true;

        return result;
    }

    public boolean empty() {
        return this.empty;
    }
}
