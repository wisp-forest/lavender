package io.wispforest.lavender.structure;

import net.minecraft.block.BlockState;

import java.util.function.Predicate;

public interface BlockStatePredicate extends Predicate<BlockState> {

    BlockState preview();

}
