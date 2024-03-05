package mod.gottsch.forge.dungeons2.core.decorator;

import com.google.common.collect.Maps;
import mod.gottsch.forge.dungeons2.core.pattern.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Optional;

/**
 * @author Mark Gottschling on Mar 1, 2024
 *
 */
// TODO should be one block provider or a separate provider for each room element?
public class BasicBlockProvider implements IBlockProvider {
    private static final Map<IPatternEnum, Block> REGISTRY = Maps.newHashMap();

    public void set(IPatternEnum pattern, Block block) {
        REGISTRY.put(pattern, block);
    }

    @Override
    public Optional<BlockState> get(IPatternEnum pattern) {
        // TODO get the blockstate by pattern from the internal registry which is loaded by json files
//        BlockState newState = switch (pattern) {
//            case CORNER -> Blocks.POLISHED_ANDESITE.defaultBlockState();
//            default -> Blocks.STONE_BRICKS.defaultBlockState();
//        };
//        return newState;
        Block block = REGISTRY.get(pattern);
        if (block == null) {
            return Optional.empty();
        }
        return Optional.of(block.defaultBlockState());
    }

    @Override
    public Optional<BlockState> get(IPatternEnum pattern, BlockState state) {
        // TODO get the blockstate by pattern from the internal registry
        Block block = REGISTRY.get(pattern);
        if (block == null) {
            return Optional.empty();
        }
        BlockState newState = block.defaultBlockState();
//        // ie this is for things like stairs which will have a certain facing property set etc.
//        BlockState newState = Blocks.POLISHED_ANDESITE.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            newState = copyProperty(state, newState, property);
        }
//        return newState;
        return Optional.of(block.defaultBlockState());
    }

    /**
     *
     * @param pattern
     * @return
     */
    public BlockState get(FloorPattern pattern) {
        Block block = REGISTRY.get(pattern);
        if (block != null) {
            return block.defaultBlockState();
        }
        return null;
//        BlockState newState = switch (pattern) {
//            case CORNER -> ModBlocks.DARK_IRON_GRATE.get().defaultBlockState();
//            case BORDER -> Blocks.POLISHED_ANDESITE.defaultBlockState();
//            // default = FLOOR
//            default -> Blocks.STONE_BRICKS.defaultBlockState();

//        };
//        return newState;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
}
