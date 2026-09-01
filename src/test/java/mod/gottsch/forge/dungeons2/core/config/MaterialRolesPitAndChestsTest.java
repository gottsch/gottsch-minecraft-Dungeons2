package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.pit.CentrePitShape;
import mod.gottsch.forge.dungeons2.core.config.pit.HazardPitShape;
import mod.gottsch.forge.dungeons2.core.config.pit.InsetPitShape;
import mod.gottsch.forge.dungeons2.core.config.pit.PitShapePattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #65 phase 6 &mdash; the {@code pit} and {@code chests} slots read a material role.
 *
 * <p>The last of the element slots, and the smallest: five fields. With this,
 * <strong>every slot on a scheme that names a block reads a role</strong>. {@code pots} and
 * {@code spawners} name entities, mob sets and loot tables rather than blocks, so there was never
 * anything here for them &mdash; and a role against those registries would be a different feature,
 * not this one.</p>
 *
 * <p>Neither record has a second home; both live only on {@code RoomScheme}. That was checked before
 * starting rather than after, which is the habit phase 3 bought.</p>
 */
class MaterialRolesPitAndChestsTest {

    private static final Gson GSON = new Gson();

    private static final String PALETTE =
            "\"palette\": {\"a\": \"minecraft:stone\", \"b\": \"minecraft:cobblestone\","
                    + " \"c\": \"minecraft:pointed_dripstone\"}";

    // ---- the pit ---------------------------------------------------------------------------------

    /**
     * Two levels at once: the paving belongs to the ENTRY and the rim to the SHAPE.
     * {@code floor_block} sits beside {@code type} rather than inside {@code config} because it
     * paves the sunken floor whatever shape cut it.
     */
    @Test
    void aPitResolvesItsOwnPavingAndItsShapesRim() {
        PitPatternEntry pit = pit("""
                {"type": "dungeons2:centre", "floor_block": "$a",
                 "config": {"size": 5, "depth": 2, "rim_block": "$b"}}""");
        assertEquals(Optional.of("minecraft:stone"), pit.floorBlock());
        CentrePitShape shape = (CentrePitShape) pit.shape();
        assertEquals(Optional.of("minecraft:cobblestone"), shape.rimBlock());
        assertEquals(5, shape.size(), "and keeps what it did not resolve");
        assertEquals(2, shape.depth());
    }

    @Test
    void aHazardResolvesItsSpikesAndItsRim() {
        HazardPitShape hazard = (HazardPitShape) pit("""
                {"type": "dungeons2:hazard",
                 "config": {"width": 3, "depth": 4, "spike_block": "$c", "rim_block": "$b",
                            "offset_x": 2}}""").shape();
        assertEquals(Optional.of("minecraft:pointed_dripstone"), hazard.spikeBlock());
        assertEquals(Optional.of("minecraft:cobblestone"), hazard.rimBlock());
        assertEquals(4, hazard.depth());
        assertEquals(2, hazard.offsetX());
    }

    /**
     * {@code inset} is the one shape with no block of its own &mdash; a walkable sunken court is
     * paved by the floor around it &mdash; so it takes {@link PitShapePattern}'s default.
     */
    @Test
    void theShapeWithNoBlocksTakesTheDefault() {
        PitShapePattern inset = new InsetPitShape(1, 2);
        assertSame(inset, inset.withRoles(role -> "minecraft:dirt"));
    }

    // ---- the chests ------------------------------------------------------------------------------

    @Test
    void everyChestVariantsBlockIsResolved() {
        ChestConfig chests = fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"vault\","
                + " \"chests\": {\"variants\": ["
                + "{\"block\": \"$a\", \"weight\": 3},"
                + "{\"block\": \"minecraft:trapped_chest\"}]}}]}")
                .forFloor(0).schemes().stream()
                .filter(scheme -> "vault".equals(scheme.name())).findFirst().orElseThrow()
                .chests().orElseThrow();
        assertEquals("minecraft:stone", chests.variants().get(0).block());
        assertEquals(3, chests.variants().get(0).weight(), "and keeps the variant's weight");
        assertEquals("minecraft:trapped_chest", chests.variants().get(1).block(),
                "a literal beside a role is untouched");
    }

    /**
     * {@code loot_table} is deliberately NOT a role. It names a loot table, and its record is shared
     * with {@code ChestLootBand} and the chest marker processor &mdash; a role against the loot
     * registry would be a different feature, so the key stays a plain string.
     */
    @Test
    void aLootTableIsNotABlockAndIsLeftAlone() {
        ChestConfig chests = fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"vault\","
                + " \"chests\": {\"variants\": [{\"block\": \"$a\"}],"
                + " \"loot_tables\": [{\"loot_table\": \"dungeons2:chests/deep\", \"weight\": 1}]}}]}")
                .forFloor(0).schemes().stream()
                .filter(scheme -> "vault".equals(scheme.name())).findFirst().orElseThrow()
                .chests().orElseThrow();
        assertEquals("dungeons2:chests/deep",
                chests.lootTables().orElseThrow().get(0).lootTable());
    }

    // ---- through a band, and at load -------------------------------------------------------------

    @Test
    void aBandRepaintsThePitsRim() {
        MotifConfig motif = fold("""
                {"palette": {"rim": "minecraft:cobblestone_stairs"},
                 "schemes": [{"name": "court", "pit":
                     {"type": "dungeons2:centre", "config": {"rim_block": "$rim"}}}],
                 "strata_by_floor_index": [
                   {"min_floor_index": 0, "palette": {"rim": "minecraft:mud_brick_stairs"}},
                   {"min_floor_index": 1}]}""");
        assertEquals(Optional.of("minecraft:mud_brick_stairs"), rimOf(motif.forFloor(0)));
        assertEquals(Optional.of("minecraft:cobblestone_stairs"), rimOf(motif.forFloor(1)));
    }

    @Test
    void anUndeclaredRoleInAPitIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC, """
                {"schemes": [{"name": "court", "pit":
                    {"type": "dungeons2:hazard", "config": {"spike_block": "$spike"}}}]}""")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$spike"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("court"), () -> problems.get(0));
    }

    // ---- identity --------------------------------------------------------------------------------

    @Test
    void aPitOfLiteralsIsNotEvenCopied() {
        PitPatternEntry entry = decode(PitPatternEntry.CODEC,
                "{\"type\": \"dungeons2:centre\", \"config\": {\"rim_block\": \"minecraft:stone\"}}");
        assertSame(entry, entry.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void aChestSlotOfLiteralsIsNotEvenCopied() {
        ChestConfig chests = decode(ChestConfig.CODEC,
                "{\"variants\": [{\"block\": \"minecraft:chest\"}]}");
        assertSame(chests, chests.withRoles(role -> "minecraft:dirt"));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static Optional<String> rimOf(MotifConfig motif) {
        PitShapePattern shape = motif.schemes().stream()
                .filter(scheme -> "court".equals(scheme.name())).findFirst().orElseThrow()
                .pit().orElseThrow().shape();
        return ((CentrePitShape) shape).rimBlock();
    }

    /** One pit entry, decoded in a scheme and then resolved against {@link #PALETTE}. */
    private static PitPatternEntry pit(String pitJson) {
        return fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"court\", \"pit\": "
                + pitJson + "}]}")
                .forFloor(0).schemes().stream()
                .filter(scheme -> "court".equals(scheme.name())).findFirst().orElseThrow()
                .pit().orElseThrow();
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
}
