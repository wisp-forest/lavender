package io.wispforest.lavender.md.features;

import com.google.common.primitives.Ints;
import io.wispforest.lavender.book.StructureComponent;
import io.wispforest.lavender.client.StructureOverlayRenderer;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.structure.LavenderStructures;
import io.wispforest.lavender.structure.StructureTemplate;
import io.wispforest.lavendermd.Lexer;
import io.wispforest.lavendermd.MarkdownFeature;
import io.wispforest.lavendermd.Parser;
import io.wispforest.lavendermd.compiler.MarkdownCompiler;
import io.wispforest.lavendermd.compiler.OwoUICompiler;
import io.wispforest.owo.ui.component.SlimSliderComponent;
import io.wispforest.owo.ui.core.ParentComponent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;

public class StructureFeature implements MarkdownFeature {

    private final BookCompiler.ComponentSource bookComponentSource;

    public StructureFeature(BookCompiler.ComponentSource bookComponentSource) {
        this.bookComponentSource = bookComponentSource;
    }

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
        registrar.registerToken((nibbler, tokens) -> {
            if (!nibbler.tryConsume("<structure;")) return false;

            var structureIdString = nibbler.consumeUntil('>');
            if (structureIdString == null) return false;

            int angle = 35;
            if (structureIdString.matches("-?\\d+;.+")) {
                var parsedAngle = Ints.tryParse(structureIdString.substring(0, structureIdString.indexOf(';')));
                if (parsedAngle == null) return false;

                angle = parsedAngle;
                structureIdString = structureIdString.substring(structureIdString.indexOf(';') + 1);
            }

            var structureId = Identifier.tryParse(structureIdString);
            if (structureId == null) return false;

            var structure = LavenderStructures.get(structureId);
            if (structure == null) return false;

            tokens.add(new StructureToken(structureIdString, structure, angle));
            return true;
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, structureToken, tokens) -> new StructureNode(structureToken.structure, structureToken.angle),
                (token, tokens) -> token instanceof StructureToken structure ? structure : null
        );
    }

    private static class StructureToken extends Lexer.Token {

        public final StructureTemplate structure;
        public final int angle;

        public StructureToken(String content, StructureTemplate structure, int angle) {
            super(content);
            this.structure = structure;
            this.angle = angle;
        }
    }

    private class StructureNode extends Parser.Node {

        private final StructureTemplate structure;
        private final int angle;

        public StructureNode(StructureTemplate structure, int angle) {
            this.structure = structure;
            this.angle = angle;
        }

        @Override
        @SuppressWarnings("DataFlowIssue")
        protected void visitStart(MarkdownCompiler<?> compiler) {
            var structureComponent = StructureFeature.this.bookComponentSource.builtinTemplate(
                    ParentComponent.class,
                    this.structure.ySize > 1 ? "structure-preview-with-layers" : "structure-preview",
                    Map.of("structure", this.structure.id.toString(), "angle", String.valueOf(this.angle))
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
        protected void visitEnd(MarkdownCompiler<?> compiler) {
        }
    }
}
