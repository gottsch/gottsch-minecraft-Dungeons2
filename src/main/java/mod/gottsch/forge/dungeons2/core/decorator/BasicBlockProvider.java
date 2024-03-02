package mod.gottsch.forge.dungeons2.core.decorator;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * @author Mark Gottschling on Mar 1, 2024
 *
 */
public class BasicBlockProvider {
    public BlockState get(WallPattern pattern) {
        // TODO get the blockstate by pattern from the internal registry
        BlockState newState = switch (pattern) {
            case CORNER -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
        return newState;
    }

    public BlockState get(WallPattern pattern, BlockState state) {
        // TODO get the blockstate by pattern from the internal registry

        // ie this is for things like stairs which will have a certain facing property set etc.
        BlockState newState = Blocks.POLISHED_ANDESITE.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            newState = copyProperty(state, newState, property);
        }
        return newState;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
}
