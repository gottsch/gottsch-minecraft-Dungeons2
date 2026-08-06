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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.IDungeonWallGenerator;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code classic}'s shipped {@code pilastered_hall} scheme is authored to be.
 *
 * <h2>What this can and cannot check, and why</h2>
 * <p>The scheme's pilasters are {@code dungeonblocks:} blocks, and <strong>a bare
 * {@code Bootstrap.bootStrap()} registers no mod blocks</strong>, so their ids do not resolve here
 * at all (the trap the Aug-05 session documented after a probe returned a confident, meaningless
 * zero). Rendering them offline is therefore impossible, and asserting on the blocks would be
 * asserting on the bootstrap rather than on the scheme.</p>
 *
 * <p>So this test covers the two things that <em>are</em> honest offline: the authored shape, and
 * the degradation behaviour when one pattern of a list cannot resolve. The pilaster geometry itself
 * is covered against vanilla blocks in {@code PilastersWallPatternProviderTest}.</p>
 *
 * @author Mark Gottschling on Aug 5, 2026
 */
class PilasteredHallSchemeTest {

    private static final int WIDTH = 13;
    private static final int DEPTH = 13;
    private static final int HEIGHT = 8;
    private static final int FLOOR_Y = 60;
    private static final int ORIGIN = 10;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MotifConfig classic() {
        return MotifConfigs.load("classic");
    }

    private static RoomScheme pilasteredHall() {
        return classic().schemes().stream()
                .filter(s -> "pilastered_hall".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "classic no longer ships a 'pilastered_hall' scheme; this test is about it"));
    }

    /** The scheme has to survive being authored: an unparseable one never reaches the roll. */
    @Test
    void classicShipsTheSchemeAsACourseAndAPilaster() {
        RoomScheme scheme = pilasteredHall();
        List<WallPatternEntry.PatternEntry> patterns = scheme.wall().orElseThrow().patterns();

        assertEquals(3, patterns.size(), "a band, the rhythm crossing it, and the corner piers");
        assertTrue(patterns.get(0).isCourses());
        assertTrue(patterns.get(1).isPilasters());
        assertFalse(patterns.get(1).isEndPilasters(), "the second is the evenly spaced rhythm");
        assertTrue(patterns.get(2).isEndPilasters(), "the third is the paired corner");
        assertTrue(patterns.get(1).projection() > 0 && patterns.get(2).projection() > 0,
                "the strips stand out from the wall; flush they are panelling, not pilasters");
    }

    /**
     * Both strip patterns author a real capital: the same block as the plinth, at the opposite
     * value of {@code base}.
     *
     * <p>{@code dungeonblocks}' {@code *_pillar_base_block} became orientable on 2026-08-06
     * (verified against the republished 3.0.0 jar: {@code base=up} is the unrotated model,
     * {@code base=down} is it flipped), which is what makes a capital expressible at all. The
     * remaining half was on this side &mdash; a single shared {@code properties} map could not give
     * the two rows opposite values.</p>
     *
     * <p><strong>The values are the opposite of what the property name suggests</strong>, confirmed
     * in game after authoring them the other way round first: the row sitting on the FLOOR wants
     * {@code base=up}, and the capital under the ceiling wants {@code base=down}. That is the same
     * class of surprise as Backlog #25 &mdash; dungeonblocks trim reads inverted relative to the
     * obvious reading &mdash; and it is not derivable at runtime, which is why it is pinned here.
     * Getting it wrong renders both ends upside down and nothing errors.</p>
     *
     * <p>Asserted on the authored config rather than on blocks, for the headless reason above.</p>
     */
    @Test
    void bothStripPatternsAuthorAnInvertedCapital() {
        for (WallPatternEntry.PatternEntry pattern : pilasteredHall().wall().orElseThrow().patterns()) {
            if (!pattern.isPilasters()) {
                continue;
            }
            assertEquals(pattern.baseBlockOrBase(), pattern.capBlockOrBase(),
                    "a capital is the plinth block, not a different one");
            assertEquals("up", pattern.basePropertiesOrBase().get("base"),
                    pattern.type() + ": the plinth sits on the floor -- 'up', counter-intuitively");
            assertEquals("down", pattern.capPropertiesOrBase().get("base"),
                    pattern.type() + ": the capital is the same block inverted");
        }
    }

    /**
     * The scheme carries pots <em>and</em> floor-level projecting trim &mdash; the combination that
     * used to fail the build.
     *
     * <p>It was forbidden because a pot would spawn inside the trim, and it could not simply be
     * allowed for pilasters: a pilaster takes those cells by construction, on every wall, rather
     * than through an authoring choice that could be made differently. The resolution was to make the
     * prop pass aware of the cells instead, which is what this scheme exercises in game.</p>
     */
    @Test
    void theSchemeCombinesPotsWithProjectingTrim() {
        RoomScheme scheme = pilasteredHall();
        assertTrue(scheme.pots().isPresent(), "the point of the scheme is that both can coexist");
        assertTrue(scheme.wall().orElseThrow().patterns().stream()
                        .anyMatch(p -> p.isPilasters() && p.projection() > 0),
                "and that the trim really does reach floor level");
    }

    /**
     * One pattern failing to resolve does not take the rest of the list with it.
     *
     * <p>Pinned against shipped content rather than a fixture, because this is exactly the situation
     * offline: the {@code dungeonblocks} pilasters cannot resolve, the vanilla course can, and the
     * course must still draw. Under the old degrade-the-whole-entry rule the wall would come out
     * bare. Two patterns are two authored decisions, and losing the visible one to a problem with
     * the other hides which is broken.</p>
     */
    @Test
    void theVanillaCourseStillDrawsWhenTheModPilastersCannotResolve() {
        RoomScheme scheme = pilasteredHall();
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, WIDTH, DEPTH, HEIGHT, RoomRole.NORMAL);
        List<BlockPlacement> blocks = new ArrayList<>();

        new BasicRoomGenerator().withMotifConfig(classic())
                .selectWallGenerator(DungeonMotif.CLASSIC, scheme, WIDTH, DEPTH, HEIGHT)
                .build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), blocks);

        // The band is top-anchored polished andesite; the wall's top row is floorY + height - 2.
        int topRowY = FLOOR_Y + HEIGHT - 2;
        assertTrue(blocks.stream().anyMatch(bp -> bp.getY() == topRowY
                        && "minecraft:polished_andesite".equals(bp.getBlockId())),
                "the vanilla course should survive the unresolvable pilasters beside it");
    }

    /**
     * With the pilasters unresolvable there is no projecting trim, so nothing is reserved and the
     * pots fall back to the plain eligible ring. Stated as a test so the offline result is not
     * mistaken for evidence that the reservation does not happen in game.
     */
    @Test
    void offlineTheresNoTrimToReserveSoPotsUseTheWholeRing() {
        RoomScheme scheme = pilasteredHall();
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, WIDTH, DEPTH, HEIGHT, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();

        BasicRoomGenerator generator = new BasicRoomGenerator().withMotifConfig(classic());
        IDungeonWallGenerator wallGen =
                generator.selectWallGenerator(DungeonMotif.CLASSIC, scheme, WIDTH, DEPTH, HEIGHT);
        wallGen.build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out.getBlocks());

        Set<Coords2D> occupied = wallGen.occupiedFloorCells();
        assertTrue(occupied.isEmpty(),
                "offline the dungeonblocks pilasters do not resolve, so they claim nothing");

        List<EntityPlacement> pots = new ArrayList<>();
        RoomPropGenerator.placePots(room, FLOOR_Y, scheme.pots().orElseThrow(), occupied,
                RandomSource.create(7L), pots);
        assertFalse(pots.isEmpty(), "the scheme should still furnish the room");
        List<Coords2D> eligible = RoomPropGenerator.eligibleCells(room);
        for (EntityPlacement pot : pots) {
            assertTrue(eligible.contains(new Coords2D(pot.getX(), pot.getZ())),
                    "pot at " + pot.getX() + "," + pot.getZ() + " is off the eligible ring");
        }
    }
}
