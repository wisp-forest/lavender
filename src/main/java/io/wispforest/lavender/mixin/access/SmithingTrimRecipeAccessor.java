package io.wispforest.lavender.mixin.access;

import io.wispforest.lavender.pond.SmithingRecipeAccessor;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.SmithingTrimRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmithingTrimRecipe.class)
public interface SmithingTrimRecipeAccessor extends SmithingRecipeAccessor {
    @Accessor("template")
    Ingredient lavender$getTemplate();

    @Accessor("base")
    Ingredient lavender$getBase();

    @Accessor("addition")
    Ingredient lavender$getAddition();
}
