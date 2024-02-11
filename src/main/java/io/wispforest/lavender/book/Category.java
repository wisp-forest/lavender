package io.wispforest.lavender.book;

import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public record Category(
        Identifier id,
        String title,
        Function<Sizing, Component> iconFactory,
        boolean secret,
        int ordinal,
        String content
) implements Book.BookmarkableElement {}
