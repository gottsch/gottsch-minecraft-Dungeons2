package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;

public interface ICorridorGenerator {
    // TODO could take in some sort of config for the style etc
    void addWallToWorld(ServerLevel level, ICoords coords);

    void addCorridorToWorld(ServerLevel level, ICoords coords);

    void addToWorld(ServerLevel level, Grid2D grid, ICoords spawnCoords);
}
