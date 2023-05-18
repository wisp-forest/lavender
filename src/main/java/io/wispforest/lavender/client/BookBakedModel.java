package io.wispforest.lavender.client;

import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.book.BookItem;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;


public class BookBakedModel extends ForwardingBakedModel {

    private final ModelOverrideList overrides = new ModelOverrideList() {
        @Nullable
        @Override
        public BakedModel apply(BakedModel model, ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity entity, int seed) {
            var book = BookItem.bookOf(stack);
            if (book == null || book.dynamicBookModel() == null) return model;

            var bookModel = MinecraftClient.getInstance().getBakedModelManager().getModel(new ModelIdentifier(book.dynamicBookModel(), "inventory"));
            return bookModel != MinecraftClient.getInstance().getBakedModelManager().getMissingModel()
                    ? bookModel
                    : model;
        }
    };

    private BookBakedModel(BakedModel parent) {
        this.wrapped = parent;
    }

    @Override
    public ModelOverrideList getOverrides() {
        return this.overrides;
    }

    public static class Unbaked implements UnbakedModel {

        public static final ModelIdentifier BROWN_BOOK_ID = new ModelIdentifier(Lavender.id("brown_book"), "inventory");

        @Override
        public Collection<Identifier> getModelDependencies() {
            return List.of(BROWN_BOOK_ID);
        }

        @Nullable
        @Override
        public BakedModel bake(Baker baker, Function<SpriteIdentifier, Sprite> textureGetter, ModelBakeSettings rotationContainer, Identifier modelId) {
            return new BookBakedModel(baker.bake(BROWN_BOOK_ID, rotationContainer));
        }

        @Override
        public void setParents(Function<Identifier, UnbakedModel> modelLoader) {}
    }
}
