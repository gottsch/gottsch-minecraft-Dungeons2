package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.collection.Array2D;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.pattern.wall.WallPattern;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author Mark Gottschling on Mar 6, 2024
 */
public class BasicWallGenerator implements IDungeonWallGenerator  {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    @Override
    public Array2D<Integer> addToWorld(ServerLevel level, RandomSource random, IRoom room, ICoords normalSpawnCoords, IDungeonMotif motif) {
        // get the size of the footprint
        Coords2D size = new Coords2D(room.getWidth(), room.getDepth());
        // create a grid
        Array2D<Integer> grid = new Array2D<>(Integer.class, size.getX(), size.getY());

        // NOTE did it this way to build without using conditional statements
        IBlockProvider blockProvider = IBlockProvider.get(motif);
        // TODO move the corners to the decorators
        int[] xx = {0, room.getWidth()-1};
        for (int x: xx) {
            for (int z = 0; z < room.getDepth(); z++) {
                for (int y = 1; y < room.getHeight() - 1; y++) {
                    level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(),
                            blockProvider.get(WallPattern.WALL).orElse(DEFAULT));
                    grid.put(x, z, 1);
                }
            }
        }

        int[] zz = {0, room.getDepth()-1};
        for (int z : zz) {
            for (int x = 0; x < room.getWidth(); x++) {
                for (int y = 1; y < room.getHeight() - 1; y++) {
                    level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(),
                            blockProvider.get(WallPattern.WALL).orElse(DEFAULT));
                    grid.put(x, z, 1);
                }
            }
        }

        // air
        for (int x = 1; x < room.getWidth() - 1; x++) {
            for (int z = 1; z < room.getDepth() - 1; z++) {
                for (int y = 1; y < room.getHeight() -1; y++) {
                    level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), Blocks.AIR.defaultBlockState());
                    grid.put(x, z, 0);
                }
            }
        }
        return grid;
    }
}
