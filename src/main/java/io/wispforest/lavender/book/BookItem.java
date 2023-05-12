package io.wispforest.lavender.book;

import io.wispforest.lavender.Lavender;
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
import org.jetbrains.annotations.Nullable;

public class BookItem extends Item {

    public static final NbtKey<Identifier> BOOK_ID = new NbtKey<>("BookId", NbtKey.Type.IDENTIFIER);

    public BookItem() {
        super(new Item.Settings().maxCount(1));
    }

    public static @Nullable Book bookOf(ItemStack bookStack) {
        if (!(bookStack.getItem() instanceof BookItem) || !bookStack.has(BOOK_ID)) return null;
        return BookLoader.get(bookStack.get(BOOK_ID));
    }

    public static ItemStack create(Book book) {
        var stack = Lavender.BOOK.getDefaultStack();
        stack.put(BOOK_ID, book.id());
        return stack;
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
