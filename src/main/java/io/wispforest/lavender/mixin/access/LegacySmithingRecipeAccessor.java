package io.wispforest.lavender.mixin.access;

import io.wispforest.lavender.pond.SmithingRecipeAccessor;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.LegacySmithingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LegacySmithingRecipe.class)
public interface LegacySmithingRecipeAccessor extends SmithingRecipeAccessor {
    @Accessor("base")
    Ingredient lavender$getBase();

    @Accessor("addition")
    Ingredient lavender$getAddition();
}
