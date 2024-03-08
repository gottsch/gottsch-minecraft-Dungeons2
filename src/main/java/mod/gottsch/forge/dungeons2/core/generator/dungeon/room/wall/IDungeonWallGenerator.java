package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.collection.Array2D;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public interface IDungeonWallGenerator {
    public Array2D<Integer> addToWorld(ServerLevel level, RandomSource random, IRoom room, ICoords normalSpawnCoords, IDungeonMotif motif);
}
