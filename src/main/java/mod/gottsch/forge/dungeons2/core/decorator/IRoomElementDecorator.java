package mod.gottsch.forge.dungeons2.core.decorator;

import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * @author Mark Gottschling on March 3, 2024
 *
 */
public interface IRoomElementDecorator {
    public Grid2D decorate(ServerLevel level, RandomSource random, Grid2D layout, IRoom room, ICoords coords, IDungeonMotif motif);
}
