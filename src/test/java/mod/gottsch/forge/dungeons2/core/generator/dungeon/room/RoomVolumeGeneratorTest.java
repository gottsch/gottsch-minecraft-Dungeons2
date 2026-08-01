package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hollow step, extracted from {@code BasicWallGenerator} so that interior features (pillars,
 * vaults) have a room volume to build into rather than having to coordinate with wall code.
 *
 * <p>What these tests really pin down is the <strong>boundary</strong>: this step must claim the
 * interior and nothing else, because everything else in the room pipeline assumes it can render its
 * own plane without checking whether the air fill already stomped it.</p>
 */
class RoomVolumeGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The same 7x7x5 room BasicWallGeneratorTest uses, anchored at (10, 10). */
    private static RoomData smallRoom() {
        return new RoomData(1, 10, 10, 7, 7, 5, RoomRole.NORMAL);
    }

    private static List<BlockPlacement> hollow(RoomData room, int floorY) {
        List<BlockPlacement> out = new ArrayList<>();
        RoomVolumeGenerator.hollow(room, floorY, out);
        return out;
    }

    @Test
    void emitsTheInteriorVolume() {
        // 5x5 interior footprint * 3 interior layers (y=1..3 for height 5).
        assertEquals(75, hollow(smallRoom(), 60).size());
    }

    @Test
    void everyPlacementIsAir() {
        for (BlockPlacement bp : hollow(smallRoom(), 60)) {
            assertEquals("minecraft:air", bp.getBlockId(), "hollowing must emit air: " + bp);
        }
    }

    /**
     * Never the perimeter ring. A wall cell filled with air here would be a hole straight through
     * the room, and the wall generator runs afterwards only by convention -- not as a guarantee
     * this step is allowed to lean on.
     */
    @Test
    void neverTouchesTheWallRing() {
        RoomData room = smallRoom();
        for (BlockPlacement bp : hollow(room, 60)) {
            int x = bp.getX() - room.getOriginX();
            int z = bp.getZ() - room.getOriginZ();
            assertTrue(x > 0 && x < room.getWidth() - 1, "hollowed a wall column: " + bp);
            assertTrue(z > 0 && z < room.getDepth() - 1, "hollowed a wall column: " + bp);
        }
    }

    /**
     * Never the floor or ceiling plane either -- those are the two the surface generators own, and
     * this step deliberately does not depend on running before them.
     */
    @Test
    void neverTouchesTheFloorOrCeilingPlane() {
        RoomData room = smallRoom();
        int floorY = 60;
        int ceilingY = floorY + room.getHeight() - 1;
        for (BlockPlacement bp : hollow(room, floorY)) {
            assertTrue(bp.getY() > floorY && bp.getY() < ceilingY,
                    "hollowed the floor or ceiling plane: " + bp);
        }
    }

    /**
     * A 2-thick room has no interior at all. Degenerate but reachable -- nothing clamps room
     * dimensions on the way in -- so it must produce nothing rather than a negative-extent loop.
     */
    @Test
    void aRoomWithNoInteriorEmitsNothing() {
        assertEquals(0, hollow(new RoomData(1, 0, 0, 2, 7, 5, RoomRole.NORMAL), 60).size());
        assertEquals(0, hollow(new RoomData(1, 0, 0, 7, 2, 5, RoomRole.NORMAL), 60).size());
        assertEquals(0, hollow(new RoomData(1, 0, 0, 7, 7, 2, RoomRole.NORMAL), 60).size());
    }
}
