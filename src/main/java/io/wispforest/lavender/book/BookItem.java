package io.wispforest.lavender.book;

import io.wispforest.lavender.client.BookScreen;
import io.wispforest.owo.nbt.NbtKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class BookItem extends Item {

    private static final NbtKey<Identifier> BOOK_ID = new NbtKey<>("BookId", NbtKey.Type.IDENTIFIER);

    public BookItem() {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        var playerStack = user.getStackInHand(hand);
        if (!playerStack.has(BOOK_ID)) return TypedActionResult.pass(playerStack);

        var book = BookLoader.get(playerStack.get(BOOK_ID));
        if (book == null) return TypedActionResult.pass(playerStack);

        if (!world.isClient) return TypedActionResult.success(playerStack);
        MinecraftClient.getInstance().setScreen(new BookScreen(book));

        return TypedActionResult.success(playerStack);
    }
}
