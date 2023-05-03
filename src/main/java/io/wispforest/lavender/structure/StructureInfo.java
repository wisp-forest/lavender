package io.wispforest.lavender.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BiConsumer;

public class StructureInfo {

    private final BlockStatePredicate[][][] predicates;
    public final int xSize, ySize, zSize;
    public final int nonNullPredicates;

    public StructureInfo(BlockStatePredicate[][][] predicates, int xSize, int ySize, int zSize) {
        this.predicates = predicates;
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;

        var nonNullPredicates = new MutableInt();
        this.forEachPredicate((blockPos, predicate) -> {
            if (predicate == BlockStatePredicate.NULL_PREDICATE) return;
            nonNullPredicates.increment();
        });

        this.nonNullPredicates = nonNullPredicates.intValue();
    }

    // --- iteration ---

    private void forEachPredicate(BiConsumer<BlockPos, BlockStatePredicate> action) {
        this.forEachPredicate(action, BlockRotation.NONE);
    }

    public void forEachPredicate(BiConsumer<BlockPos, BlockStatePredicate> action, BlockRotation rotation) {
        var mutable = new BlockPos.Mutable();

        for (int x = 0; x < predicates.length; x++) {
            for (int y = 0; y < predicates[x].length; y++) {
                for (int z = 0; z < predicates[x][y].length; z++) {

                    switch (rotation) {
                        case CLOCKWISE_90 -> mutable.set(this.zSize - z - 1, y, x);
                        case COUNTERCLOCKWISE_90 -> mutable.set(z, y, this.xSize - x - 1);
                        case CLOCKWISE_180 -> mutable.set(this.xSize - x - 1, y, this.zSize - z - 1);
                        default -> mutable.set(x, y, z);
                    }

                    action.accept(mutable, predicates[x][y][z]);
                }
            }
        }
    }

    // --- validation ---

    public boolean validate(World world, BlockPos anchor) {
        return this.validate(world, anchor, BlockRotation.NONE);
    }

    public boolean validate(World world, BlockPos anchor, BlockRotation rotation) {
        return this.countValidStates(world, anchor, rotation) == this.nonNullPredicates;
    }

    public int countValidStates(World world, BlockPos anchor) {
        return countValidStates(world, anchor, BlockRotation.NONE);
    }

    public int countValidStates(World world, BlockPos anchor, BlockRotation rotation) {
        var validStates = new MutableInt();
        var mutable = new BlockPos.Mutable();

        this.forEachPredicate((pos, predicate) -> {
            if (predicate == BlockStatePredicate.NULL_PREDICATE) return;

            if (predicate.test(world.getBlockState(mutable.set(pos).move(anchor)).rotate(inverse(rotation)))) {
                validStates.increment();
            }
        }, rotation);

        return validStates.intValue();
    }

    // --- utility ---

    public BlockRenderView asBlockRenderView() {
        var world = MinecraftClient.getInstance().world;
        return new BlockRenderView() {
            @Override
            public float getBrightness(Direction direction, boolean shaded) {
                return 1f;
            }

            @Override
            public LightingProvider getLightingProvider() {
                return world.getLightingProvider();
            }

            @Override
            public int getColor(BlockPos pos, ColorResolver colorResolver) {
                return colorResolver.getColor(world.getBiome(pos).value(), pos.getX(), pos.getZ());
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                if (pos.getX() < 0 || pos.getX() >= StructureInfo.this.xSize || pos.getY() < 0 || pos.getY() >= StructureInfo.this.ySize || pos.getZ() < 0 || pos.getZ() >= StructureInfo.this.zSize)
                    return Blocks.AIR.getDefaultState();
                return StructureInfo.this.predicates[pos.getX()][pos.getY()][pos.getZ()].preview();
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return Fluids.EMPTY.getDefaultState();
            }

            @Override
            public int getHeight() {
                return world.getHeight();
            }

            @Override
            public int getBottomY() {
                return world.getBottomY();
            }
        };
    }

    public static BlockRotation inverse(BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> BlockRotation.NONE;
            case CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> BlockRotation.CLOCKWISE_180;
        };
    }

    // --- parsing ---

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static StructureInfo parse(JsonObject json) {
        var keyObject = JsonHelper.getObject(json, "keys");
        var keys = new Char2ObjectOpenHashMap<BlockStatePredicate>();

        for (var entry : keyObject.entrySet()) {
            if (entry.getKey().length() != 1) continue;
            try {
                var result = BlockArgumentParser.blockOrTag(Registries.BLOCK.getReadOnlyWrapper(), entry.getValue().getAsString(), false);
                if (result.left().isPresent()) {
                    var predicate = result.left().get();

                    keys.put(entry.getKey().charAt(0), new BlockStatePredicate() {
                        @Override
                        public BlockState preview() {
                            return predicate.blockState();
                        }

                        @Override
                        public boolean test(BlockState state) {
                            if (state.getBlock() != predicate.blockState().getBlock()) return false;

                            for (var propAndValue : predicate.properties().entrySet()) {
                                if (!state.get(propAndValue.getKey()).equals(propAndValue.getValue())) {
                                    return false;
                                }
                            }

                            return true;
                        }
                    });
                } else {
                    var predicate = result.right().get();

                    var previewStates = new ArrayList<BlockState>();
                    predicate.tag().forEach(registryEntry -> {
                        var block = registryEntry.value();
                        var state = block.getDefaultState();

                        for (var propAndValue : predicate.vagueProperties().entrySet()) {
                            Property prop = block.getStateManager().getProperty(propAndValue.getKey());
                            if (prop == null) return;

                            Optional<Comparable> value = prop.parse(propAndValue.getValue());
                            if (value.isEmpty()) return;

                            state = state.with(prop, value.get());
                        }

                        previewStates.add(state);
                    });

                    keys.put(entry.getKey().charAt(0), new BlockStatePredicate() {
                        @Override
                        public BlockState preview() {
                            if (previewStates.isEmpty()) return Blocks.AIR.getDefaultState();
                            return previewStates.get((int) (System.currentTimeMillis() / 1000 % previewStates.size()));
                        }

                        @Override
                        public boolean test(BlockState state) {
                            if (!state.isIn(predicate.tag())) return false;

                            for (var propAndValue : predicate.vagueProperties().entrySet()) {
                                var prop = state.getBlock().getStateManager().getProperty(propAndValue.getKey());
                                if (prop == null) return false;

                                var expected = prop.parse(propAndValue.getValue());
                                if (expected.isEmpty()) return false;

                                if (!state.get(prop).equals(expected.get())) return false;
                            }

                            return true;
                        }
                    });
                }
            } catch (CommandSyntaxException e) {
                throw new JsonParseException("Failed to parse block state predicate", e);
            }
        }

        var layersArray = JsonHelper.getArray(json, "layers");
        int xSize = 0, ySize = layersArray.size(), zSize = 0;

        for (var element : layersArray) {
            if (!(element instanceof JsonArray layer)) {
                throw new JsonParseException("Every element in the 'layers' array must itself be an array");
            }

            if (zSize == 0) {
                zSize = layer.size();
            } else if (zSize != layer.size()) {
                throw new JsonParseException("Every layer must have the same amount of rows");
            }

            for (var rowElement : layer) {
                if (!rowElement.isJsonPrimitive()) {
                    throw new JsonParseException("Every element in a row must be a primitive");
                }
                if (xSize == 0) {
                    xSize = rowElement.getAsString().length();
                } else if (xSize != rowElement.getAsString().length()) {
                    throw new JsonParseException("Every row must have the same length");
                }
            }
        }

        var result = new BlockStatePredicate[xSize][][];
        for (int x = 0; x < xSize; x++) {
            result[x] = new BlockStatePredicate[ySize][];
            for (int y = 0; y < ySize; y++) {
                result[x][y] = new BlockStatePredicate[zSize];
            }
        }

        for (int y = 0; y < layersArray.size(); y++) {
            var layer = (JsonArray) layersArray.get(y);
            for (int z = 0; z < layer.size(); z++) {
                var row = layer.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    char key = row.charAt(x);

                    BlockStatePredicate predicate;
                    if (key == ' ') {
                        predicate = BlockStatePredicate.NULL_PREDICATE;
                    } else {
                        predicate = keys.get(key);
                        if (predicate == null) throw new JsonParseException("Unknown key '" + key + "'");
                    }

                    result[x][y][z] = predicate;
                }
            }
        }

        return new StructureInfo(result, xSize, ySize, zSize);
    }
}
