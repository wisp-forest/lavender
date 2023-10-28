package io.wispforest.lavender.book;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public record Category(
        Identifier id,
        String title,
        ItemStack icon,
        boolean secret,
        int ordinal,
        String content
) implements Book.BookmarkableElement {}
