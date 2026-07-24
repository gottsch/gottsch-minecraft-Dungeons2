package mod.gottsch.forge.dungeons2.core.registry;

import com.google.common.collect.Maps;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.decorator.data.BlockProviderDefinition;
import mod.gottsch.forge.dungeons2.core.decorator.data.BlockSetDefinition;
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

    /**
     * Drops every registered provider. Used by the datapack reload listener so a
     * {@code /reload} (or a re-loaded datapack) rebuilds the palette from scratch
     * instead of accumulating stale block sets.
     */
    public static void clear() {
        REGISTRY.clear();
    }

    /**
     * Populates the registry from datapack {@link BlockProviderDefinition}s keyed by
     * motif (the JSON file name under {@code data/<ns>/block_provider/}). The
     * datapack-JSON analogue of {@link #load(List)}: same pattern-element resolution
     * via {@link PatternRegistry}, same tolerant block lookup (an unknown / absent-mod
     * block id resolves to air rather than failing the file).
     *
     * @param byMotif motif id -> its palette definition
     */
    public static void loadDefinitions(Map<String, BlockProviderDefinition> byMotif) {
        for (Map.Entry<String, BlockProviderDefinition> motifEntry : byMotif.entrySet()) {
            String motif = motifEntry.getKey();
            BlockProvider blockProvider = get(motif).orElseGet(() -> {
                BlockProvider created = new BlockProvider();
                register(motif, created);
                return created;
            });

            motifEntry.getValue().patterns().forEach((patternId, blockSetDefs) -> {
                for (BlockSetDefinition blockSetDef : blockSetDefs) {
                    BlockSet blockSet = new BlockSet(blockSetDef.id());
                    blockSetDef.elements().forEach((elementId, blockRl) -> {
                        Optional<IPatternEnum> patternEnum = PatternRegistry.get(patternId, elementId);
                        patternEnum.ifPresent(penum ->
                                blockSet.set(penum, BuiltInRegistries.BLOCK.get(blockRl)));
                    });
                    blockProvider.register(patternId, blockSet);
                }
            });
        }
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

    public static List<BlockProvider> getValues() {
        return new ArrayList<>(REGISTRY.values());
    }

}
