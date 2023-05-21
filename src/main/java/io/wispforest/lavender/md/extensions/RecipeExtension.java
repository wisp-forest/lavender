package io.wispforest.lavender.md.extensions;

import io.wispforest.lavender.md.Lexer;
import io.wispforest.lavender.md.MarkdownExtension;
import io.wispforest.lavender.md.Parser;
import io.wispforest.lavender.md.compiler.BookCompiler;
import io.wispforest.lavender.md.compiler.MarkdownCompiler;
import io.wispforest.lavender.md.compiler.OwoUICompiler;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.parsing.UIParsing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeGridAligner;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class RecipeExtension implements MarkdownExtension {

    private final BookCompiler.ComponentSource bookComponentSource;

    public RecipeExtension(BookCompiler.ComponentSource bookComponentSource) {
        this.bookComponentSource = bookComponentSource;
    }

    @Override
    public String name() {
        return "recipes";
    }

    @Override
    public boolean supportsCompiler(MarkdownCompiler<?> compiler) {
        return compiler instanceof OwoUICompiler;
    }

    @Override
    public void registerTokens(TokenRegistrar registrar) {
        registrar.registerToken((lexer, reader, tokens) -> {
            if (!lexer.expectString(reader, "<recipe;")) return false;

            var recipeIdString = lexer.readTextUntil(reader, c -> c == '>');
            if (!reader.canRead() || reader.read() != '>') return false;

            var recipeId = Identifier.tryParse(recipeIdString);
            if (recipeId == null) return false;

            var recipe = MinecraftClient.getInstance().world.getRecipeManager().get(recipeId);
            if (recipe.isEmpty()) return false;

            tokens.add(new RecipeToken(recipeIdString, recipe.get()));
            return true;
        }, '<');
    }

    @Override
    public void registerNodes(NodeRegistrar registrar) {
        registrar.registerNode(
                (parser, recipeToken, tokens) -> new RecipeNode(recipeToken.recipe),
                (token, tokens) -> token instanceof RecipeToken recipe ? recipe : null
        );
    }

    private static class RecipeToken extends Lexer.Token {

        public final Recipe<?> recipe;

        public RecipeToken(String content, Recipe<?> recipe) {
            super(content);
            this.recipe = recipe;
        }
    }

    private class RecipeNode extends Parser.Node {

        private final Recipe<?> recipe;

        public RecipeNode(Recipe<?> recipe) {
            this.recipe = recipe;
        }

        @Override
        @SuppressWarnings("DataFlowIssue")
        protected void visitStart(MarkdownCompiler<?> compiler) {
            var recipeComponent = RecipeExtension.this.bookComponentSource.template(ParentComponent.class, "crafting-recipe");
            var inputGrid = recipeComponent.childById(ParentComponent.class, "input-grid");

            ((RecipeGridAligner<Ingredient>) (inputs, slot, amount, gridX, gridY) -> {
                if (!(inputGrid.children().get(slot) instanceof IngredientComponent ingredient)) return;
                ingredient.ingredient(inputs.next());
            }).alignRecipeToGrid(3, 3, 9, this.recipe, this.recipe.getIngredients().iterator(), 0);

            recipeComponent.childById(ItemComponent.class, "output").stack(this.recipe.getOutput(MinecraftClient.getInstance().world.getRegistryManager()));

            ((OwoUICompiler) compiler).visitComponent(recipeComponent);
        }

        @Override
        protected void visitEnd(MarkdownCompiler<?> compiler) {}
    }

    public static class IngredientComponent extends ItemComponent {

        private @Nullable Ingredient ingredient = null;

        private float time = 0f;
        private int matchingStackIndex;

        public IngredientComponent() {
            super(ItemStack.EMPTY);
            this.setTooltipFromStack(true);
        }

        @Override
        public void update(float delta, int mouseX, int mouseY) {
            super.update(delta, mouseX, mouseY);

            this.time += delta;
            if (this.time >= 20) {
                this.time -= 20;
                this.updateForIngredient();
            }
        }

        private void updateForIngredient() {
            if (this.ingredient != null && this.ingredient.getMatchingStacks().length != 0) {
                this.matchingStackIndex = (this.matchingStackIndex + 1) % this.ingredient.getMatchingStacks().length;
                this.stack(this.ingredient.getMatchingStacks()[this.matchingStackIndex]);
            } else {
                this.matchingStackIndex = 0;
                this.stack(ItemStack.EMPTY);
            }
        }

        public IngredientComponent ingredient(@Nullable Ingredient ingredient) {
            this.ingredient = ingredient;
            this.updateForIngredient();

            return this;
        }

        public @Nullable Ingredient ingredient() {
            return ingredient;
        }
    }

    static {
        UIParsing.registerFactory("lavender.ingredient", element -> new IngredientComponent());
    }
}
