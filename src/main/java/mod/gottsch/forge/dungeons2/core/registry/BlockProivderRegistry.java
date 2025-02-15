package mod.gottsch.forge.dungeons2.core.registry;

import com.google.common.collect.Maps;
import mod.gottsch.forge.dungeons2.core.config.BlockProviderConfiguration;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Mark Gottschling on Mar 3, 2024
 *
 */

public class BlockProivderRegistry {
    private static final Map<String, BlockProvider> REGISTRY = Maps.newHashMap();

    private BlockProivderRegistry() { }

    /**
     * Register a BlockProvider by a Motif.
     * A BlockProvider must be registered to the registry before BlockSets can be loaded.
     * @param motif
     * @param provider
     */
    public static void register(IDungeonMotif motif, BlockProvider provider) {
        if (motif != null) {
            register(motif.getName(), provider);
        }
    }

    // TODO go through all code and change method signature to use String instead of IDungeonMotif.
    // that way, one can just add to the blockproviders config and not touch any code
    public static void register(String motif, BlockProvider provider) {
        if (motif != null) {
            REGISTRY.put(motif.trim().toLowerCase(), provider);
        }
    }

    public static boolean isRegistered(String key) {
        return REGISTRY.containsKey(key.toLowerCase());
    }

    public static Optional<BlockProvider> get(IDungeonMotif motif) {
        return get(motif.getName());
    }

    public static Optional<BlockProvider> get(String key) {
        if (isRegistered(key)) {
            return Optional.of(REGISTRY.get(key.toLowerCase()));
        }
        return Optional.empty();
    }

    // NOTE remember not to restrict motifs to enums

    /**
     * Loads BlockSets to a BlockProvider from the config
     * @param blockSetConfigs
     */
    public static void load(List<BlockProviderConfiguration.BlockSet> blockSetConfigs) {
        for (BlockProviderConfiguration.BlockSet blockSetConfig : blockSetConfigs) {
            Optional<BlockProvider> blockProviderOptional = BlockProivderRegistry.get(blockSetConfig.getMotif());
            BlockProvider blockProvider;
            if (blockProviderOptional.isEmpty()) {
                blockProvider = new BlockProvider();
                BlockProivderRegistry.register(blockSetConfig.getMotif(), blockProvider);
            } else {
                blockProvider = blockProviderOptional.get();
            }

            // create a new block set
            BlockSet blockSet = new BlockSet(blockSetConfig.getId());
            for (BlockProviderConfiguration.PatternElement element : blockSetConfig.getElements()) {
                // check if element has been registered
                Optional<IPatternEnum> patternEnum = PatternRegistry.get(blockSetConfig.getPattern(), element.getId());
                patternEnum.ifPresent(penum -> blockSet.set(penum, BuiltInRegistries.BLOCK.get(new ResourceLocation(element.getBlock()))));
            }
            // register the blockset to the pattern name
            blockProvider.register(blockSetConfig.getPattern(), blockSet);
        }
    }

    public static List<BlockProvider> getValues() {
        return new ArrayList<>(REGISTRY.values());
    }

}
