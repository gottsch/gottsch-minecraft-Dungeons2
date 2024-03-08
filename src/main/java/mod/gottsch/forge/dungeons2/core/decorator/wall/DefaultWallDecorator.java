package mod.gottsch.forge.dungeons2.core.decorator.wall;

import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
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
public class DefaultWallDecorator implements IRoomElementDecorator {
    // TODO better way for the default. this won't make sense for a different motif
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();
    @Override
    public Grid2D decorate(ServerLevel level, RandomSource random, Grid2D layout, IRoom room, ICoords coords, IDungeonMotif motif) {
        IBlockProvider blockProvider = IBlockProvider.get(motif);

        Coords2D size = new Coords2D(room.getWidth(), room.getDepth());

        // decorate the corner stones
        int[] yy = {1, room.getHeight() -2};

        int[] xx = {1, room.getWidth() -2};
        int[] zz = {0, room.getDepth() -1};
        for (int x : xx) {
            for (int z : zz) {
                for (int y : yy) {
                    addCorners(level, coords, room, x, y, z, blockProvider);
                }
            }
        }

        // TODO add ledges

        // TODO if double wall add pattern

        // TODO add windows


        xx = new int[]{0, room.getWidth() - 1};
        zz = new int[]{1, room.getDepth() - 2};
        for (int x : xx) {
            for (int z : zz) {
                for (int y : yy) {
                    addCorners(level, coords, room, x, y, z, blockProvider);
                }
            }
        }
        return layout;
    }

    private void addCorners(ServerLevel level, ICoords coords, IRoom room, int x, int y, int z, IBlockProvider blockProvider) {
        level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, z).toPos(), blockProvider.get(WallPattern.CORNER).orElse(DEFAULT));
    }
}
