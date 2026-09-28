package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.PitPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.SecretConfig;
import mod.gottsch.forge.dungeons2.core.config.pit.HiddenMoatPitShape;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomPedestalGenerator;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Take the prize, lose the floor: the moat dug under an untouched floor, and the pedestal armed
 * over it.
 */
class HiddenMoatPitTest {

    private static final int FLOOR_Y = 60;
    private static final FloorConfig PAVING =
            new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static HiddenMoatPitShape moat(int radius) {
        return new HiddenMoatPitShape(radius, 4, Optional.of("minecraft:pointed_dripstone"),
                Map.of("vertical_direction", "up"), 1.0D);
    }

    private static RoomData room(int side) {
        return new RoomData(1, 0, 0, side, side, 7, RoomRole.NORMAL);
    }

    private static BlockPlacement at(List<BlockPlacement> out, int x, int y, int z) {
        BlockPlacement found = null;
        for (BlockPlacement p : out) {
            if (p.getX() == x && p.getY() == y && p.getZ() == z) {
                found = p;
            }
        }
        return found;
    }

    @Test
    void aRingIsDugRoundAnUndugCentreAndTheFloorIsKept() {
        PitPlan plan = moat(2).provider().plan(7, 7, RandomSource.create(1L));
        assertEquals(24, plan.depths().size(), "a 5x5 ring round the centre");
        assertFalse(plan.depths().containsKey(new Coords2D(3, 3)), "the centre is not dug");
        assertEquals(plan.depths().keySet(), plan.keepFloor());
        assertTrue(plan.cover().isEmpty() && plan.falseFloor().isEmpty() && plan.rim().isEmpty());
    }

    @Test
    void theRingShrinksToKeepAWalkableEdgeAndVanishesWithoutRoom() {
        assertEquals(8, moat(2).provider().plan(5, 5, RandomSource.create(1L)).depths().size());
        assertTrue(moat(2).provider().plan(3, 3, RandomSource.create(1L)).isEmpty());
        assertTrue(moat(1).provider().plan(6, 7, RandomSource.create(1L)).isEmpty(),
                "an even interior has no centre cell to stand the pedestal on");
        assertEquals(1, moat(3).provider().fittedRadius(5, 5));
        assertEquals(0, moat(3).provider().fittedRadius(3, 3));
    }

    @Test
    void theFloorStandsOverAVoidAndTheCentreOnItsColumn() {
        RoomData room = room(9);
        List<BlockPlacement> out = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                out.add(new BlockPlacement(x, FLOOR_Y, z, "minecraft:stone_bricks"));
            }
        }
        Set<Coords2D> dug = RoomPitGenerator.excavate(room, FLOOR_Y, new PitPatternEntry(moat(2)), 5,
                PAVING, RandomSource.create(2L), out);
        assertEquals(24, dug.size());
        for (Coords2D cell : dug) {
            assertEquals("minecraft:stone_bricks", at(out, cell.getX(), FLOOR_Y, cell.getY()).getBlockId(),
                    "the floor is untouched at " + cell);
            assertEquals("minecraft:air", at(out, cell.getX(), FLOOR_Y - 1, cell.getY()).getBlockId(),
                    "and open beneath at " + cell);
        }
        assertFalse(dug.contains(new Coords2D(4, 4)));
        // Under the centre is solid: the lining backs every face the moat cut, so the pedestal's
        // column is stone all the way down, whatever terrain was there.
        assertEquals("minecraft:stone_bricks", at(out, 4, FLOOR_Y - 1, 4).getBlockId());
    }

    // ---------- the pedestal ----------

    private static final SecretConfig TABLE = new SecretConfig(Optional.of(List.of(
            new mod.gottsch.forge.dungeons2.core.config.ChestConfig.LootTableEntry("x:y", 1))));

    private static BlockPlacement pedestal(List<BlockPlacement> out) {
        return out.stream().filter(p -> "dungeonblocks:pedestal".equals(p.getBlockId())).findFirst()
                .orElseThrow();
    }

    @Test
    void aPedestalOnTheCentreIsArmed() {
        RoomData room = room(9);
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> taken = new HashSet<>();
        RoomPedestalGenerator.place(room, FLOOR_Y, TABLE, null, 2, taken, RandomSource.create(1L), out);
        String forgeData = pedestal(out).getBlockEntityNbt().getNbtValues().get("ForgeData");
        assertEquals("{dungeons2:{trap_radius:2}}", forgeData);
    }

    @Test
    void aPedestalPushedOffTheCentreIsLeftUnarmed() {
        RoomData room = room(9);
        List<BlockPlacement> out = new ArrayList<>();
        Set<Coords2D> taken = new HashSet<>(Set.of(new Coords2D(4, 4)));
        RoomPedestalGenerator.place(room, FLOOR_Y, TABLE, null, 2, taken, RandomSource.create(1L), out);
        assertFalse(pedestal(out).getBlockEntityNbt().getNbtValues().containsKey("ForgeData"));
    }

    @Test
    void itDecodesAndHasNoRimOrOneDeepForm() {
        Gson gson = new Gson();
        assertTrue(PitPatternEntry.CODEC.parse(JsonOps.INSTANCE, gson.fromJson(
                "{\"type\":\"dungeons2:hidden_moat\",\"config\":{\"radius\":2}}", JsonElement.class))
                .result().map(e -> e.shape() instanceof HiddenMoatPitShape).orElse(false));
        assertTrue(PitPatternEntry.CODEC.parse(JsonOps.INSTANCE, gson.fromJson(
                "{\"type\":\"dungeons2:hidden_moat\",\"config\":{\"rim_block\":\"minecraft:stone\"}}",
                JsonElement.class)).error().isPresent());
        assertTrue(PitPatternEntry.CODEC.parse(JsonOps.INSTANCE, gson.fromJson(
                "{\"type\":\"dungeons2:hidden_moat\",\"config\":{\"depth\":1}}", JsonElement.class))
                .error().isPresent());
    }
}
