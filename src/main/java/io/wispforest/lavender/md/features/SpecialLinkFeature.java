package io.wispforest.lavender.md.features;


import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavendermd.Lexer;
import io.wispforest.lavendermd.MarkdownFeature;
import io.wispforest.lavendermd.Parser;
import io.wispforest.lavendermd.compiler.MarkdownCompiler;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Optional;

// Workaround to prevent the Lavender-MD feature from exploding when parsing a string that starts with ^ into a URI
public class SpecialLinkFeature implements MarkdownFeature {

	@Override
	public String name() {
		return "lavender:links";
	}

	@Override
	public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
		return true;
	}

	@Override
	public void registerTokens(TokenRegistrar registrar) {
		registrar.registerToken(Lexer.Token.lexFromChar(OpenLinkToken::new), '[');
		registrar.registerToken((nibbler, tokens) -> {
			nibbler.skip();
			if (!nibbler.tryConsume('(')) return false;

			var link = nibbler.consumeUntil(')');
			if (link == null) return false;

			tokens.add(new CloseLinkToken(link));
			return true;
		}, ']');
	}

	@Override
	public void registerNodes(NodeRegistrar registrar) {
		registrar.registerNode((parser, left, tokens) -> {
			int pointer = tokens.pointer();
			var content = parser.parseUntil(tokens, CloseLinkToken.class);

			if (tokens.peek() instanceof CloseLinkToken right) {
				tokens.nibble();
				return new Parser.FormattingNode(style -> style.withClickEvent(
						right.link.startsWith("^")
								? new ClickEvent.Custom(BookCompiler.BookLabelComponent.LINK, Optional.of(NbtString.of(right.link)))
								: new ClickEvent.OpenUrl(URI.create(right.link))
				).withHoverEvent(
						new HoverEvent.ShowText(Text.literal(right.link))
				).withColor(Formatting.BLUE)).addChild(content);
			} else {
				tokens.setPointer(pointer);
				return new Parser.TextNode(left.content());
			}
		}, (token, tokens) -> token instanceof OpenLinkToken link ? link : null);
	}

	// --- tokens ---

	private static final class OpenLinkToken extends Lexer.Token {
		public OpenLinkToken() {
			super("[");
		}
	}

	private static final class CloseLinkToken extends Lexer.Token {

		public final @NotNull String link;

		public CloseLinkToken(@NotNull String link) {
			super("](" + link + ")");
			this.link = link;
		}
	}
}
