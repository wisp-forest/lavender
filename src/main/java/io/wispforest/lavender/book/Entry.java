package io.wispforest.lavender.book;

import com.google.common.collect.ImmutableSet;
import io.wispforest.lavender.mixin.ClientAdvancementManagerAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record Entry(Identifier id, @Nullable Identifier category, String title, Item icon, boolean secret,
                    ImmutableSet<Identifier> requiredAdvancements, ImmutableSet<Item> associatedItems, String content) implements Book.BookmarkableElement {

    public boolean canPlayerView(ClientPlayerEntity player) {
        var advancementHandler = player.networkHandler.getAdvancementHandler();

        for (var advancementId : this.requiredAdvancements) {
            var advancement = advancementHandler.getManager().get(advancementId);
            if (advancement == null) return false;

            var progress = ((ClientAdvancementManagerAccessor) advancementHandler).lavender$getAdvancementProgresses().get(advancement);
            if (progress == null || !progress.isDone()) return false;
        }

        return true;
    }

}
