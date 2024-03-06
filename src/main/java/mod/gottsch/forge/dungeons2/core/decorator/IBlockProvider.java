package mod.gottsch.forge.dungeons2.core.decorator;

import mod.gottsch.forge.dungeons2.api.DungeonsApi;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * @author Mark Gottschling on Mar 3, 2024
 */
public interface IBlockProvider {
    /**
     * convenience method to get block provider by motif, with a fallback to the basic block provider.
     *
     * @param motif
     * @return
     */
    public static IBlockProvider get(IDungeonMotif motif) {
        Optional<IBlockProvider> blockProviderOptional = DungeonsApi.getBlockProvider(motif);
        return blockProviderOptional.orElseGet(BlockProvider::new);
    }

    public Optional<BlockState> get(IPatternEnum pattern);

    public void set(IPatternEnum pattern, Block block);

    Optional<BlockState> get(IPatternEnum pattern, BlockState state);
}
