package io.wispforest.lavender.md.features;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavendermd.Lexer;
import io.wispforest.lavendermd.MarkdownFeature;
import io.wispforest.lavendermd.Parser;
import io.wispforest.lavendermd.compiler.MarkdownCompiler;
import io.wispforest.lavendermd.compiler.OwoUICompiler;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.parsing.UIModel;
import net.minecraft.text.Text;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OwoUIModelFeature implements MarkdownFeature {

    @Override
    public String name() {
        return "owo_ui_models";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((nibbler, tokens) -> {
            if (!nibbler.tryConsume("```xml owo-ui")) return false;

            var content = nibbler.consumeUntil('`');
            if (content == null || !nibbler.tryConsume("``")) return false;

            tokens.add(new UIModelToken(content, content));
            return true;
        }, '`');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, stackToken, tokens) -> new UIModelNode(stackToken.xmlContent),
                (token, tokens) -> token instanceof UIModelToken model ? model : null
        );
    }

    private static class UIModelToken extends Lexer.Token {

        public final String xmlContent;

        public UIModelToken(String content, String xmlContent) {
            super(content);
            this.xmlContent = xmlContent;
        }
    }

    private static class UIModelNode extends Parser.Node {

        private static final String MODEL_TEMPLATE = """
                <owo-ui>
                    <templates>
                        <template name="__model-feature-generated__">
                            {{template-content}}
                        </template>
                    </templates>
                </owo-ui>
                """;

        private final String modelString;

        public UIModelNode(String xmlContent) {
            this.modelString = MODEL_TEMPLATE.replaceFirst("\\{\\{template-content}}", xmlContent);
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            try {
                var model = UIModel.load(new ByteArrayInputStream(this.modelString.getBytes(StandardCharsets.UTF_8)));
                ((OwoUICompiler) compiler).visitComponent(model.expandTemplate(Component.class, "__model-feature-generated__", Map.of()));
            } catch (ParserConfigurationException | IOException | SAXException e) {
                Lavender.LOGGER.warn("Failed to build owo-ui model markdown element", e);
                ((OwoUICompiler) compiler).visitComponent(
                        Containers.verticalFlow(Sizing.fill(100), Sizing.content())
                                .child(Components.label(Text.literal(e.getMessage())).horizontalSizing(Sizing.fill(100)))
                                .padding(Insets.of(10))
                                .surface(Surface.flat(0x77A00000).and(Surface.outline(0x77FF0000)))
                );
            }

        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }

}
