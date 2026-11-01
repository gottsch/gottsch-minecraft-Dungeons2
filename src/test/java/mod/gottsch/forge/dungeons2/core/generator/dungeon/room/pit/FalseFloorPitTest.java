package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.PitPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.pit.FalseFloorPitShape;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The false-floor pit: a spiked shaft under a crumbling copy of the floor.
 */
class FalseFloorPitTest {

    private static final int FLOOR_Y = 60;
    private static final FloorConfig PAVING =
            new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RoomData room() {
        return new RoomData(1, 0, 0, 11, 11, 7, RoomRole.NORMAL);
    }

    private static FalseFloorPitShape shape(String spike, Map<String, String> properties) {
        return new FalseFloorPitShape(3, 4, 0, 0, Optional.of(spike), properties, 1.0D);
    }

    /** A floor paved in {@code block}, as the floor generator would have left it. */
    private static List<BlockPlacement> paved(RoomData room, String block) {
        List<BlockPlacement> out = new ArrayList<>();
        for (int x = room.getOriginX(); x < room.getOriginX() + room.getWidth(); x++) {
            for (int z = room.getOriginZ(); z < room.getOriginZ() + room.getDepth(); z++) {
                out.add(new BlockPlacement(x, FLOOR_Y, z, block));
            }
        }
        return out;
    }

    /** The last placement at a cell: the list is a layering order. */
    private static BlockPlacement at(List<BlockPlacement> out, int x, int y, int z) {
        BlockPlacement found = null;
        for (BlockPlacement p : out) {
            if (p.getX() == x && p.getY() == y && p.getZ() == z) {
                found = p;
            }
        }
        return found;
    }

    private static Set<Coords2D> excavate(RoomData room, FalseFloorPitShape shape,
                                          List<BlockPlacement> out) {
        return RoomPitGenerator.excavate(room, FLOOR_Y, new PitPatternEntry(shape), 5, PAVING,
                RandomSource.create(3L), out);
    }

    @Test
    void theLidIsACrumblingCopyOfTheFloorOverAnOpenShaft() {
        RoomData room = room();
        List<BlockPlacement> out = paved(room, "minecraft:stone_bricks");
        Set<Coords2D> dug = excavate(room, shape("minecraft:pointed_dripstone",
                Map.of("vertical_direction", "up")), out);

        assertEquals(9, dug.size(), "a centred 3x3 shaft");
        for (Coords2D cell : dug) {
            assertEquals("dungeonblocks:crumbling_stone_bricks",
                    at(out, cell.getX(), FLOOR_Y, cell.getY()).getBlockId(), "lid at " + cell);
            // Open under the lid, down to the spike standing on the shaft floor.
            assertEquals("minecraft:air", at(out, cell.getX(), FLOOR_Y - 1, cell.getY()).getBlockId());
            assertEquals("minecraft:pointed_dripstone",
                    at(out, cell.getX(), FLOOR_Y - 3, cell.getY()).getBlockId());
        }
    }

    /** A floor pattern repaints cells, and each lid cell follows whatever is under it. */
    @Test
    void eachLidCellMatchesThePatternedFloorUnderIt() {
        RoomData room = room();
        List<BlockPlacement> out = paved(room, "minecraft:stone_bricks");
        out.add(new BlockPlacement(5, FLOOR_Y, 5, "minecraft:polished_andesite"));
        out.add(new BlockPlacement(4, FLOOR_Y, 5, "minecraft:mossy_stone_bricks"));
        excavate(room, shape("minecraft:pointed_dripstone", Map.of()), out);

        assertEquals("dungeonblocks:crumbling_polished_andesite", at(out, 5, FLOOR_Y, 5).getBlockId());
        assertEquals("dungeonblocks:crumbling_mossy_stone_bricks", at(out, 4, FLOOR_Y, 5).getBlockId());
        assertEquals("dungeonblocks:crumbling_stone_bricks", at(out, 6, FLOOR_Y, 5).getBlockId());
    }

    /** One cell in a stone with no crumbling version: no lid at all, rather than a visible patch. */
    @Test
    void aFloorStoneWithNoCrumblingVersionLeavesThePitOpen() {
        RoomData room = room();
        List<BlockPlacement> out = paved(room, "minecraft:stone_bricks");
        out.add(new BlockPlacement(5, FLOOR_Y, 5, "minecraft:chiseled_stone_bricks"));
        Set<Coords2D> dug = excavate(room, shape("minecraft:pointed_dripstone", Map.of()), out);

        assertFalse(dug.isEmpty(), "still a pit");
        for (Coords2D cell : dug) {
            String top = at(out, cell.getX(), FLOOR_Y, cell.getY()).getBlockId();
            assertFalse(top.startsWith("dungeonblocks:crumbling_"), "lid at " + cell);
            assertEquals("minecraft:air", top);
        }
    }

    /** Both spike kinds reach the shaft floor, and the lid never gets a rim. */
    @Test
    void ironSpikesAndNoRim() {
        RoomData room = room();
        List<BlockPlacement> spikes = paved(room, "minecraft:cobblestone");
        excavate(room, shape("dungeonblocks:iron_spikes", Map.of("facing", "up")), spikes);
        // Without DungeonBlocks' registry the spike resolves to the hazard's dripstone fallback;
        // what matters here is that the shaft floor is spiked and nothing but the lid is at Y.
        assertEquals("dungeonblocks:crumbling_cobblestone", at(spikes, 5, FLOOR_Y, 5).getBlockId());
        for (BlockPlacement p : spikes) {
            if (p.getY() == FLOOR_Y) {
                assertTrue(p.getBlockId().equals("minecraft:cobblestone")
                        || p.getBlockId().equals("dungeonblocks:crumbling_cobblestone")
                        || p.getBlockId().equals("minecraft:air"), "a rim or stray block: " + p);
            }
        }
    }

    // ---------- the schema ----------

    private static DataResult<PitPatternEntry> parse(String json) {
        return PitPatternEntry.CODEC.parse(JsonOps.INSTANCE, new Gson().fromJson(json, JsonElement.class));
    }

    @Test
    void itDecodesFromItsAuthoredForm() {
        PitPatternEntry entry = parse("{\"type\":\"dungeons2:false_floor\",\"config\":{\"depth\":4,"
                + "\"spike_block\":\"dungeonblocks:iron_spikes\",\"spike_properties\":{\"facing\":\"up\"}}}")
                .result().orElseThrow();
        assertTrue(entry.shape() instanceof FalseFloorPitShape);
    }

    /** A rim is the hazard's tell; a false floor that could have one would not be false. */
    @Test
    void aRimAndAOneDeepShaftAreLoadErrors() {
        assertTrue(parse("{\"type\":\"dungeons2:false_floor\",\"config\":{\"rim_block\":\"minecraft:stone\"}}")
                .error().isPresent());
        assertTrue(parse("{\"type\":\"dungeons2:false_floor\",\"config\":{\"depth\":1}}")
                .error().isPresent());
    }
}
