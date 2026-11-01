package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheme-level door-count gate ({@code min_doors}/{@code max_doors}) and the {@code secret}
 * object, as JSON: what decodes, what is a load error, and what an absent key means.
 */
class DoorGateAndSecretCodecTest {

    private static final Gson GSON = new Gson();

    private static DataResult<RoomScheme> parse(String json) {
        return RoomScheme.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static RoomScheme decode(String json) {
        return parse(json).result().orElseThrow(() -> new AssertionError(
                "expected to decode: " + json + " -- " + parse(json).error().map(e -> e.message())));
    }

    private static String errorOf(String json) {
        return parse(json).error().orElseThrow(() -> new AssertionError("expected a load error: " + json))
                .message();
    }

    @Test
    void aSchemeWithNeitherKeyFitsAnyDoorCountAndIsNotSecret() {
        RoomScheme scheme = decode("{\"name\":\"plain\"}");
        assertEquals(DoorRange.ANY, scheme.doors());
        assertFalse(scheme.isSecret());
        for (int doors = 1; doors <= 6; doors++) {
            assertTrue(scheme.fitsDoors(doors));
        }
    }

    @Test
    void bothBoundsAreInclusiveAndFlatOnTheScheme() {
        RoomScheme scheme = decode("{\"name\":\"junction\",\"min_doors\":3,\"max_doors\":4}");
        assertEquals(new DoorRange(3, Optional.of(4)), scheme.doors());
        assertFalse(scheme.fitsDoors(2));
        assertTrue(scheme.fitsDoors(3));
        assertTrue(scheme.fitsDoors(4));
        assertFalse(scheme.fitsDoors(5));
    }

    /** A caller that does not know the room's doors passes -1, and no gate may reject on that. */
    @Test
    void anUnknownDoorCountPassesEveryGate() {
        assertTrue(decode("{\"name\":\"dead_end\",\"max_doors\":1}").fitsDoors(-1));
        assertTrue(decode("{\"name\":\"junction\",\"min_doors\":4}").fitsDoors(-1));
    }

    @Test
    void anInvertedRangeIsALoadError() {
        String error = errorOf("{\"name\":\"odd\",\"min_doors\":3,\"max_doors\":2}");
        assertTrue(error.contains("max_doors 2 is below min_doors 3"), error);
    }

    /** Every room has a way in, so max_doors 0 could only switch a scheme off everywhere. */
    @Test
    void maxDoorsZeroIsALoadError() {
        errorOf("{\"name\":\"odd\",\"max_doors\":0}");
    }

    @Test
    void anEmptySecretObjectIsASecretRoomWithNoPedestal() {
        RoomScheme scheme = decode("{\"name\":\"vault\",\"secret\":{}}");
        assertTrue(scheme.isSecret());
        assertTrue(scheme.secret().orElseThrow().pedestalTables().isEmpty());
    }

    @Test
    void theSecretObjectCarriesWeightedPedestalTables() {
        RoomScheme scheme = decode("{\"name\":\"vault\",\"secret\":{\"pedestal_loot_tables\":"
                + "[{\"loot_table\":\"dungeons2:pedestals/secret\",\"weight\":2}]}}");
        assertEquals(1, scheme.secret().orElseThrow().pedestalTables().size());
        assertEquals(2, scheme.secret().orElseThrow().pedestalTables().get(0).weight());
    }

    /** Closed like every other config object: a misspelled key fails the pack. */
    @Test
    void aMisspelledSecretKeyIsALoadError() {
        errorOf("{\"name\":\"vault\",\"secret\":{\"pedestal_loot_table\":[]}}");
    }

    /** A secret room has one door by construction, so min_doors above one fits nothing it could be. */
    @Test
    void aSecretSchemeAskingForMoreThanOneDoorIsALoadError() {
        String error = errorOf("{\"name\":\"vault\",\"min_doors\":2,\"secret\":{}}");
        assertTrue(error.contains("a secret room has one door"), error);
    }

    /** Eligibility, not content: a child keeps its own door gate and secret, as it keeps its sizes. */
    @Test
    void doorsAndSecretAreNotInherited() {
        RoomScheme parent = decode("{\"name\":\"base\",\"max_doors\":1,\"secret\":{},\"abstract\":true}");
        RoomScheme child = decode("{\"name\":\"child\",\"extends\":\"base\"}").inheritFrom(parent);
        assertEquals(DoorRange.ANY, child.doors());
        assertFalse(child.isSecret());
    }
}
