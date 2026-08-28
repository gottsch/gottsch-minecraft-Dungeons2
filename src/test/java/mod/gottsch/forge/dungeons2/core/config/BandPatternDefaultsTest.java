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
import mod.gottsch.forge.dungeons2.core.config.floor.PlainFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.SpeckleFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.PanelsWallPattern;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.BasicFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RandomSpeckleFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.CompositeWallPatternProvider;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // ---------- tier 1: the scheme wins the ceiling and floor, and COMPOSES on the wall ----------

    /**
     * WALLS COMPOSE (Gottsch, 2026-08-27). The band draws first and the scheme draws on top, so a
     * room that asked for courses gets its courses AND keeps the band's panels underneath -- a wall
     * is a stack of horizontal bands at different anchors, so the two occupy different rows and
     * read as one wall.
     *
     * <p>Asserted as a composite of both providers, in that order. Before this the scheme replaced
     * the band outright, which made a band wall pattern dead weight: ten of classic's eleven
     * schemes name a wall slot, so a shipped band plinth drew in 0% of rooms.
     */
    @Test
    void aSchemesWallComposesOnTopOfTheBandsPattern() {
        ISurfacePatternProvider provider = WallPatternSelector.providerFor(
                Optional.of(courses()), wallPaving(panels()), 11, 11, 7);

        assertInstanceOf(CompositeWallPatternProvider.class, provider,
                "the band's pattern and the scheme's should both draw");
        assertEquals(List.of(PanelsWallPatternProvider.class, CoursesWallPatternProvider.class),
                ((CompositeWallPatternProvider) provider).providers().stream()
                        .map(Object::getClass).map(c -> (Class<?>) c).toList(),
                "band first, scheme second -- the overlay is in list order, so the SCHEME wins a"
                        + " cell they both claim");
    }

    /**
     * The ceiling and floor still REPLACE, and the asymmetry is the point: they are single
     * surfaces, so two treatments fight over the same cells and one simply loses. Composing them
     * would take a band ceiling to 100% incidence, which is the complaint that started this.
     */
    @Test
    void aSchemesCeilingAndFloorStillBeatTheBandsPattern() {
        assertInstanceOf(GridSurfacePatternProvider.class,
                CeilingPatternSelector.providerFor(Optional.of(coffers()),
                        ceilingPaving(coffers()), 11, 11, 7),
                "a room that asked for coffers asked for them at every depth");
        assertInstanceOf(BasicFloorGenerator.class,
                FloorPatternSelector.generatorFor(
                        Optional.of(new FloorPatternEntry(new PlainFloorPattern())),
                        floorPaving(new FloorPatternEntry(new SpeckleFloorPattern(
                                "minecraft:cobblestone", "minecraft:packed_mud", 0.12)))),
                "and the floor, whose scheme asked for plain -- the band's speckle must not"
                        + " reappear underneath it");
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

    /**
     * The band's ENTRY-level gate, as opposed to the per-pattern gate above. It was never consulted
     * before composition -- {@code forRoom} filters the patterns inside an entry, not the entry
     * itself, and only a scheme's entry passed through {@code RoomScheme#wallFor} on the way in. A
     * band pattern gated on the entry therefore drew in rooms below its own minimum. Nothing
     * shipped authored one, which is why it went unseen rather than being reported.
     */
    @Test
    void aBandsEntryLevelGateIsRespectedToo() {
        WallPatternEntry gated = new WallPatternEntry(
                List.of(new WallPatternEntry.PatternEntry(new PanelsWallPattern(BLOCK, 3, 3, 0, 0,
                        WallPatternEntry.CourseOrient.NONE, java.util.Map.of()))),
                new SizeGate(0, 13, Optional.empty(), Optional.empty()));

        assertNull(WallPatternSelector.providerFor(Optional.empty(), wallPaving(gated), 9, 9, 7),
                "the entry's own gate says this room is too small for the band's dressing");
        assertInstanceOf(PanelsWallPatternProvider.class,
                WallPatternSelector.providerFor(Optional.empty(), wallPaving(gated), 13, 13, 7));
    }

    /**
     * Each tier is gated on its own, so a band gated out of a room leaves the scheme's wall drawing
     * alone rather than taking it down with it -- and unwrapped, exactly as it drew before walls
     * composed at all.
     */
    @Test
    void aGatedOutBandLeavesTheSchemesWallUntouched() {
        WallPatternEntry gated = new WallPatternEntry(
                List.of(new WallPatternEntry.PatternEntry(new PanelsWallPattern(BLOCK, 3, 3, 0, 0,
                        WallPatternEntry.CourseOrient.NONE, java.util.Map.of()))),
                new SizeGate(0, 13, Optional.empty(), Optional.empty()));

        assertInstanceOf(CoursesWallPatternProvider.class,
                WallPatternSelector.providerFor(Optional.of(courses()), wallPaving(gated), 9, 9, 7));
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
