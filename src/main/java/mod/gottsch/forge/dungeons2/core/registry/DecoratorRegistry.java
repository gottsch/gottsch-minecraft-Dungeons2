package mod.gottsch.forge.dungeons2.core.registry;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.IRoomElementType;

import java.util.List;

/**
 *
 * @author Mark Gottschling on Mar 3, 2024
 *
 */

public class DecoratorRegistry {
    private static final Multimap<IRoomElementType, IRoomElementDecorator> REGISTRY = ArrayListMultimap.create();

    private DecoratorRegistry() { }


    public static void register(IRoomElementType key, IRoomElementDecorator decorator) {
        if (key != null) {
            REGISTRY.put(key, decorator);
        }
    }

    public static List<IRoomElementDecorator> get(IRoomElementType key) {
        IRoomElementDecorator decorator;
            return (List<IRoomElementDecorator>) REGISTRY.get(key);
    }

//    public static List<IBlockProvider> getValues() {
//        return new ArrayList<>(REGISTRY.values());
//    }
}
