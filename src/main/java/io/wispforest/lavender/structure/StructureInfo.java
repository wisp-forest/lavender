package io.wispforest.lavender.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.BlockPos;

import java.util.function.BiConsumer;

public class StructureInfo {

    private final BlockStatePredicate[][][] predicates;

    public StructureInfo(BlockStatePredicate[][][] predicates, int xSize, int ySize, int zSize) {
        this.predicates = predicates;
    }

    public void forEachPreviewState(BiConsumer<BlockPos, BlockState> action) {
        var mutable = new BlockPos.Mutable();

        for (int x = 0; x < predicates.length; x++) {
            for (int y = 0; y < predicates[x].length; y++) {
                for (int z = 0; z < predicates[x][y].length; z++) {
                    mutable.set(x, y, z);
                    action.accept(mutable, predicates[x][y][z].preview());
                }
            }
        }
    }

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
                            if (state.getBlock() == predicate.blockState().getBlock()) return false;

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

                    keys.put(entry.getKey().charAt(0), new BlockStatePredicate() {
                        @Override
                        public BlockState preview() {
                            return predicate.tag().get((int) (System.currentTimeMillis() / 1000 % predicate.tag().size())).value().getDefaultState();
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
                        predicate = new BlockStatePredicate() {
                            @Override
                            public BlockState preview() {
                                return Blocks.AIR.getDefaultState();
                            }

                            @Override
                            public boolean test(BlockState state) {
                                return true;
                            }
                        };
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
