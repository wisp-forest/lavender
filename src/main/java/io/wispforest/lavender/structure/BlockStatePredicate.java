package io.wispforest.lavender.structure;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.function.Predicate;

/**
 * A predicate used for matching the elements of a structure
 * info against some concrete block state in the world
 * <p>
 * Importantly, it also provides a mechanism for getting a representative
 * sample block state that can be used for the structure preview
 */
public interface BlockStatePredicate extends Predicate<BlockState> {

    BlockStatePredicate NULL_PREDICATE = new BlockStatePredicate() {
        @Override
        public BlockState preview() {
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean test(BlockState blockState) {
            return true;
        }
    };

    BlockStatePredicate AIR_PREDICATE = new BlockStatePredicate() {
        @Override
        public BlockState preview() {
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean test(BlockState blockState) {
            return blockState.isAir();
        }
    };

    /**
     * @return A representative sample state for this predicate. As this function
     * is called every frame the preview is rendered, returning a different sample
     * depending on system time (e.g. to cycle to a block tag) is valid behavior
     */
    BlockState preview();

}
