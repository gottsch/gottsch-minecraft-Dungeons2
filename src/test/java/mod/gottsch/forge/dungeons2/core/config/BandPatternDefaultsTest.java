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

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CoffersCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.SpeckleFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.PanelsWallPattern;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.BasicFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RandomSpeckleFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.CoursesWallPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.PanelsWallPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.WallPatternSelector;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <strong>All three surface sections carry a {@code pattern}, and all three resolve it the same
 * way.</strong>
 *
 * <h2>Why this test exists as one file rather than three</h2>
 * <p>{@code FloorConfig} got its {@code pattern} slot first, alone, because the mud stratum needed
 * cobble paving. That left a real asymmetry for a day: a depth band could pave its floors but could
 * not course its walls or joist its ceilings, so the band was a repainted classic room rather than
 * a different depth. Wall and ceiling were added to match.</p>
 *
 * <p>The point of asserting them together is that the <em>rule</em> is one rule. Each selector
 * resolves it separately, so three copies could drift; a single test that runs the same three tiers
 * against all three sections is what stops that.</p>
 */
class BandPatternDefaultsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String BLOCK = "minecraft:stone_bricks";

    private static WallConfig wallPaving(WallPatternEntry pattern) {
        return new WallConfig(BLOCK, Optional.of(pattern));
    }

    private static CeilingConfig ceilingPaving(CeilingPatternEntry pattern) {
        return new CeilingConfig(BLOCK, Optional.of(pattern));
    }

    private static FloorConfig floorPaving(FloorPatternEntry pattern) {
        return new FloorConfig(BLOCK, BLOCK, Optional.of(pattern));
    }

    private static WallPatternEntry panels() {
        return new WallPatternEntry(List.of(new WallPatternEntry.PatternEntry(
                new PanelsWallPattern(BLOCK, 3, 3, 0, 0, WallPatternEntry.CourseOrient.NONE,
                        java.util.Map.of()))));
    }

    private static WallPatternEntry courses() {
        return WallPatternEntry.ofCourses(List.of(new WallPatternEntry.CourseEntry(
                BLOCK, WallPatternEntry.CourseAnchor.BOTTOM, 0)));
    }

    private static CeilingPatternEntry coffers() {
        return new CeilingPatternEntry(List.of(new CeilingPatternEntry.SurfacePatternEntry(
                new CoffersCeilingPattern(BLOCK))));
    }

    // ---------- tier 2: the band's own pattern draws when the scheme names none ----------

    @Test
    void aBandsOwnPatternIsUsedWhenTheSchemeNamesNoSlot() {
        assertInstanceOf(PanelsWallPatternProvider.class,
                WallPatternSelector.providerFor(Optional.empty(), wallPaving(panels()), 11, 11, 7),
                "a scheme with no wall slot must fall through to the band's own dressing");
        assertInstanceOf(GridSurfacePatternProvider.class,
                CeilingPatternSelector.providerFor(Optional.empty(), ceilingPaving(coffers()), 11, 11, 7),
                "and likewise the ceiling");
        assertInstanceOf(RandomSpeckleFloorPatternProvider.class,
                FloorPatternSelector.generatorFor(Optional.empty(),
                        floorPaving(new FloorPatternEntry(new SpeckleFloorPattern(
                                "minecraft:cobblestone", "minecraft:packed_mud", 0.12)))),
                "and the floor, which had this first");
    }

    // ---------- tier 1: the scheme wins ----------

    @Test
    void aSchemesOwnSlotBeatsTheBandsPattern() {
        assertInstanceOf(CoursesWallPatternProvider.class,
                WallPatternSelector.providerFor(Optional.of(courses()), wallPaving(panels()), 11, 11, 7),
                "a room that asked for courses asked for them at every depth -- the band is the"
                        + " default underneath, never an override on top");
    }

    // ---------- tier 3: nothing anywhere is still plain ----------

    @Test
    void withNoSchemeSlotAndNoBandPatternTheSurfaceIsPlain() {
        assertNull(WallPatternSelector.providerFor(Optional.empty(), WallConfig.DEFAULT, 11, 11, 7));
        assertNull(CeilingPatternSelector.providerFor(Optional.empty(), CeilingConfig.DEFAULT, 11, 11, 7));
        assertInstanceOf(BasicFloorGenerator.class,
                FloorPatternSelector.generatorFor(Optional.empty(), FloorConfig.DEFAULT));
    }

    /**
     * A band's pattern is gated exactly as a scheme's is. A default is still a treatment, and a
     * pattern gated on room size means the same thing whichever tier authored it -- so this must
     * not quietly become "the band always draws".
     */
    @Test
    void aBandsPatternIsStillSubjectToItsOwnSizeGate() {
        WallPatternEntry gated = new WallPatternEntry(
                List.of(new WallPatternEntry.PatternEntry(
                        new PanelsWallPattern(BLOCK, 3, 3, 0, 0,
                                WallPatternEntry.CourseOrient.NONE, java.util.Map.of()),
                        new SizeGate(0, 13, Optional.empty(), Optional.empty()))));

        assertNull(WallPatternSelector.providerFor(Optional.empty(), wallPaving(gated), 9, 9, 7),
                "too small for the gate: the band's dressing drops out, same as a scheme's would");
        assertInstanceOf(PanelsWallPatternProvider.class,
                WallPatternSelector.providerFor(Optional.empty(), wallPaving(gated), 13, 13, 7));
    }

    // ---------- the authored shape ----------

    @Test
    void allThreeSectionsDecodeAPatternFromJson() {
        assertTrue(WallConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"wall\": \"" + BLOCK + "\", \"pattern\": {\"patterns\": ["
                                + "{\"type\": \"dungeons2:courses\", \"config\": {\"courses\": ["
                                + "{\"block\": \"" + BLOCK + "\"}]}}]}}"))
                .result().orElseThrow().pattern().isPresent());

        assertTrue(CeilingConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"ceiling\": \"" + BLOCK + "\", \"pattern\": {\"patterns\": ["
                                + "{\"type\": \"dungeons2:coffers\", \"config\": {"
                                + "\"block\": \"" + BLOCK + "\"}}]}}"))
                .result().orElseThrow().pattern().isPresent());
    }

    /** A malformed pattern is a load error, not silently an absent one -- #31. */
    @Test
    void aMalformedBandPatternIsALoadError() {
        assertTrue(WallConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"wall\": \"" + BLOCK + "\", \"pattern\": {\"patterns\": ["
                                + "{\"type\": \"dungeons2:nonesuch\"}]}}"))
                .error().isPresent());
        assertTrue(CeilingConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"ceiling\": \"" + BLOCK + "\", \"pattern\": {\"patterns\": ["
                                + "{\"type\": \"dungeons2:coffers\"}]}}"))
                .error().isPresent(), "coffers with no block");
    }

    /** Nothing authored keeps the pre-existing shape: absent, not a defaulted empty treatment. */
    @Test
    void anAbsentPatternStaysAbsent() {
        assertTrue(WallConfig.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"wall\": \"" + BLOCK + "\"}"))
                .result().orElseThrow().pattern().isEmpty());
        assertTrue(CeilingConfig.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"ceiling\": \"" + BLOCK + "\"}"))
                .result().orElseThrow().pattern().isEmpty());
    }
}
