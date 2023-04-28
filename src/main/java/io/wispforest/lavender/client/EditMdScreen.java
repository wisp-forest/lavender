package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.parsing.Parser;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

import java.io.PrintWriter;
import java.io.StringWriter;

public class EditMdScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    protected EditMdScreen() {
        super(FlowLayout.class, Lavender.id("edit-md"));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        var output = rootComponent.childById(LabelComponent.class, "output");

        var box = new EditBoxWidget(this.client.textRenderer, 0, 0, 300, 100, Text.empty(), Text.empty()) {
            @Override
            public void onFocusGained(FocusSource source) {
                super.onFocusGained(source);
                this.setFocused(true);
            }
        };
        rootComponent.child(0, box.sizing(Sizing.fixed(300), Sizing.fixed(100)));

        box.setChangeListener(value -> {
            try {
                output.text(Parser.markdownToText(value));
            } catch (Exception e) {
                var trace = new StringWriter();
                var traceWriter = new PrintWriter(trace);
                e.printStackTrace(traceWriter);

                output.text(Text.literal(trace.toString()));
            }
        });
    }
}
