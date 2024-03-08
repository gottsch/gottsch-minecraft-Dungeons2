package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import mod.gottsch.forge.dungeons2.core.collection.Array2D;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.pattern.ceiling.CeilingPattern;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author Mark Gottschling on Mar 6, 2024
 */
public class BasicCeilingGenerator implements IDungeonCeilingGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    @Override
    public Array2D<Integer> addToWorld(ServerLevel level, RandomSource random, IRoom room, ICoords normalSpawnCoords, IDungeonMotif motif) {
        // get the size of the footprint
        Coords2D size = new Coords2D(room.getWidth(), room.getDepth());
        // create a grid
        Array2D<Integer> grid = new Array2D<>(Integer.class, size.getX(), size.getY());

        IBlockProvider blockProvider = IBlockProvider.get(motif);
        int y = room.getHeight() -1;
        for (int x = 1; x < room.getWidth() -1; x++) {
            for (int z = 1; z < room.getDepth() -1; z++) {
                level.setBlockAndUpdate(normalSpawnCoords.add(room.getCoords()).add(x, y, z).toPos(), blockProvider.get(CeilingPattern.CEILING).orElse(DEFAULT));
            }
        }
        return grid;
    }
}
