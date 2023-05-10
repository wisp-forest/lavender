package io.wispforest.lavender.md.compiler;

import io.wispforest.lavender.client.LavenderClient;
import io.wispforest.lavender.md.TextBuilder;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.util.Drawer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;

public class OwoUICompiler implements MarkdownCompiler<ParentComponent> {

    protected final Deque<FlowLayout> components = new ArrayDeque<>();
    protected final TextBuilder textBuilder = new TextBuilder();

    public OwoUICompiler() {
        this.components.push(Containers.verticalFlow(Sizing.content(), Sizing.content()));
    }

    @Override
    public void visitText(String text) {
        this.textBuilder.append(Text.literal(text));
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
        this.textBuilder.pushStyle(style -> style.withFormatting(Formatting.GRAY));

        var quotation = Containers.verticalFlow(Sizing.content(), Sizing.content());
        quotation.padding(Insets.of(5, 5, 7, 5)).surface((matrices, component) -> {
            Drawer.fill(matrices, component.x(), component.y() + 3, component.x() + 2, component.y() + component.height() - 3, 0xFF777777);
        });

        this.push(quotation);
    }

    @Override
    public void visitQuotationEnd() {
        this.textBuilder.popStyle();
        this.pop();
    }

    @Override
    public void visitHorizontalRule() {
        this.append(new BoxComponent(Sizing.fill(100), Sizing.fixed(2)).color(Color.ofRgb(0x777777)).fill(true));
    }

    @Override
    public void visitImage(Identifier image, String description) {
        var textureSize = LavenderClient.getTextureSize(image);
        if (textureSize == null) textureSize = Size.of(64, 64);

        this.append(Components.texture(image, 0, 0, textureSize.width(), textureSize.height(), textureSize.width(), textureSize.height()).blend(true).tooltip(Text.literal(description)));
    }

    @Override
    public void visitListItem(OptionalInt ordinal) {
        var element = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        element.child(this.makeLabel(Text.literal(ordinal.isPresent() ? " " + ordinal.getAsInt() + ". " : " • ").formatted(Formatting.GRAY)).margins(Insets.left(-11))).margins(Insets.vertical(1));
        element.padding(Insets.left(11)).allowOverflow(true);

        var container = Containers.verticalFlow(Sizing.content(), Sizing.content());
        element.child(container);

        this.push(element, container);
    }

    @Override
    public void visitListItemEnd() {
        this.pop();
    }

    public void visitComponent(Component component) {
        this.append(component);
    }

    protected void append(Component component) {
        this.flushText();
        this.components.peek().child(component);
    }

    protected void push(FlowLayout component) {
        this.push(component, component);
    }

    protected void push(Component element, FlowLayout contentPanel) {
        this.append(element);
        this.components.push(contentPanel);
    }

    protected void pop() {
        this.flushText();
        this.components.pop();
    }

    protected LabelComponent makeLabel(MutableText text) {
        return Components.label(text);
    }

    protected void flushText() {
        if (this.textBuilder.empty()) return;
        this.components.peek().child(this.makeLabel(this.textBuilder.build()).horizontalSizing(Sizing.fill(100)));
    }

    @Override
    public ParentComponent compile() {
        this.flushText();
        return this.components.getLast();
    }

    @Override
    public String name() {
        return "lavender_builtin_owo_ui";
    }
}
