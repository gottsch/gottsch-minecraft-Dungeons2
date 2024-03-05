package mod.gottsch.forge.dungeons2.core.generator.dungeon.door;

import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLevel;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public interface IDoorGenerator {
    void addToWorld(ServerLevel level, RandomSource random, DungeonLevel dungeonLevel, ICoords spawnCoords, IDungeonMotif motif);
}
