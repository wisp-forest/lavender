package io.wispforest.lavender.book;

import com.google.common.base.Preconditions;
import io.wispforest.lavender.Lavender;
import io.wispforest.lavender.client.BookScreen;
import io.wispforest.owo.nbt.NbtKey;
import io.wispforest.owo.ops.TextOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookItem extends Item {

    public static final NbtKey<Identifier> BOOK_ID = new NbtKey<>("BookId", NbtKey.Type.IDENTIFIER);
    public static final BookItem DYNAMIC_BOOK = new BookItem(null);

    private static final Map<Identifier, BookItem> BOOK_ITEMS = new HashMap<>();

    private final @Nullable Identifier bookId;

    private BookItem(@Nullable Identifier bookId) {
        super(new Item.Settings().maxCount(1));
        this.bookId = bookId;
    }

    public static BookItem registerForBook(@NotNull Identifier bookId) {
        return registerForBook(bookId, bookId);
    }

    public static BookItem registerForBook(@NotNull Identifier bookId, @NotNull Identifier itemId) {
        Preconditions.checkNotNull(bookId, "Book-specific book items must have a non-null book ID");

        var item = new BookItem(bookId);
        Registry.register(Registries.ITEM, itemId, item);
        BOOK_ITEMS.put(bookId, item);
        return item;
    }

    public static Identifier bookIdOf(ItemStack bookStack) {
        if (!(bookStack.getItem() instanceof BookItem book)) return null;
        return book.bookId != null ? book.bookId : bookStack.getOr(BOOK_ID, null);
    }

    public static @Nullable Book bookOf(ItemStack bookStack) {
        var bookId = bookIdOf(bookStack);
        if (bookId == null) return null;

        return BookLoader.get(bookId);
    }

    public static ItemStack itemOf(Book book) {
        var bookItem = BOOK_ITEMS.get(book.id());
        if (bookItem != null) {
            return bookItem.getDefaultStack();
        } else {
            return createDynamic(book);
        }
    }

    public static ItemStack createDynamic(Book book) {
        var stack = DYNAMIC_BOOK.getDefaultStack();
        stack.put(BOOK_ID, book.id());
        return stack;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        var playerStack = user.getStackInHand(hand);

        var bookId = bookIdOf(playerStack);
        if (bookId == null) return TypedActionResult.pass(playerStack);

        var book = BookLoader.get(bookId);
        if (book == null) {
            Lavender.LOGGER.warn("Player {} tried to open unknown book with id '{}'", user.getGameProfile().getName(), bookId);
            return TypedActionResult.pass(playerStack);
        }

        if (!world.isClient) return TypedActionResult.success(playerStack);
        MinecraftClient.getInstance().setScreen(new BookScreen(book));

        return TypedActionResult.success(playerStack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        var bookId = bookIdOf(stack);
        if (bookId == null) {
            tooltip.add(TextOps.withFormatting("⚠ §No associated book", Formatting.RED, Formatting.DARK_GRAY));
        } else {
            var book = BookLoader.get(bookId);
            if (book != null) return;

            tooltip.add(TextOps.withFormatting("⚠ §Unknown book \"" + bookId + "\"", Formatting.RED, Formatting.DARK_GRAY));
        }
    }
}
