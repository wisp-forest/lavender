package io.wispforest.lavender.md.extensions;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wispforest.lavender.md.Lexer;
import io.wispforest.lavender.md.MarkdownExtension;
import io.wispforest.lavender.md.Parser;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import io.wispforest.lavender.md.compiler.OwoUICompiler;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class EntityExtension implements MarkdownExtension {

    @Override
    public String name() {
        return "entities";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((lexer, reader, tokens) -> {
            if (!lexer.expectString(reader, "<entity;")) return false;

            var entityString = lexer.readTextUntil(reader, c -> c == '>');
            if (!reader.canRead() || reader.read() != '>') return false;

            try {
                NbtCompound nbt = null;

                int nbtIndex = entityString.indexOf('{');
                if (nbtIndex != -1) {

                    nbt = new StringNbtReader(new StringReader(entityString.substring(nbtIndex))).parseCompound();
                    entityString = entityString.substring(0, nbtIndex);
                }

                var entityType = Registries.ENTITY_TYPE.getOrEmpty(new Identifier(entityString)).orElseThrow();
                tokens.add(new EntityToken(entityString, entityType, nbt));
                return true;
            } catch (CommandSyntaxException e) {
                return false;
            }
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, entityToken, tokens) -> new EntityNode(entityToken.type, entityToken.nbt),
                (token, tokens) -> token instanceof EntityToken entity ? entity : null
        );
    }

    private static class EntityToken extends Lexer.Token {

        public final EntityType<?> type;
        public final @Nullable NbtCompound nbt;

        public EntityToken(String content, EntityType<?> type, @Nullable NbtCompound nbt) {
            super(content);
            this.type = type;
            this.nbt = nbt;
        }
    }

    private static class EntityNode extends Parser.Node {

        public final EntityType<?> type;
        public final @Nullable NbtCompound nbt;

        public EntityNode(EntityType<?> type, @Nullable NbtCompound nbt) {
            this.type = type;
            this.nbt = nbt;
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            ((OwoUICompiler) compiler).visitComponent(Components.entity(Sizing.fixed(32), this.type, this.nbt).scaleToFit(true));
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }
}
