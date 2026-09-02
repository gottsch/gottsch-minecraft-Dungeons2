package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.PropConfig.PropPlacement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code props} slot's schema (#73).
 *
 * <p>What is checked here is almost entirely the failure modes a lenient codec would let through:
 * this slot's whole vocabulary is four placement words and a list of block ids, so a typo is the
 * realistic authoring mistake and it must be a load error rather than a room that quietly gets its
 * furniture in the wrong place.</p>
 */
class PropConfigTest {

    private static final Gson GSON = new Gson();

    private static final String PALETTE =
            "\"palette\": {\"crate\": \"dungeonblocks:crate\", \"bars\": \"minecraft:iron_bars\"}";

    // ---- defaults --------------------------------------------------------------------------------

    /**
     * A slot naming nothing but its variants is a working slot. The defaults matter because they are
     * what an author gets by saying nothing, and {@code against_wall} is the placement furniture
     * wants nine times in ten.
     */
    @Test
    void theDefaultsAreOneOrTwoPropsAgainstTheWall() {
        PropConfig props = decode(PropConfig.CODEC,
                "{\"variants\": [{\"block\": \"minecraft:barrel\"}]}");
        assertEquals(1, props.minCount());
        assertEquals(2, props.maxCount());
        assertEquals(PropPlacement.AGAINST_WALL, props.placement());
        assertEquals(1, props.variants().get(0).weight());
        assertTrue(props.variants().get(0).oriented(), "orientation is opt-OUT");
        assertEquals(SizeGate.UNBOUNDED, props.gate());
    }

    @Test
    void anInvertedCountRangeIsClampedRatherThanRejected() {
        PropConfig props = decode(PropConfig.CODEC,
                "{\"min_count\": 4, \"max_count\": 1, \"variants\": [{\"block\": \"minecraft:barrel\"}]}");
        assertEquals(4, props.clampedMaxCount());
    }

    // ---- the closed schema -----------------------------------------------------------------------

    /** {@code variants} is the one required key: a slot with nothing to place is a mistake. */
    @Test
    void aSlotWithNoVariantsIsALoadError() {
        assertTrue(error(PropConfig.CODEC, "{\"placement\": \"corner\"}").contains("variants"));
    }

    @Test
    void aStrayKeyIsALoadError() {
        String message = error(PropConfig.CODEC,
                "{\"placment\": \"corner\", \"variants\": [{\"block\": \"minecraft:barrel\"}]}");
        assertTrue(message.contains("placment"), () -> message);
    }

    @Test
    void aStrayKeyInsideAVariantIsALoadError() {
        String message = error(PropConfig.CODEC,
                "{\"variants\": [{\"block\": \"minecraft:barrel\", \"orientd\": true}]}");
        assertTrue(message.contains("orientd"), () -> message);
    }

    /**
     * A misspelled placement fails rather than falling back to the default. Reading
     * {@code "corners"} as {@code against_wall} would put the room's one anvil on a wall with no
     * error anywhere &mdash; the silent-default failure the strict codecs exist to prevent.
     */
    @Test
    void aMisspelledPlacementIsALoadError() {
        String message = error(PropConfig.CODEC,
                "{\"placement\": \"corners\", \"variants\": [{\"block\": \"minecraft:barrel\"}]}");
        assertTrue(message.contains("corners"), () -> message);
    }

    @Test
    void everyPlacementWordDecodes() {
        for (PropPlacement placement : PropPlacement.values()) {
            assertEquals(placement, decode(PropConfig.CODEC,
                    "{\"placement\": \"" + placement.getSerializedName() + "\","
                            + " \"variants\": [{\"block\": \"minecraft:barrel\"}]}").placement());
        }
    }

    @Test
    void aWeightOfZeroIsALoadError() {
        assertTrue(error(PropConfig.CODEC,
                "{\"variants\": [{\"block\": \"minecraft:barrel\", \"weight\": 0}]}")
                .contains("weight"));
    }

    /**
     * A malformed ROLE name fails at load. A malformed literal id deliberately does not:
     * {@code Codecs.BLOCK_ID_OR_ROLE} validates the role half only, because tightening the literal
     * half would reject data that loads today and that is a separate decision from this slot.
     */
    @Test
    void aMalformedRoleNameIsALoadError() {
        assertTrue(error(PropConfig.CODEC, "{\"variants\": [{\"block\": \"$oak barrel\"}]}")
                .contains("$oak barrel"));
    }

    // ---- roles -----------------------------------------------------------------------------------

    @Test
    void everyVariantsBlockIsResolvedAgainstThePalette() {
        PropConfig props = slotOf(fold("{" + PALETTE + ", \"schemes\": [{\"name\": \"store\","
                + " \"props\": {\"placement\": \"corner\", \"variants\": ["
                + "{\"block\": \"$crate\", \"weight\": 3, \"oriented\": false},"
                + "{\"block\": \"minecraft:barrel\"}]}}]}"));
        assertEquals("dungeonblocks:crate", props.variants().get(0).block());
        assertEquals(3, props.variants().get(0).weight(), "and keeps the variant's weight");
        assertFalse(props.variants().get(0).oriented(), "and its orientation");
        assertEquals("minecraft:barrel", props.variants().get(1).block(),
                "a literal beside a role is untouched");
        assertEquals(PropPlacement.CORNER, props.placement(),
                "placement is a rule, not a material -- nothing to resolve");
    }

    @Test
    void aSlotOfLiteralsIsNotEvenCopied() {
        PropConfig props = decode(PropConfig.CODEC,
                "{\"variants\": [{\"block\": \"minecraft:barrel\"}]}");
        assertSame(props, props.withRoles(role -> "minecraft:dirt"));
    }

    @Test
    void anUndeclaredRoleInAPropIsReportedAtLoad() {
        List<String> problems = new ArrayList<>();
        MotifConfigFragment.resolve(List.of(decode(MotifConfigFragment.CODEC,
                "{\"schemes\": [{\"name\": \"store\", \"props\":"
                        + " {\"variants\": [{\"block\": \"$cask\"}]}}]}")),
                problems::add);
        assertEquals(1, problems.size(), () -> problems.toString());
        assertTrue(problems.get(0).contains("$cask"), () -> problems.get(0));
        assertTrue(problems.get(0).contains("store"), () -> problems.get(0));
    }

    // ---- on the scheme ---------------------------------------------------------------------------

    /**
     * The slot's own {@link SizeGate} is honoured, and validated on load like every other slot's.
     * Worth its own case here because {@code corner} and {@code flanking_door} offer a handful of
     * cells whatever the room's size, so a gate is the only way to keep a small-room slot out of a
     * hall.
     */
    @Test
    void thePropsSlotGatesItselfOnRoomSize() {
        RoomScheme scheme = decode(RoomScheme.CODEC,
                "{\"name\": \"store\", \"props\": {\"min_size\": 9,"
                        + " \"variants\": [{\"block\": \"minecraft:barrel\"}]}}");
        assertEquals(Optional.empty(), scheme.propsFor(7, 7, 8));
        assertTrue(scheme.propsFor(11, 11, 8).isPresent());
        assertTrue(scheme.declaresAnySlot());
        assertTrue(scheme.drawsAnything(11, 11, 8));
    }

    @Test
    void anInvertedGateOnThePropsSlotIsALoadError() {
        String message = error(RoomScheme.CODEC,
                "{\"name\": \"store\", \"props\": {\"min_size\": 9, \"max_size\": 5,"
                        + " \"variants\": [{\"block\": \"minecraft:barrel\"}]}}");
        assertTrue(message.contains("props"), () -> message);
    }

    /** A child's props slot replaces the parent's, and an absent one inherits it whole. */
    @Test
    void thePropsSlotInherits() {
        RoomScheme parent = decode(RoomScheme.CODEC,
                "{\"name\": \"base\", \"abstract\": true,"
                        + " \"props\": {\"variants\": [{\"block\": \"minecraft:barrel\"}]}}");
        RoomScheme child = decode(RoomScheme.CODEC, "{\"name\": \"store\", \"extends\": \"base\"}");
        assertEquals(parent.props(), child.inheritFrom(parent).props());

        RoomScheme override = decode(RoomScheme.CODEC,
                "{\"name\": \"cells\", \"extends\": \"base\","
                        + " \"props\": {\"variants\": [{\"block\": \"minecraft:iron_bars\"}]}}");
        assertEquals(override.props(), override.inheritFrom(parent).props());
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static PropConfig slotOf(MotifConfig motif) {
        return motif.forFloor(0).schemes().stream()
                .filter(scheme -> "store".equals(scheme.name())).findFirst().orElseThrow()
                .props().orElseThrow();
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
