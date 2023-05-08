package io.wispforest.lavender.md.extensions;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wispforest.lavender.md.Lexer;
import io.wispforest.lavender.md.MarkdownExtension;
import io.wispforest.lavender.md.Parser;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import io.wispforest.lavender.md.compiler.OwoUICompiler;
import io.wispforest.owo.ui.component.Components;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public class ItemStackExtension implements MarkdownExtension {

    @Override
    public String name() {
        return "item_stacks";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((lexer, reader, tokens) -> {
            if (!lexer.expectString(reader, "<item;")) return false;

            var itemStackString = lexer.readTextUntil(reader, c -> c == '>');
            if (!reader.canRead() || reader.read() != '>') return false;

            try {
                var result = ItemStringReader.item(Registries.ITEM.getReadOnlyWrapper(), new StringReader(itemStackString));

                var stack = result.item().value().getDefaultStack();
                stack.setNbt(result.nbt());

                tokens.add(new ItemStackToken(itemStackString, stack));
                return true;
            } catch (CommandSyntaxException e) {
                return false;
            }
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, stackToken, tokens) -> new ItemStackNode(stackToken.stack),
                (token, tokens) -> token instanceof ItemStackToken itemStack ? itemStack : null
        );
    }

    private static class ItemStackToken extends Lexer.Token {

        public final ItemStack stack;

        public ItemStackToken(String content, ItemStack stack) {
            super(content);
            this.stack = stack;
        }
    }

    private static class ItemStackNode extends Parser.Node {

        private final ItemStack stack;

        public ItemStackNode(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        protected void visitStart(MarkdownCompiler<?> compiler) {
            ((OwoUICompiler) compiler).visitComponent(Components.item(this.stack).setTooltipFromStack(true));
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }
}
