package mod.gottsch.forge.dungeons2.core.decorator.floor.border;

import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.pattern.floor.border.FloorBorderPattern;
import mod.gottsch.forge.gottschcore.block.IFacingBlock;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import static mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns.FLOOR_BORDER_PATTERN;

/**
 * @author Mark Gottschling on March 3, 2024
 *
 */
public class DefaultBorderDecorator implements IRoomElementDecorator {
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
        BlockSet blockSet = BlockProvider.get(motif, FLOOR_BORDER_PATTERN, random);
        int y = 0;

        BlockState blockState = blockSet.get(FloorBorderPattern.BORDER).orElse(DEFAULT);
        BlockState northState = blockState;
        BlockState eastState = blockState;
        if (blockState.getBlock() instanceof IFacingBlock) {
            northState = blockState.setValue(IFacingBlock.FACING, Direction.NORTH);
            eastState = blockState.setValue(IFacingBlock.FACING, Direction.EAST);
        }
        if (blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            northState = blockState.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            eastState = blockState.setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        }

        for (int x = 1; x < room.getWidth() - 1; x++) {
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, 1).toPos(), northState);
//            level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, 1).toPos(), blockProvider.get(FloorBorderPattern.BORDER).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, room.getDepth() - 2).toPos(), northState);
        }
        for (int z = 2; z < room.getDepth() - 2; z++) {
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, z).toPos(), eastState);
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, z).toPos(), eastState);
        }

        return layout;
    }
}
