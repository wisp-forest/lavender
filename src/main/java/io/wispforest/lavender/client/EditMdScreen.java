package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.md.MarkdownProcessor;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextAreaComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
import net.minecraft.text.Text;

import java.io.PrintWriter;
import java.io.StringWriter;

public class EditMdScreen extends BaseUIModelScreen<FlowLayout> implements CommandOpenedScreen {

    public EditMdScreen() {
        super(FlowLayout.class, Lavender.id("edit-md"));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        var output = rootComponent.childById(LabelComponent.class, "output");

        var anchor = rootComponent.childById(FlowLayout.class, "output-anchor");
        rootComponent.childById(TextAreaComponent.class, "input").onChanged().subscribe(value -> {
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
