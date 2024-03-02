package mod.gottsch.forge.dungeons2.core.decorator;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;

public class CeilingDecorator {

    public Grid2D decorate(ServerLevel level, IRoom room, ICoords spawnCoords) {

        // TODO determine the size of the room in order to select the available ceilings that fit

        // TODO also determine the 'long' axis to determine the direction the ceiling will run

        if (room.getHeight() >= 7) {
            // ceiling is good for arches

        }

        return null;
    }
}
