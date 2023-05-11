package io.wispforest.lavender.book;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

public record Category(Identifier id, Item icon, String content) {}
