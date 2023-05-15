package io.wispforest.lavender.md.extensions;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.StructureComponent;
import io.wispforest.lavender.client.StructureOverlayRenderer;
import io.wispforest.lavender.md.Lexer;
import io.wispforest.lavender.md.MarkdownExtension;
import io.wispforest.lavender.md.Parser;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import io.wispforest.lavender.md.compiler.OwoUICompiler;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.lavender.structure.StructureInfo;
import io.wispforest.owo.ui.component.SlimSliderComponent;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.parsing.UIModelLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

public class StructureExtension implements MarkdownExtension {

    @Override
    public String name() {
        return "structures";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((lexer, reader, tokens) -> {
            if (!lexer.expectString(reader, "<structure;")) return false;

            var structureIdString = lexer.readTextUntil(reader, c -> c == '>');
            if (!reader.canRead() || reader.read() != '>') return false;

            var structureId = Identifier.tryParse(structureIdString);
            if (structureId == null) return false;

            var structure = LavenderStructures.get(structureId);
            if (structure == null) return false;

            tokens.add(new StructureToken(structureIdString, structure));
            return true;
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, structureToken, tokens) -> new StructureNode(structureToken.structure),
                (token, tokens) -> token instanceof StructureToken structure ? structure : null
        );
    }

    private static class StructureToken extends Lexer.Token {

        public final StructureInfo structure;

        public StructureToken(String content, StructureInfo structure) {
            super(content);
            this.structure = structure;
        }
    }

    private static class StructureNode extends Parser.Node {

        private final StructureInfo structure;

        public StructureNode(StructureInfo structure) {
            this.structure = structure;
        }

        @Override
        @SuppressWarnings("DataFlowIssue")
        protected void visitStart(MarkdownCompiler<?> compiler) {
            var structureComponent = UIModelLoader.get(Lavender.id("book_components")).expandTemplate(
                    ParentComponent.class,
                    this.structure.ySize > 1 ? "structure-preview-with-layers" : "structure-preview",
                    Map.of("structure", this.structure.id.toString())
            );

            var structurePreview = structureComponent.childById(StructureComponent.class, "structure");
            var layerSlider = structureComponent.childById(SlimSliderComponent.class, "layer-slider");

            if (layerSlider != null) {
                layerSlider.max(0).min(this.structure.ySize).tooltipSupplier(layer -> {
                    return layer > 0
                            ? Text.translatable("text.lavender.structure_component.layer_tooltip", layer.intValue())
                            : Text.translatable("text.lavender.structure_component.all_layers_tooltip");
                }).onChanged().subscribe(layer -> {
                    structurePreview.visibleLayer((int) layer - 1);
                });

                layerSlider.value(StructureOverlayRenderer.getLayerRestriction(this.structure.id) + 1);
            }

            ((OwoUICompiler) compiler).visitComponent(structureComponent);
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }
}
