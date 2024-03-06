package mod.gottsch.forge.dungeons2.core.registry;

import com.google.common.collect.Maps;
import mod.gottsch.forge.dungeons2.core.config.BlockProviderConfiguration;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;
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
    private static final Map<String, IBlockProvider> REGISTRY = Maps.newHashMap();

    private BlockProivderRegistry() { }

    public static void register(IDungeonMotif motif, IBlockProvider provider) {
        if (motif != null) {
            register(motif.getName(), provider);
        }
    }

    // TODO go through all code and change method signature to use String instead of IDungeonMotif.
    // that way, one can just add to the blockproviders config and not touch any code
    public static void register(String motif, IBlockProvider provider) {
        if (motif != null) {
            REGISTRY.put(motif.trim().toLowerCase(), provider);
        }
    }

    public static boolean isRegistered(String key) {
        return REGISTRY.containsKey(key.toLowerCase());
    }

    public static Optional<IBlockProvider> get(IDungeonMotif motif) {
        return get(motif.getName());
    }

    public static Optional<IBlockProvider> get(String key) {
        if (isRegistered(key)) {
            return Optional.of(REGISTRY.get(key.toLowerCase()));
        }
        return Optional.empty();
    }

    // remember not to restrict motifs to enums
    public static void register(List<BlockProviderConfiguration.Motif> motifs) {
        // for each motif, check if it is registered
        for (BlockProviderConfiguration.Motif motif : motifs) {
            IBlockProvider blockProvider = new BlockProvider();
            for (BlockProviderConfiguration.Pattern pattern : motif.getPatterns()) {
                for (BlockProviderConfiguration.PatternElement element : pattern.getElements()) {
                    // check if element has been registered
                    Optional<IPatternEnum> patternEnum = PatternRegistry.get(pattern.getName(), element.getName());
                    patternEnum.ifPresent(penum -> blockProvider.set(penum, BuiltInRegistries.BLOCK.get(new ResourceLocation(element.getBlock()))));
                }
            }
            BlockProivderRegistry.register(motif.getName().toLowerCase(), blockProvider);
        }
    }

    public static List<IBlockProvider> getValues() {
        return new ArrayList<>(REGISTRY.values());
    }

}
