package io.wispforest.lavender.book;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

public record Category(
        Identifier id,
        String title,
        Item icon,
        boolean secret,
        int ordinal,
        String content
) implements Book.BookmarkableElement {}
