package io.wispforest.lavender.md.extensions;

import io.wispforest.lavender.md.Lexer;
import io.wispforest.lavender.md.MarkdownExtension;
import io.wispforest.lavender.md.Parser;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;

public class KeybindExtension implements MarkdownExtension {

    @Override
    public String name() {
        return "keybindings";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return true;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((lexer, reader, tokens) -> {
            if (!lexer.expectString(reader, "<keybind;")) return false;

            var keybindKey = lexer.readTextUntil(reader, c -> c == '>');
            if (!reader.canRead() || reader.read() != '>') return false;

            var binding = Arrays.stream(MinecraftClient.getInstance().options.allKeys).filter($ -> $.getTranslationKey().equals(keybindKey)).findAny();
            if (binding.isEmpty()) return false;

            tokens.add(new KeybindToken(keybindKey, binding.get()));
            return true;
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, keybindToken, tokens) -> new KeybindNode(keybindToken.binding),
                (token, tokens) -> token instanceof KeybindToken keybind ? keybind : null
        );
    }

    private static class KeybindToken extends Lexer.Token {

        public final KeyBinding binding;

        public KeybindToken(String content, KeyBinding binding) {
            super(content);
            this.binding = binding;
        }
    }

    private static class KeybindNode extends Parser.Node {

        private final KeyBinding binding;

        public KeybindNode(KeyBinding binding) {
            this.binding = binding;
        }

        @Override
        public void visitStart(MarkdownCompiler<?> compiler) {
            compiler.visitStyle(style -> style.withColor(Formatting.GOLD).withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(
                            "text.lavender.keybind_tooltip",
                            Text.translatable(this.binding.getCategory()),
                            Text.translatable(this.binding.getTranslationKey())
                    ))
            ));
            compiler.visitText(I18n.translate(this.binding.getBoundKeyTranslationKey()));
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {
            compiler.visitStyleEnd();
        }
    }
}
