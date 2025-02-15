package mod.gottsch.forge.dungeons2.core.decorator;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;
import mod.gottsch.forge.dungeons2.core.registry.BlockProivderRegistry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * @author Mark Gottschling on Mar 1, 2024
 *
 */
public class BlockProvider {

    private final Multimap<String, BlockSet> registry = ArrayListMultimap.create();

    /**
     * Convenience method to get a concrete BlockProvider instead of an Optional.
     * @param motif
     * @return
     */
    public static BlockProvider get(IDungeonMotif motif) {
        Optional<BlockProvider> blockProviderOptional = BlockProivderRegistry.get(motif);
        return blockProviderOptional.orElseGet(BlockProvider::new);
    }

    /**
     * Convenience method to get a concrete BlockSet instead of an Optional.
     * @param random
     * @param motif
     * @param pattern
     * @return
     */
    public static BlockSet get(IDungeonMotif motif, String pattern, RandomSource random) {
        return BlockProvider.get(motif).get(random, pattern).orElseGet(BlockSet::new);
    }

    public void register(String patternId, BlockSet blockSet) {
        registry.put(patternId.toLowerCase(), blockSet);
    }

    public List<BlockSet> getAll(String patternId) {
        return (List<BlockSet>) registry.get(patternId.toLowerCase());
    }

    public Optional<BlockSet> get(String patternId, String blockSetId) {
        if (registry.containsKey(patternId.toLowerCase())) {
            List<BlockSet> blockSets = getAll(patternId);
            for (BlockSet blockSet : blockSets) {
                if (blockSet.getId().equalsIgnoreCase(blockSetId)) {
                    return Optional.of(blockSet);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<BlockSet> get(RandomSource random, String patternId) {
        if (registry.containsKey(patternId.toLowerCase())) {
            List<BlockSet> blockSets = getAll(patternId);
            return Optional.of(blockSets.get(random.nextInt(blockSets.size())));
        }
        return Optional.empty();
    }

    public Optional<BlockState> get(RandomSource random, String patternId, IPatternEnum pattern) {
        if (registry.containsKey(patternId.toLowerCase())) {
            List<BlockSet> blockSets = getAll(patternId);
            BlockSet blockSet = blockSets.get(random.nextInt(blockSets.size()));
            return blockSet.get(pattern);
        }
        return Optional.empty();
    }
}
