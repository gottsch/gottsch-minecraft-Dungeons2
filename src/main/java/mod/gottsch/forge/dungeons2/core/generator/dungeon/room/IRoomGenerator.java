package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLevel;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonMotif;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;

public interface IRoomGenerator {
    public void addToWorld(ServerLevel level, DungeonLevel dungeonLevel, ICoords spawnCoords, DungeonMotif motif);
}
