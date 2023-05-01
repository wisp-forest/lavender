package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.parsing.MarkdownProcessor;
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

        var box = new EditBoxWidget(this.client.textRenderer, 0, 0, 500, 180, Text.empty(), Text.empty()) {
            @Override
            public void onFocusGained(FocusSource source) {
                super.onFocusGained(source);
                this.setFocused(true);
            }
        };
        rootComponent.childById(FlowLayout.class, "input-anchor").child(0, box.sizing(Sizing.fixed(500), Sizing.fixed(180)));

        var anchor = rootComponent.childById(FlowLayout.class, "output-anchor");
        box.setChangeListener(value -> {
            try {
                anchor.<FlowLayout>configure(layout -> {
                    layout.clearChildren();
                    layout.child(MarkdownProcessor.OWO_UI.process(value));
                });

                output.text(MarkdownProcessor.TEXT.process(value));
            } catch (Exception e) {
                var trace = new StringWriter();
                var traceWriter = new PrintWriter(trace);
                e.printStackTrace(traceWriter);

                output.text(Text.literal(trace.toString()));
            }
        });
    }
}
