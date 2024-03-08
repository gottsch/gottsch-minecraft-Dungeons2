package mod.gottsch.forge.dungeons2.core.decorator.floor;

import mod.gottsch.forge.dungeons2.api.DungeonsApi;
import mod.gottsch.forge.dungeons2.core.decorator.IBlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.IRoomElementDecorator;
import mod.gottsch.forge.dungeons2.core.enums.FloorElementType;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IRoomElementType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorProperties;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * @author Mark Gottschling on March 2, 2024
 *
 */
public class DefaultFloorDecorator implements IRoomElementDecorator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    public Grid2D decorate(ServerLevel level, RandomSource random, Grid2D layout, IRoom room, ICoords coords, IDungeonMotif motif) {

        FloorProperties properties = new FloorProperties();

        IBlockProvider blockProvider = IBlockProvider.get(motif);

        // TODO determine if using a sunken floor
        // get the size of the footprint
        Coords2D size = new Coords2D(room.getWidth(), room.getDepth());
        // check if room is big enough to use a padded border

        if (size.getX() >= 7 && size.getY() >= 7 && RandomHelper.checkProbability(random, 25)) {
            properties.setPaddedBorder(true);
        }

        if (RandomHelper.checkProbability(random, 25)) {
            properties.setBorder(true);
        }

//        if (RandomHelper.checkProbability(random, 25)) {
//            properties.setCornerGrates(true);
//        }

        int y = 0;

        // borders
        // TODO this really should be calling a FloorBorderDecorator
        if (properties.border) {
            // TODO should be Optional
            Optional<IRoomElementDecorator> borderDecorator = getDecorator(random, FloorElementType.FLOOR_BORDER);
            if (borderDecorator.isPresent()) {
                borderDecorator.get().decorate(level, random, layout, room, coords, motif);
            }
        }
        if (properties.paddedBorder) {
            Optional<IRoomElementDecorator> borderDecorator = getDecorator(random, FloorElementType.FLOOR_PADDED_BORDER);
            if (borderDecorator.isPresent()) {
                borderDecorator.get().decorate(level, random, layout, room, coords, motif);
            }
        }

        // corners last
//        if (properties.cornerGrates) {
            Optional<IRoomElementDecorator> drainageDecorator = getDecorator(random, FloorElementType.FLOOR_DRAINAGE);
            if (drainageDecorator.isPresent()) {
                drainageDecorator.get().decorate(level, random, layout, room, coords, motif);
                // NOTE don't put it into lambda notation as we need the return value
            }

//            IRoomElementDecorator cornerDecorator = getDecorator(random, FloorElementType.FLOOR_CORNER);
//            cornerDecorator.decorate(level, random, layout, room, coords, motif);

            // TODO could hardcode the grates here OR use another patter - FloorDrainagePattern

//            level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, 1).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
//            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, 1).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
//            level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, room.getDepth() - 2).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
//            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, room.getDepth() - 2).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
//        }

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
