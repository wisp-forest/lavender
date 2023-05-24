package io.wispforest.lavender.pond;

import net.minecraft.recipe.Ingredient;

public interface SmithingRecipeAccessor {
    default Ingredient lavender$getTemplate() {
        return Ingredient.EMPTY;
    }

    Ingredient lavender$getBase();

    Ingredient lavender$getAddition();
}
