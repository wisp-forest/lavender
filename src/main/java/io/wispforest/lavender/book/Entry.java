package io.wispforest.lavender.book;

import net.minecraft.item.Item;
import net.minecraft.text.Text;

public record Entry(String title, Item icon, String content) {}
