package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.partition.CornerPartitionShape;
import mod.gottsch.forge.dungeons2.core.config.partition.PartitionShapeRegistry;
import mod.gottsch.forge.dungeons2.core.config.partition.StripPartitionShape;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code partition} slot's schema (#74).
 *
 * <p>Two things are worth a test here beyond the usual closed-schema sweep. The {@code block} field
 * is <strong>required</strong> where the pit's is not, and the reason is not symmetry: a pit with no
 * material continues the floor around it, which is a sensible thing to mean, and a partition with no
 * material is not a partition. And the shape is dispatched over a registry, so an unregistered
 * {@code type} has to be a load error naming what IS registered rather than a room that quietly has
 * no partition.</p>
 */
class PartitionPatternEntryTest {

    private static final Gson GSON = new Gson();

    private static final String PALETTE =
            "\"palette\": {\"bars\": \"minecraft:iron_bars\", \"gate\": \"minecraft:iron_door\"}";

    // ---- defaults --------------------------------------------------------------------------------

    @Test
    void theDefaultsAreAThreeHighCornerCellInAnyCorner() {
        PartitionPatternEntry entry = decode(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:corner\", \"block\": \"minecraft:iron_bars\"}");
        assertEquals("minecraft:iron_bars", entry.block());
        assertEquals(PartitionPatternEntry.DEFAULT_HEIGHT, entry.height());
        assertEquals(Optional.empty(), entry.gapBlock(), "the way through is open by default");
        CornerPartitionShape shape = (CornerPartitionShape) entry.shape();
        assertEquals(CornerPartitionShape.DEFAULT_SIZE, shape.width());
        assertEquals(CornerPartitionShape.DEFAULT_SIZE, shape.depth());
        assertEquals(CornerPartitionShape.Corner.ANY, shape.corner());
        assertEquals(SizeGate.UNBOUNDED, entry.gate());
    }

    @Test
    void aStripDefaultsToTheMiddleOfEitherAxis() {
        StripPartitionShape shape = (StripPartitionShape) decode(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:strip\", \"block\": \"minecraft:iron_bars\"}").shape();
        assertEquals(StripPartitionShape.Axis.ANY, shape.axis());
        assertEquals(Optional.empty(), shape.offset(), "absent means the middle, whatever the room");
    }

    @Test
    void bothShapesAreRegistered() {
        assertTrue(PartitionShapeRegistry.ids()
                .containsAll(List.of(new ResourceLocation("dungeons2", "corner"),
                        new ResourceLocation("dungeons2", "strip"))),
                () -> "registered: " + PartitionShapeRegistry.ids());
    }

    // ---- the closed schema -----------------------------------------------------------------------

    /**
     * {@code block} is required. A pit's {@code floor_block} is not, and the difference is
     * deliberate: an unauthored pit continues the floor around it, and an unauthored partition is
     * nothing at all.
     */
    @Test
    void aPartitionWithNoBlockIsALoadError() {
        assertTrue(error(PartitionPatternEntry.CODEC, "{\"type\": \"dungeons2:corner\"}")
                .contains("block"));
    }

    @Test
    void anUnregisteredShapeTypeIsALoadError() {
        String message = error(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:diagonal\", \"block\": \"minecraft:iron_bars\"}");
        assertTrue(message.contains("dungeons2:diagonal"), () -> message);
        assertTrue(message.contains("corner"),
                () -> "the error should name what IS registered: " + message);
    }

    @Test
    void aStrayKeyIsALoadError() {
        String message = error(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:corner\", \"block\": \"minecraft:iron_bars\","
                        + " \"hieght\": 3}");
        assertTrue(message.contains("hieght"), () -> message);
    }

    @Test
    void aStrayKeyInsideTheShapeConfigIsALoadError() {
        String message = error(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:corner\", \"block\": \"minecraft:iron_bars\","
                        + " \"config\": {\"widht\": 3}}");
        assertTrue(message.contains("widht"), () -> message);
    }

    @Test
    void aMisspelledCornerIsALoadError() {
        String message = error(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:corner\", \"block\": \"minecraft:iron_bars\","
                        + " \"config\": {\"corner\": \"northwest\"}}");
        assertTrue(message.contains("northwest"), () -> message);
    }

    /**
     * Height is capped at 8, which is as tall as the taper (#51) lets a room's interior be. A bigger
     * number could never mean more than 8 does, so it is rejected rather than silently clamped --
     * the clamp that DOES happen is against the actual room, which no codec can see.
     */
    @Test
    void aHeightAboveTheTallestPossibleRoomIsALoadError() {
        assertTrue(error(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:corner\", \"block\": \"minecraft:iron_bars\","
                        + " \"height\": 9}").contains("height"));
    }

    // ---- roles -----------------------------------------------------------------------------------

    @Test
    void theBlockAndTheGapBlockBothReadARole() {
        PartitionPatternEntry entry = slotOf(fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"cells\","
                + " \"partition\": {\"type\": \"dungeons2:corner\", \"block\": \"$bars\","
                + " \"gap_block\": \"$gate\", \"config\": {\"width\": 4, \"corner\": \"south_east\"}}}]}"));
        assertEquals("minecraft:iron_bars", entry.block());
        assertEquals(Optional.of("minecraft:iron_door"), entry.gapBlock());
        CornerPartitionShape shape = (CornerPartitionShape) entry.shape();
        assertEquals(4, shape.width(), "and keeps what it did not resolve");
        assertEquals(CornerPartitionShape.Corner.SOUTH_EAST, shape.corner());
    }

    @Test
    void anEntryOfLiteralsIsNotEvenCopied() {
        PartitionPatternEntry entry = decode(PartitionPatternEntry.CODEC,
                "{\"type\": \"dungeons2:strip\", \"block\": \"minecraft:iron_bars\"}");
        assertSame(entry, entry.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void anUndeclaredRoleInAPartitionIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC,
                "{\"schemes\": [{\"name\": \"cells\", \"partition\":"
                        + " {\"type\": \"dungeons2:corner\", \"block\": \"$grate\"}}]}")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$grate"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("cells"), () -> problems.get(0));
    }

    // ---- on the scheme ---------------------------------------------------------------------------

    @Test
    void thePartitionSlotGatesItselfOnRoomSize() {
        RoomScheme scheme = decode(RoomScheme.CODEC,
                "{\"name\": \"cells\", \"partition\": {\"type\": \"dungeons2:corner\","
                        + " \"block\": \"minecraft:iron_bars\", \"min_size\": 11}}");
        assertEquals(Optional.empty(), scheme.partitionFor(9, 9, 8));
        assertTrue(scheme.partitionFor(13, 13, 8).isPresent());
        assertTrue(scheme.declaresAnySlot());
        assertTrue(scheme.drawsAnything(13, 13, 8));
    }

    @Test
    void anInvertedGateOnThePartitionSlotIsALoadError() {
        String message = error(RoomScheme.CODEC,
                "{\"name\": \"cells\", \"partition\": {\"type\": \"dungeons2:corner\","
                        + " \"block\": \"minecraft:iron_bars\", \"min_size\": 11, \"max_size\": 7}}");
        assertTrue(message.contains("partition"), () -> message);
    }

    @Test
    void thePartitionSlotInherits() {
        RoomScheme parent = decode(RoomScheme.CODEC,
                "{\"name\": \"base\", \"abstract\": true, \"partition\":"
                        + " {\"type\": \"dungeons2:corner\", \"block\": \"minecraft:iron_bars\"}}");
        RoomScheme child = decode(RoomScheme.CODEC, "{\"name\": \"cells\", \"extends\": \"base\"}");
        assertEquals(parent.partition(), child.inheritFrom(parent).partition());
    }

    /** The fold (RoomSlots) moved no JSON: the slot keys are still flat on the scheme object. */
    @Test
    void everySlotKeyIsStillFlatOnTheScheme() {
        JsonElement dumped = RoomScheme.CODEC.encodeStart(JsonOps.INSTANCE, decode(RoomScheme.CODEC,
                "{\"name\": \"cells\", \"min_size\": 9, \"partition\":"
                        + " {\"type\": \"dungeons2:corner\", \"block\": \"minecraft:iron_bars\"}}"))
                .result().orElseThrow();
        assertTrue(dumped.getAsJsonObject().has("partition"),
                () -> "no nested wrapper: " + dumped);
        assertTrue(dumped.getAsJsonObject().has("min_size"), () -> dumped.toString());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static PartitionPatternEntry slotOf(MotifConfig motif) {
        return motif.forFloor(0).schemes().stream()
                .filter(scheme -> "cells".equals(scheme.name())).findFirst().orElseThrow()
                .partition().orElseThrow();
    }

    private static MotifConfig fold(String json) {
        List<String> problems = new ArrayList<>();
        MotifConfig motif = MotifConfigFragment.resolve(
                List.of(decode(MotifConfigFragment.CODEC, json)), problems::add);
        assertEquals(List.of(), problems, "expected this motif to fold cleanly");
        return motif;
    }

    private static <A> A decode(Codec<A> codec, String json) {
        DataResult<A> result = codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
        return result.result().orElseThrow(() -> new AssertionError(
                "expected this to decode: " + result.error().orElseThrow().message() + " -- " + json));
    }

    private static <A> String error(Codec<A> codec, String json) {
        DataResult<A> result = codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
        return result.error().orElseThrow(() -> new AssertionError(
                "expected this to be rejected: " + json)).message();
    }
}
