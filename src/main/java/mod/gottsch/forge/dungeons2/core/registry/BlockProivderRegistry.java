package mod.gottsch.forge.dungeons2.core.registry;

import com.google.common.collect.Maps;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;

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
    private static final Map<IDungeonMotif, IBlockProvider> REGISTRY = Maps.newHashMap();

    private BlockProivderRegistry() { }

    public static void register(IDungeonMotif motif, IBlockProvider provider) {
        if (motif != null) {
            REGISTRY.put(motif, provider);
        }
    }

    public static Optional<IBlockProvider> get(IDungeonMotif motif) {
        if (REGISTRY.containsKey(motif)) {
            return Optional.of(REGISTRY.get(motif));
        }
        return Optional.empty();
    }

    public static List<IBlockProvider> getValues() {
        return new ArrayList<>(REGISTRY.values());
    }
}
