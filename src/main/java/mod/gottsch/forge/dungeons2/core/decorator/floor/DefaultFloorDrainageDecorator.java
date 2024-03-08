package mod.gottsch.forge.dungeons2.core.decorator.floor;

import mod.gottsch.forge.dungeons2.api.DungeonsApi;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IRoomElementType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.pattern.IPatternEnum;
import mod.gottsch.forge.dungeons2.core.pattern.floor.FloorDrainagePattern;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * @author Mark Gottschling on March 6, 2024
 *
 */
public class DefaultFloorDrainageDecorator implements IRoomElementDecorator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    public Grid2D decorate(ServerLevel level, RandomSource random, Grid2D layout, IRoom room, ICoords coords, IDungeonMotif motif) {

        IBlockProvider blockProvider = IBlockProvider.get(motif);

        // get the size of the footprint
        Coords2D size = new Coords2D(room.getWidth(), room.getDepth());
        // check if room is big enough to use a padded border


        int y = 0;

        IPatternEnum pattern = (random.nextDouble() < 0.5) ? FloorDrainagePattern.CORNER : FloorDrainagePattern.ALTERNATE_CORNER;

        // add corners
        if (random.nextDouble() < 0.25) {
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, 1).toPos(), blockProvider.get(pattern).orElse(DEFAULT));
//            layout.put(1, 1, 1);
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, 1).toPos(), blockProvider.get(pattern).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, room.getDepth() - 2).toPos(), blockProvider.get(pattern).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, room.getDepth() - 2).toPos(), blockProvider.get(pattern).orElse(DEFAULT));
        }

        // add center
        if (random.nextDouble() < 0.25) {
            // center
            Coords2D center = new Coords2D((room.getWidth()-1)/2, (room.getDepth()-1)/2);
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(center.getX(), y, center.getY()).toPos(), blockProvider.get(pattern == FloorDrainagePattern.CORNER ? FloorDrainagePattern.CENTER : FloorDrainagePattern.ALTERNATE_CENTER).orElse(DEFAULT));

        }

        // TODO add gutters

        return layout;
    }

    private Optional<IRoomElementDecorator> getDecorator(RandomSource random, IRoomElementType type) {
     List<IRoomElementDecorator> decoratorList = DungeonsApi.getDecorators(type);
        if (decoratorList.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(decoratorList.get(random.nextInt(decoratorList.size())));
    }
}
