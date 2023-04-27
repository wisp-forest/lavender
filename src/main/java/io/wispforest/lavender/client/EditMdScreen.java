package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.parsing.Parser;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.util.CommandOpenedScreen;
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
        rootComponent.childById(TextBoxComponent.class, "input").onChanged().subscribe(value -> {
            try {
                output.text(Parser.parse(value));
            } catch (Exception e) {
                var trace = new StringWriter();
                var traceWriter = new PrintWriter(trace);
                e.printStackTrace(traceWriter);

                output.text(Text.literal(trace.toString()));
            }
        });
    }
}
