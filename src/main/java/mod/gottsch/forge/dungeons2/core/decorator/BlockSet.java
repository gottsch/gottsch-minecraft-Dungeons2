package mod.gottsch.forge.dungeons2.core.decorator;

import com.google.common.collect.Maps;
import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Optional;

public class BlockSet {
    private String id;
    private final Map<IPatternEnum, Block> registry = Maps.newHashMap();

    public BlockSet() {}
    public BlockSet(String id) {
        this.id = id;
    }

    public void set(IPatternEnum pattern, Block block) {
        registry.put(pattern, block);
    }

    public Optional<BlockState> get(IPatternEnum pattern) {
        Block block = registry.get(pattern);
        if (block == null) {
            return Optional.empty();
        }
        return Optional.of(block.defaultBlockState());
    }

    public Optional<BlockState> get(IPatternEnum pattern, BlockState state) {
        // TODO get the blockstate by pattern from the internal registry
        Block block = registry.get(pattern);
        if (block == null) {
            return Optional.empty();
        }
        BlockState newState = block.defaultBlockState().getBlock().withPropertiesOf(state);
//        // ie this is for things like stairs which will have a certain facing property set etc.
//        BlockState newState = Blocks.POLISHED_ANDESITE.defaultBlockState();
//        for (Property<?> property : state.getProperties()) {
//            newState = copyProperty(state, newState, property);
//        }
//        return newState;
        return Optional.of(newState);
    }

    // TODO need to create a copyProperties method that doesn't get block.defaultBlockState first.
    // ie pass in the current and desired blockstates then copy props.

//    /**
//     *
//     * @param pattern
//     * @return
//     */
//    public BlockState get(FloorPattern pattern) {
//        Block block = registry.get(pattern);
//        if (block != null) {
//            return block.defaultBlockState();
//        }
//        return null;
//    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
}
