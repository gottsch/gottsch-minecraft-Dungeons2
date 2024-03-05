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
import mod.gottsch.forge.dungeons2.core.pattern.floor.FloorPattern;
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

        Optional<IBlockProvider> blockProviderOptional = DungeonsApi.getBlockProvider(motif);
        if (blockProviderOptional.isEmpty()) {
            return layout;
        }
        IBlockProvider blockProvider = blockProviderOptional.get();

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

        if (RandomHelper.checkProbability(random, 25)) {
            properties.setCornerGrates(true);
        }

        int y = 0;

        // borders
        // TODO this really should be calling a FloorBorderDecorator
        if (properties.border) {
            // TODO should be Optional
            IRoomElementDecorator borderDecorator = getDecorator(random, FloorElementType.FLOOR_BORDER);
            borderDecorator.decorate(level, random, layout, room, coords, motif);
//            for (int x = 1; x < room.getWidth() - 1; x++) {
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, 1).toPos(), blockProvider.get(FloorPattern.BORDER));
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, room.getDepth() - 2).toPos(), blockProvider.get(FloorPattern.BORDER));
//            }
//            for (int z = 2; z < room.getDepth() - 2; z++) {
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, z).toPos(), blockProvider.get(FloorPattern.BORDER));
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, z).toPos(), blockProvider.get(FloorPattern.BORDER));
//            }
        }
        if (properties.paddedBorder) {
            IRoomElementDecorator borderDecorator = getDecorator(random, FloorElementType.FLOOR_PADDED_BORDER);
            borderDecorator.decorate(level, random, layout, room, coords, motif);
//            for (int x = 2; x < room.getWidth() - 2; x++) {
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, 2).toPos(), blockProvider.get(FloorPattern.BORDER));
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(x, y, room.getDepth() - 3).toPos(), blockProvider.get(FloorPattern.BORDER));
//            }
//            for (int z = 3; z < room.getDepth() - 3; z++) {
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(2, y, z).toPos(), blockProvider.get(FloorPattern.BORDER));
//                level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 3, y, z).toPos(), blockProvider.get(FloorPattern.BORDER));
//            }
        }

        // corners last
        if (properties.cornerGrates) {
//            IRoomElementDecorator cornerDecorator = getDecorator(random, FloorElementType.FLOOR_CORNER);
//            cornerDecorator.decorate(level, random, layout, room, coords, motif);

            level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, 1).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, 1).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(1, y, room.getDepth() - 2).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
            level.setBlockAndUpdate(coords.add(room.getCoords()).add(room.getWidth() - 2, y, room.getDepth() - 2).toPos(), blockProvider.get(FloorPattern.CORNER).orElse(DEFAULT));
        }

        return layout;
    }

    private IRoomElementDecorator getDecorator(RandomSource random, IRoomElementType type) {
        // TODO get from registry
        // TODO return Optional

     List<IRoomElementDecorator> decoratorList = DungeonsApi.getDecorator(type);
        if (decoratorList.isEmpty()) {
            return this;
        }
        return decoratorList.get(RandomHelper.randomInt(random, 0, decoratorList.size() - 1));

        // TEMP
//        if ("floor_border".equals(key)) {
//            return new DefaultBorderDecorator();
//        }
//        else if ("floor_padded_border".equals(key)) {
//            return new DefaultPaddedBorderDecorator();
//        }
//        return null;
    }
}
