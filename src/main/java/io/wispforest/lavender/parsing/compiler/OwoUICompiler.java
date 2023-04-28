package io.wispforest.lavender.parsing.compiler;

import io.wispforest.lavender.parsing.TextBuilder;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.Drawer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.UnaryOperator;

public class OwoUICompiler implements MarkdownCompiler<Component> {

    private final Deque<FlowLayout> components = new ArrayDeque<>();

    public OwoUICompiler() {
        this.components.push(Containers.verticalFlow(Sizing.content(), Sizing.content()));
    }

    private TextBuilder textBuilder = new TextBuilder();
    private boolean textEmpty = true;

    @Override
    public void visitText(String text) {
        this.textBuilder.append(Text.literal(text));
        this.textEmpty = false;
    }

    @Override
    public void visitStyle(UnaryOperator<Style> style) {
        this.textBuilder.pushStyle(style);
    }

    @Override
    public void visitStyleEnd() {
        this.textBuilder.popStyle();
    }

    @Override
    public void visitQuotation() {
        this.pushText();
        this.textBuilder.pushStyle(style -> style.withFormatting(Formatting.GRAY));

        var quotation = Containers.verticalFlow(Sizing.content(), Sizing.content());
        quotation.padding(Insets.of(5, 5, 7, 5)).surface((matrices, component) -> {
            Drawer.fill(matrices, component.x(), component.y(), component.x() + 3, component.y() + component.height(), 0xFF777777);
        });

        this.components.peek().child(quotation);
        this.components.push(quotation);
    }

    @Override
    public void visitQuotationEnd() {
        this.pushText();
        this.components.pop();
    }

    @Override
    public void visitHorizontalRule() {
        this.pushText();
        this.components.peek().child(new BoxComponent(Sizing.fill(100), Sizing.fixed(2)).color(Color.ofRgb(0x777777)).fill(true));
    }

    protected void pushText() {
        if (this.textEmpty) return;

        var label = Components.label(this.textBuilder.build());
        this.components.peek().child(label.horizontalSizing(Sizing.fill(100)));

        this.textBuilder = new TextBuilder();
        this.textEmpty = true;
    }

    @Override
    public Component compile() {
        this.pushText();
        return this.components.getLast();
    }
}
