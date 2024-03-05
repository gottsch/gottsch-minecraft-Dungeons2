package mod.gottsch.forge.dungeons2.api;

import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;
import mod.gottsch.forge.dungeons2.core.enums.IRoomElementType;
import mod.gottsch.forge.dungeons2.core.registry.BlockProivderRegistry;
import mod.gottsch.forge.dungeons2.core.registry.DecoratorRegistry;
import mod.gottsch.forge.dungeons2.core.registry.EnumRegistry;
import mod.gottsch.forge.dungeons2.core.registry.PatternRegistry;
import mod.gottsch.forge.gottschcore.enums.IEnum;
import mod.gottsch.forge.gottschcore.enums.IRarity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DungeonsApi {

    public static final String DUNGEON_MOTIF = "dungeonMotif";

    public static void registerMotif(DungeonMotif e) {
        EnumRegistry.register(DUNGEON_MOTIF, e);
    }

    /**
     *
     * @param key
     * @return
     */
    public static Optional<IDungeonMotif> getMotif(String key) {
        IEnum ienum = EnumRegistry.get(DUNGEON_MOTIF, key);
        if (ienum == null) {
            return Optional.empty();
        }
        else {
            return Optional.of((IDungeonMotif) ienum);
        }
    }

    public static List<IDungeonMotif> getMotifs() {
        List<IEnum> enums = EnumRegistry.getValues(DUNGEON_MOTIF);
        return enums.stream().map(e -> (IDungeonMotif)e).collect(Collectors.toList());
    }

    /**
     *
     * @param key
     * @param pattern
     */
    public static void registerPattern(String key, IPatternEnum pattern) {
        PatternRegistry.register(key, pattern);
    }

    /**
     *
     * @param key
     * @return
     */
    public static Optional<IPatternEnum> getPattern(String patternKey, String key) {
        return PatternRegistry.get(patternKey, key);
    }

    public static Optional<IPatternEnum> getFloorPattern(String key) {
        return PatternRegistry.getFloorPattern(key);
    }

    public static List<IRarity> getPatterns(String key) {
        List<IPatternEnum> enums = PatternRegistry.getValues(key);
        return enums.stream().map(e -> (IRarity)e).collect(Collectors.toList());
    }

    public static List<IRoomElementDecorator> getDecorator(IRoomElementType type) {
        return DecoratorRegistry.get(type);
    }

    public static Optional<IBlockProvider> getBlockProvider(IDungeonMotif motif) {
        return BlockProivderRegistry.get(motif);
    }
}
