/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.integration.EchelonsIntegration;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two halves of Enemy Echelons scaling that live in the motif config: a band's
 * {@code difficulty} (which step a floor is at) and the motif's {@code echelon} block (what a step
 * is worth). Nothing here needs EE on the classpath &mdash; which is the point of keeping every EE
 * type inside {@link EchelonsIntegration}.
 */
class EchelonConfigTest {

    private static <T> DataResult<T> parse(com.mojang.serialization.Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static final String SETS = "\"mob_sets\":[{\"mob_set\":\"dungeons2:classic_vermin\"}]";

    // ---- band difficulty -------------------------------------------------------------------

    @Test
    void absentDifficultyMeansNoOpinion() {
        MobSetBand band = parse(MobSetBand.CODEC, "{" + SETS + "}").getOrThrow(false, e -> {});
        assertTrue(band.difficulty().isEmpty());
        assertEquals(OptionalInt.empty(), band.drawDifficulty(RandomSource.create(1L)),
                "a band with no difficulty must leave the mob to SMB, not scale it to 0");
    }

    @Test
    void aPlainNumberIsOneFixedDifficulty() {
        MobSetBand band = parse(MobSetBand.CODEC, "{" + SETS + ",\"difficulty\":3}")
                .getOrThrow(false, e -> {});
        assertEquals(List.of(new MobSetBand.DifficultyEntry(3, 1)), band.difficulty());
        assertEquals(OptionalInt.of(3), band.drawDifficulty(RandomSource.create(1L)));
    }

    /** 0 is a real answer -- "unscaled, and SMB keep out" -- and must not read as absent. */
    @Test
    void zeroIsADifficultyNotAnAbsence() {
        MobSetBand band = parse(MobSetBand.CODEC, "{" + SETS + ",\"difficulty\":0}")
                .getOrThrow(false, e -> {});
        assertEquals(OptionalInt.of(0), band.drawDifficulty(RandomSource.create(1L)));
    }

    @Test
    void aWeightedListDrawsEveryEntry() {
        MobSetBand band = parse(MobSetBand.CODEC, "{" + SETS + ",\"difficulty\":["
                + "{\"difficulty\":2,\"weight\":3},{\"difficulty\":4}]}").getOrThrow(false, e -> {});
        RandomSource random = RandomSource.create(7L);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(band.drawDifficulty(random).getAsInt());
        }
        assertEquals(Set.of(2, 4), seen);
    }

    @Test
    void badDifficultiesAreLoadErrors() {
        assertTrue(parse(MobSetBand.CODEC, "{" + SETS + ",\"difficulty\":-1}").error().isPresent(),
                "-1 is EE's 'not set' sentinel, not something an author may write");
        assertTrue(parse(MobSetBand.CODEC, "{" + SETS + ",\"difficulty\":[]}").error().isPresent(),
                "an empty list must not quietly mean 'absent'");
        assertTrue(parse(MobSetBand.CODEC, "{" + SETS + ",\"difficulty\":[{\"difficulty\":2,"
                + "\"wieght\":3}]}").error().isPresent(), "a typo inside an entry is a load error");
    }

    @Test
    void difficultyRoundTripsInTheShapeItWasWritten() {
        for (String difficulty : List.of("3", "[{\"difficulty\":2,\"weight\":3},{\"difficulty\":4,\"weight\":1}]")) {
            MobSetBand band = parse(MobSetBand.CODEC, "{" + SETS + ",\"difficulty\":" + difficulty + "}")
                    .getOrThrow(false, e -> {});
            JsonElement encoded = MobSetBand.CODEC.encodeStart(JsonOps.INSTANCE, band).getOrThrow(false, e -> {});
            assertEquals(JsonParser.parseString(difficulty), encoded.getAsJsonObject().get("difficulty"));
            assertEquals(band, MobSetBand.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(false, e -> {}));
        }
    }

    // ---- motif echelon block ---------------------------------------------------------------

    @Test
    void everyFactorDefaultsToZero() {
        EchelonConfig config = parse(EchelonConfig.CODEC, "{\"hp_factor\":0.2}").getOrThrow(false, e -> {});
        assertEquals(0.2D, config.hpFactor());
        assertEquals(0.0D, config.damageFactor());
        assertEquals(0.0D, config.speedFactor());
        assertEquals(Optional.empty(), config.maxXp());
        assertTrue(config.mobBlacklist().isEmpty());
    }

    /**
     * EE 1.0.1 declares max_hp and friends and applies none of them, and applies speed_factor to
     * fliers itself. A key that parses and does nothing is what the closed schema exists to stop.
     */
    @Test
    void keysEeWouldIgnoreAreLoadErrors() {
        for (String inert : List.of("max_hp", "max_damage", "flying_speed_factor", "max_speed")) {
            assertTrue(parse(EchelonConfig.CODEC, "{\"" + inert + "\":1.0}").error().isPresent(), inert);
        }
        assertTrue(parse(EchelonConfig.CODEC, "{\"hp_factor\":-0.1}").error().isPresent(),
                "EE ignores a factor that is not positive, so a negative one would be silently inert");
    }

    @Test
    void xpScalesPerStepAndCaps() {
        EchelonConfig config = parse(EchelonConfig.CODEC, "{\"xp_factor\":0.5,\"max_xp\":12}")
                .getOrThrow(false, e -> {});
        assertEquals(5, config.scaleXp(5, 0));
        assertEquals(10, config.scaleXp(5, 2));
        assertEquals(12, config.scaleXp(5, 4), "capped by max_xp");
    }

    // ---- merge -----------------------------------------------------------------------------

    private static MotifConfigFragment echelonFragment(String json) {
        return parse(MotifConfigFragment.CODEC, "{\"echelon\":" + json + "}").getOrThrow(false, e -> {});
    }

    @Test
    void aLaterEchelonBlockReplacesTheWholeBlock() {
        MotifConfig merged = MotifConfigFragment.resolve(List.of(
                echelonFragment("{\"hp_factor\":0.2,\"damage_factor\":0.3}"),
                echelonFragment("{\"hp_factor\":0.5}")));
        assertEquals(0.5D, merged.echelon().orElseThrow().hpFactor());
        assertEquals(0.0D, merged.echelon().orElseThrow().damageFactor(),
                "field-merging two packs' factors is a balance nobody authored");
    }

    @Test
    void aFragmentWithoutEchelonKeepsTheEarlierOne() {
        MotifConfig merged = MotifConfigFragment.resolve(List.of(
                echelonFragment("{\"hp_factor\":0.2}"),
                parse(MotifConfigFragment.CODEC, "{}").getOrThrow(false, e -> {})));
        assertEquals(0.2D, merged.echelon().orElseThrow().hpFactor());
    }

    /**
     * forFloor builds a NEW MotifConfig for a stratum band and for a palette-only motif. Every
     * section a copy forgets is silently gone on those floors, and a dungeon whose deep floors lost
     * their echelon block would just be easier there -- nothing would look wrong.
     */
    @Test
    void theEchelonBlockSurvivesEveryPerFloorProjection() {
        EchelonConfig echelon = parse(EchelonConfig.CODEC, "{\"hp_factor\":0.2}").getOrThrow(false, e -> {});
        Stratum band = new Stratum(0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        MotifConfig stratified = new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT,
                DoorConfig.DEFAULT, CorridorConfig.DEFAULT, FloorConfig.DEFAULT,
                List.of(RoomScheme.PLAIN), List.of(), List.of(), java.util.Map.of(), List.of(band),
                java.util.Map.of(), Optional.of(echelon));
        assertEquals(Optional.of(echelon), stratified.forFloor(2).echelon(), "stratum projection");

        MotifConfig paletted = new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT,
                DoorConfig.DEFAULT, CorridorConfig.DEFAULT, FloorConfig.DEFAULT,
                List.of(RoomScheme.PLAIN), List.of(), List.of(), java.util.Map.of(), List.of(),
                java.util.Map.of("shaft", "minecraft:stone"), Optional.of(echelon));
        assertEquals(Optional.of(echelon), paletted.forFloor(2).echelon(), "palette-only projection");
    }

    // ---- the guard -------------------------------------------------------------------------

    /** Headless, ModList is null: EE is absent by definition, and nothing may touch its classes. */
    @Test
    void theIntegrationIsInertWithoutEe() {
        assertFalse(EchelonsIntegration.isLoaded());
        assertFalse(EchelonsIntegration.apply(null,
                parse(EchelonConfig.CODEC, "{}").getOrThrow(false, e -> {}), 2));
        assertEquals(OptionalInt.empty(), EchelonsIntegration.difficultyOf(null));
    }
}
