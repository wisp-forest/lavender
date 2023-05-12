package io.wispforest.lavender.book;

import com.google.common.collect.ImmutableSet;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record Entry(Identifier id, @Nullable Identifier category, String title, Item icon, ImmutableSet<Item> associatedItems, String content) {}
