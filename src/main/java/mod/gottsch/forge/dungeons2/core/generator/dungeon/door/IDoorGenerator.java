package mod.gottsch.forge.dungeons2.core.generator.dungeon.door;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.DungeonLevel;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;

public interface IDoorGenerator {
    void addToWorld(ServerLevel level, DungeonLevel dungeonLevel, ICoords spawnCoords);
}
