package io.wispforest.lavender.md.compiler;

import io.wispforest.lavender.Lavender;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;

import java.util.Map;

public class BookCompiler extends OwoUICompiler {

    private static final Style UNICODE_FONT_STYLE = Style.EMPTY.withFont(MinecraftClient.UNICODE_FONT_ID);

    private final FlowLayout resultContainer = Containers.verticalFlow(Sizing.content(), Sizing.content());

    public BookCompiler() {
        this.push(Containers.verticalFlow(Sizing.content(), Sizing.content()));
    }

    @Override
    protected LabelComponent makeLabel(MutableText text) {
        return super.makeLabel(text.styled(style -> style.withParent(UNICODE_FONT_STYLE))).color(Color.BLACK).lineHeight(8);
    }

    @Override
    public void visitHorizontalRule() {
        this.append(UIModelLoader.get(Lavender.id("book")).expandTemplate(Component.class, "horizontal-rule", Map.of()));
    }

    public void visitPageBreak() {
        this.resultContainer.child(components.peek());
        this.pop();
        this.push(Containers.verticalFlow(Sizing.content(), Sizing.content()));
    }

    @Override
    public ParentComponent compile() {
        this.pop();
        return super.compile();
    }

    @Override
    public String name() {
        return "lavender_builtin_book";
    }
}
