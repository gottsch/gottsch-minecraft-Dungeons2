package mod.gottsch.forge.dungeons2.core.decorator;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author Mark Gottschling on Mar 1, 2024
 *
 */
public class BasicBlockProvider {
    public BlockState get(WallPattern pattern) {
        // TODO get the blockstate by pattern from the internal registry
        return Blocks.POLISHED_ANDESITE.defaultBlockState();
    }

    public BlockState get(WallPattern pattern, BlockState state) {
        // TODO get the blockstate by pattern from the internal registry
        // TODO copy the state from the provided block state to the new one
        // ie this is for things like stairs which will have a certain facing property set etc.
        return Blocks.POLISHED_ANDESITE.defaultBlockState();
    }
}
