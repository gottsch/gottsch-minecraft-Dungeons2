package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies a checkerboard base with a border ring layered on top doesn't stomp the checkerboard
 * fill outside the ring -- the whole reason {@link FloorBorderPatternProvider#overlay} exists
 * instead of just reusing its full-fill {@code build}.
 */
class CompositeFloorPatternProviderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void borderOverlayDoesNotStompTheCheckerboardOutsideTheRing() {
        RoomData room = new RoomData(1, 0, 0, 9, 9, 4, RoomRole.NORMAL);
        IDungeonFloorGenerator checkerboard =
                new CheckerboardFloorPatternProvider(Blocks.GRANITE, Blocks.DIORITE);
        FloorBorderPatternProvider border = new FloorBorderPatternProvider(
                2, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE);
        CompositeFloorPatternProvider composite =
                new CompositeFloorPatternProvider(checkerboard, List.of(border));

        List<BlockPlacement> out = new ArrayList<>();
        composite.build(room, 0, DungeonMotif.CLASSIC, RandomSource.create(1), out);

        // The base fills all 81 cells, then the overlay appends 16 more for the ring -- same
        // "later placement wins the same cell" convention BasicRoomGenerator's wall/floor/ceiling
        // steps already rely on (see its class javadoc), not a dedup at the list level.
        assertEquals(81 + 16, out.size());

        // Last-wins: a HashMap#put keeps whichever placement was iterated last, i.e. the
        // overlay's, for any cell it touched.
        Map<String, BlockPlacement> byCoord = new HashMap<>();
        for (BlockPlacement p : out) {
            byCoord.put(p.getX() + "," + p.getZ(), p);
        }

        // Ring corner (NW, inset 2 on a 9x9 floor): the border's block, not the checkerboard's.
        assertEquals("minecraft:andesite", byCoord.get("2,2").getBlockId());
        // Center: outside the ring, untouched by the overlay -- still the checkerboard's own pick.
        BlockPlacement center = byCoord.get("4,4");
        assertEquals((4 + 4) % 2 == 0 ? "minecraft:granite" : "minecraft:diorite", center.getBlockId());
        // Outer margin, also outside the ring: same story.
        BlockPlacement corner = byCoord.get("0,0");
        assertEquals("minecraft:granite", corner.getBlockId());
    }

    /**
     * The pairing #75 exists for: a border ring, and a field one inset further in filling exactly
     * what the ring encloses.
     *
     * <p>Both measure {@code inset} from the room edge, so the field goes at {@code border inset +
     * 1}. Ordered field-after-border here, which is the order that reads as "a panel inside a
     * frame"; the reverse is legal and lets the ring draw over the field's outermost row.</p>
     */
    @Test
    void aFieldOneInsetFurtherInFillsExactlyWhatTheBorderEncloses() {
        RoomData room = new RoomData(1, 0, 0, 11, 11, 4, RoomRole.NORMAL);
        IDungeonFloorGenerator base =
                new CheckerboardFloorPatternProvider(Blocks.GRANITE, Blocks.DIORITE);
        FloorBorderPatternProvider border = new FloorBorderPatternProvider(
                2, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE);
        FieldFloorPatternProvider field = new FieldFloorPatternProvider(3, Blocks.GOLD_BLOCK);

        List<BlockPlacement> out = new ArrayList<>();
        new CompositeFloorPatternProvider(base, List.of(border, field))
                .build(room, 0, DungeonMotif.CLASSIC, RandomSource.create(1), out);

        Map<String, BlockPlacement> byCoord = new HashMap<>();
        for (BlockPlacement p : out) {
            byCoord.put(p.getX() + "," + p.getZ(), p);
        }

        long gold = byCoord.values().stream()
                .filter(p -> "minecraft:gold_block".equals(p.getBlockId())).count();
        assertEquals(25, gold, "the 5x5 panel the ring at inset 2 encloses");

        // The ring itself survives: the field starts one cell inside it and never reaches it.
        assertEquals("minecraft:polished_andesite", byCoord.get("2,5").getBlockId());
        assertEquals("minecraft:gold_block", byCoord.get("3,5").getBlockId());
        // And the checkerboard outside the ring is untouched by either overlay.
        assertEquals("minecraft:granite", byCoord.get("0,0").getBlockId());
    }
}
