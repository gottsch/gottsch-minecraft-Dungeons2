package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip and defaulting tests for the merged {@link MotifConfig} codec, replacing the old
 * {@code BlockProviderCodecTest}. Pure codec work &mdash; block ids stay as strings and are only
 * resolved against the registry at render time, so no Minecraft bootstrap is required here.
 */
class MotifConfigCodecTest {

    private static final Gson GSON = new Gson();

    @Test
    void fullMotifConfigRoundTrips() {
        MotifConfig config = new MotifConfig(
                new WallConfig("minecraft:stone_bricks"),
                new CeilingConfig("minecraft:stone_bricks"),
                new DoorConfig("dungeonblocks:spruce_dungeon_door",
                        "minecraft:polished_andesite", "minecraft:polished_andesite"),
                new CorridorConfig("minecraft:cobblestone", "minecraft:gravel", "minecraft:stone_bricks"),
                new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks",
                        List.of(new FloorPatternEntry("empty", 8, 0),
                                new FloorPatternEntry("border", 1, 2))));

        JsonElement json = MotifConfig.CODEC.encodeStart(JsonOps.INSTANCE, config).result().orElseThrow();
        MotifConfig back = MotifConfig.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(config, back);
    }

    /**
     * The catacombs/deep_slate case: a motif that only overrides its wall. Every absent section
     * must fall back to that section's DEFAULT, reproducing the pre-merge behaviour where a
     * BlockProvider with no entry for a pattern left the generator on its hardcoded stone_bricks.
     */
    @Test
    void absentSectionsFallBackToTheirDefaults() {
        JsonElement json = GSON.fromJson("{\"wall\": {\"wall\": \"minecraft:bricks\"}}", JsonElement.class);
        MotifConfig config = MotifConfig.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals("minecraft:bricks", config.wall().wall());
        assertEquals(CeilingConfig.DEFAULT, config.ceiling());
        assertEquals(DoorConfig.DEFAULT, config.door());
        assertEquals(CorridorConfig.DEFAULT, config.corridor());
        assertEquals(FloorConfig.DEFAULT, config.floor());
    }

    /**
     * A section that IS present must be complete -- there are no per-slot defaults, so a
     * half-authored section fails loudly at load rather than silently rendering someone else's
     * block. This is the failure mode the pre-merge string-to-enum lookup could not produce.
     */
    @Test
    void aPartiallyAuthoredSectionFailsToDecode() {
        JsonElement json = GSON.fromJson(
                "{\"door\": {\"door\": \"minecraft:oak_door\"}}", JsonElement.class);
        assertTrue(MotifConfig.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(),
                "a door section missing lintel/floor should fail to decode, not silently default");
    }

    @Test
    void floorPatternsDefaultToEmptyListButBaseBlocksAreRequired() {
        JsonElement ok = GSON.fromJson(
                "{\"floor\": {\"base\": \"minecraft:stone_bricks\", \"alternateBase\": \"minecraft:stone_bricks\"}}",
                JsonElement.class);
        MotifConfig config = MotifConfig.CODEC.parse(JsonOps.INSTANCE, ok).result().orElseThrow();
        assertTrue(config.floor().patterns().isEmpty(), "patterns is optional and defaults to empty");

        JsonElement missingBase = GSON.fromJson("{\"floor\": {\"patterns\": []}}", JsonElement.class);
        assertTrue(MotifConfig.CODEC.parse(JsonOps.INSTANCE, missingBase).error().isPresent(),
                "base/alternateBase are required when a floor section is present");
    }
}
