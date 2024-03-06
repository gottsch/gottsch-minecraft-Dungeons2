package mod.gottsch.forge.dungeons2.core.decorator.floor.border;

import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.pattern.floor.border.FloorBorderPattern;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * @author Mark Gottschling on March 3, 2024
 *
 */
public class DefaultPaddedBorderDecorator implements IRoomElementDecorator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    public Grid2D decorate(ServerLevel level, RandomSource random, Grid2D layout, IRoom room, ICoords coords, IDungeonMotif motif) {
        IBlockProvider blockProvider = IBlockProvider.get(motif);

        int y = 0;

        for (int x = 2; x < room.getWidth() - 2; x++) {
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, 2).toPos(), blockProvider.get(FloorBorderPattern.BORDER).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, room.getDepth() - 3).toPos(), blockProvider.get(FloorBorderPattern.BORDER).orElse(DEFAULT));
        }
        for (int z = 3; z < room.getDepth() - 3; z++) {
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(2, y, z).toPos(), blockProvider.get(FloorBorderPattern.BORDER).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 3, y, z).toPos(), blockProvider.get(FloorBorderPattern.BORDER).orElse(DEFAULT));
        }

        return layout;
    }
}