package mod.gottsch.forge.dungeons2.core.decorator.floor.border;

import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.pattern.floor.border.FloorBorderPattern;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author Mark Gottschling on March 3, 2024
 *
 */
public class CrossBorderDecorator extends DefaultBorderDecorator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    /**
     *
     * @param level
     * @param random
     * @param layout
     * @param room
     * @param coords
     * @param motif
     * @return
     */
    @Override
    public Grid2D decorate(ServerLevel level, RandomSource random, Grid2D layout, IRoom room, ICoords coords, IDungeonMotif motif) {
        IBlockProvider blockProvider = IBlockProvider.get(motif);
        // decorate default border
        Grid2D g = super.decorate(level, random, layout, room, coords, motif);

        // add cross
        if (room.getWidth() > 5 && room.getDepth() > 5) {
            int y = 0;

            // center
            Coords2D center = new Coords2D((room.getWidth()-1)/2, (room.getDepth()-1)/2);

            for (int x = 2; x < room.getWidth()-2; x++) {
                level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, center.getY()).toPos(), blockProvider.get(FloorBorderPattern.BORDER).orElse(DEFAULT));
            }

            for (int z = 2; z < room.getDepth()-2; z++) {
                level.setBlockAndUpdate(coords.add(room.getCoords()).add(center.getX(), y, z).toPos(), blockProvider.get(FloorBorderPattern.BORDER).orElse(DEFAULT));
            }

            if (RandomHelper.checkProbability(random, 50)) {
                level.setBlockAndUpdate(coords.add(room.getCoords()).add(center.getX(), y, center.getY()).toPos(), blockProvider.get(FloorBorderPattern.CENTER).orElse(DEFAULT));
            }

        }
        return layout;
    }
}
