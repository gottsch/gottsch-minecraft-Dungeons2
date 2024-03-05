package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;

public interface ICorridorGenerator {
    // TODO could take in some sort of config for the style etc - Motif; this should be known on constructor
    void addWallToWorld(ServerLevel level, ICoords coords, IDungeonMotif motif);

    void addCorridorToWorld(ServerLevel level, ICoords coords, IDungeonMotif motif);

    void addToWorld(ServerLevel level, Grid2D grid, ICoords spawnCoords, IDungeonMotif motif);
}
