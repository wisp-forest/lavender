package io.wispforest.lavender.book;

import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public record Entry(Identifier id, String title, Item icon, String content) {}
