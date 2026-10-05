package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code hangings} slot's schema: its defaults, and what is a load error. */
class HangingConfigTest {

    private static DataResult<HangingConfig> parse(String json) {
        return HangingConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static String errorOf(String json) {
        return parse(json).error().orElseThrow().message();
    }

    @Test
    void theDefaultsAreOneOrTwoAnywhereOnAPlainChainAtMostOneLinkDown() {
        HangingConfig config = parse("{\"variants\": [{\"block\": \"dungeonblocks:censer\"}]}")
                .result().orElseThrow();
        assertEquals(1, config.minCount());
        assertEquals(2, config.maxCount());
        assertEquals(HangingConfig.HangingPlacement.FREE, config.placement());
        assertEquals("minecraft:chain", config.chain());
        assertEquals(0, config.minDrop());
        assertEquals(1, config.maxDrop());
        assertEquals(1, config.variants().get(0).weight());
    }

    @Test
    void aMisspeltKeyIsALoadError() {
        assertTrue(errorOf("{\"variants\": [{\"block\": \"dungeonblocks:censer\"}], \"max_dorp\": 2}")
                .contains("max_dorp"));
    }

    @Test
    void anUnknownPlacementIsALoadError() {
        assertTrue(parse("{\"placement\": \"center\", \"variants\": [{\"block\": \"dungeonblocks:censer\"}]}")
                .error().isPresent(), "'center' is not 'centre' -- a typo, not a default");
    }

    @Test
    void aSceneWithASlotRoundTripsThroughTheScheme() {
        RoomScheme scheme = RoomScheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"name\": \"s\", \"hangings\": {\"placement\": \"centre\", \"min_count\": 1,"
                        + " \"max_count\": 1, \"variants\": [{\"block\": \"dungeonblocks:chandelier\"}]}}"))
                .result().orElseThrow();
        assertEquals(HangingConfig.HangingPlacement.CENTRE,
                scheme.hangingsFor(9, 9, 7).orElseThrow().placement());
        assertTrue(scheme.declaresAnySlot());
    }

    @Test
    void rolesResolveInTheVariantsAndTheChain() {
        HangingConfig config = new HangingConfig(1, 1, HangingConfig.HangingPlacement.FREE,
                List.of(new HangingConfig.HangingVariant("$fixture")), "$chain", 0, 1,
                SizeGate.UNBOUNDED);
        HangingConfig resolved = config.withRoles(role -> switch (role) {
            case "fixture" -> "dungeonblocks:meat_hook";
            case "chain" -> "dungeonblocks:rusty_chain";
            default -> null;
        });
        assertEquals("dungeonblocks:meat_hook", resolved.variants().get(0).block());
        assertEquals("dungeonblocks:rusty_chain", resolved.chain());

        HangingConfig literal = new HangingConfig(1, 1, HangingConfig.HangingPlacement.FREE,
                List.of(new HangingConfig.HangingVariant("dungeonblocks:censer")), 0, 1);
        assertSame(literal, literal.withRoles(role -> "x"), "nothing named a role: no allocation");
    }
}
