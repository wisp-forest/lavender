package io.wispforest.lavender.md.extensions;

import io.wispforest.lavender.md.Lexer;
import io.wispforest.lavender.md.MarkdownExtension;
import io.wispforest.lavender.md.Parser;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import io.wispforest.lavender.md.compiler.OwoUICompiler;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import net.minecraft.util.Identifier;

import java.util.HashMap;

public class OwoUITemplateExtension implements MarkdownExtension {
    @Override
    public String name() {
        return "owo_ui_templates";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((lexer, reader, tokens) -> {
            reader.skip();
            if (!reader.canRead() || reader.read() != '|') return false;

            var templateLocation = lexer.readTextUntil(reader, c -> c == '|');
            if (!reader.canRead() || reader.read() != '|') return false;

            var splitLocation = templateLocation.split("@");
            if (splitLocation.length != 2) return false;

            var modelId = Identifier.tryParse(splitLocation[1]);
            if (modelId == null || !UIModelLoader.allLoadedModels().contains(modelId)) return false;

            String templateParams = "";
            if (reader.canRead() && reader.peek() != '>') {
                templateParams = lexer.readTextUntil(reader, c -> c == '|');
                if (!lexer.expectString(reader, "|>")) return false;
            } else {
                reader.skip();
            }

            tokens.add(new TemplateToken(modelId, splitLocation[0], templateParams));
            return true;
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, templateToken, tokens) -> new TemplateNode(templateToken.modelId, templateToken.templateName, templateToken.params),
                (token, tokens) -> token instanceof TemplateToken template ? template : null
        );
    }

    private static class TemplateToken extends Lexer.Token {

        public final Identifier modelId;
        public final String templateName;
        public final String params;

        public TemplateToken(Identifier modelId, String templateName, String params) {
            super("<|" + modelId + "|" + params + "|>");
            this.modelId = modelId;
            this.templateName = templateName;
            this.params = params;
        }

        @Override
        public boolean isBoundary() {
            return true;
        }
    }

    private static class TemplateNode extends Parser.Node {

        private final Identifier modelId;
        private final String templateName;
        private final String params;

        public TemplateNode(Identifier modelId, String templateName, String params) {
            this.modelId = modelId;
            this.templateName = templateName;
            this.params = params;
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            var builtParams = new HashMap<String, String>();
            for (var parameter : this.params.split(",")) {
                if (parameter.split("=").length != 2) continue;
                builtParams.put(parameter.split("=")[0], parameter.split("=")[1]);
            }

            ((OwoUICompiler) compiler).visitComponent(UIModelLoader.get(modelId).expandTemplate(Component.class, this.templateName, builtParams));
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }
}
