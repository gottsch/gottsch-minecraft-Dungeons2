package mod.gottsch.forge.dungeons2.api;

import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.registry.EnumRegistry;
import mod.gottsch.forge.gottschcore.enums.IEnum;

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

}
