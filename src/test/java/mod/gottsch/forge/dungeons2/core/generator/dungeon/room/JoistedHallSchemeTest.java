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

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfacePatternEntry;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import mod.gottsch.forge.dungeons2.core.config.ceiling.JoistsCeilingPattern;

/**
 * What {@code classic}'s shipped {@code joisted_hall} scheme actually builds.
 *
 * <h2>What this pins that the provider's own unit tests cannot</h2>
 * <p>{@code SurfacePatternProvidersTest} asserts the geometry in {@code (u, v)} space. Three things
 * only appear once a real room is rendered, and all three are the kind that look fine in a plan:</p>
 *
 * <ul>
 *   <li><strong>The beams hang below a ceiling that is still there.</strong> A projected layer marks
 *       only its own cells, so a bug that emitted the beams <em>instead of</em> the plane would leave
 *       a run of open slots to the stone above.</li>
 *   <li><strong>The beams cross the room's shorter axis</strong>, which is the opposite of the
 *       colonnade's rule and therefore the easiest thing to "fix" into a bug.</li>
 *   <li><strong>The bracket is authored, and cannot be seen headlessly.</strong> A
 *       {@code dungeonblocks:} id resolves to nothing under a bare bootstrap, so the run comes out
 *       beam end to end here; the authored intent is asserted instead. See
 *       {@link #theRunEndsAreAuthoredAsCorbelsTurnedIntoTheRoom()}.</li>
 * </ul>
 *
 * @author Mark Gottschling on Aug 11, 2026
 */
class JoistedHallSchemeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Comfortably inside the scheme's gates (minSize 9, minHeight 7). Interior is 9x9. */
    private static final int SQUARE = 11;
    private static final int HEIGHT = 7;
    private static final int FLOOR_Y = 60;
    private static final int ORIGIN = 10;

    /** The row the beams hang in: one below the ceiling plane, per the scheme's projection. */
    private static final int BEAM_Y = FLOOR_Y + HEIGHT - 2;
    private static final int CEILING_Y = FLOOR_Y + HEIGHT - 1;

    private static MotifConfig classic() {
        return MotifConfigs.load("classic");
    }

    private static RoomScheme joistedHall() {
        return classic().schemes().stream()
                .filter(s -> "joisted_hall".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "classic no longer ships a 'joisted_hall' scheme; this test is about that scheme"));
    }

    /**
     * Builds the room with the scheme forced, indexed by position keeping the last write --
     * {@code VaultedHallSchemeTest}'s harness, and the same reason for forcing rather than rolling.
     */
    private static Map<String, BlockState> build(int width, int depth) {
        RoomScheme scheme = joistedHall();
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, width, depth, HEIGHT, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();

        BasicRoomGenerator forced = new BasicRoomGenerator().withMotifConfig(classic());
        RoomVolumeGenerator.hollow(room, FLOOR_Y, out.getBlocks());
        forced.selectWallGenerator(DungeonMotif.CLASSIC, scheme, width, depth, HEIGHT)
                .build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out.getBlocks());
        forced.selectFloorGenerator(DungeonMotif.CLASSIC, scheme, width, depth, HEIGHT)
                .build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out.getBlocks());
        forced.selectCeilingGenerator(DungeonMotif.CLASSIC, scheme, width, depth, HEIGHT)
                .build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out.getBlocks());

        Map<String, BlockState> world = new LinkedHashMap<>();
        for (BlockPlacement placement : out.getBlocks()) {
            world.put(key(placement.getX(), placement.getY(), placement.getZ()),
                    BlockStateCodec.resolve(placement));
        }
        return world;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static BlockState at(Map<String, BlockState> world, int x, int y, int z) {
        return world.get(key(x, y, z));
    }

    /** The scheme has to survive being authored: an unparseable one would never reach the roll. */
    @Test
    void classicShipsTheScheme() {
        RoomScheme scheme = joistedHall();
        assertEquals(9, scheme.minSize());
        assertEquals(7, scheme.minHeight());
        assertEquals(1, scheme.ceiling().map(CeilingPatternEntry::patterns).map(List::size).orElse(0),
                "one treatment: the beam run");
    }

    /**
     * A beam run crosses the whole interior in the row below the ceiling, and the ceiling plane above
     * it is untouched. The second half is the one worth having: a projecting layer that wrote into
     * the plane instead of below it would open the room to the stone above, which no plan-space
     * assertion can see.
     */
    @Test
    void aBeamRunCrossesTheRoomWithTheCeilingIntactAboveIt() {
        Map<String, BlockState> world = build(SQUARE, SQUARE);
        // Interior is 9x9 at ORIGIN+1..ORIGIN+9; a square room runs along u (X), stride along v (Z),
        // centred at v=4 with spacing 3 -> v = 1, 4, 7, i.e. z = ORIGIN+2, ORIGIN+5, ORIGIN+8.
        int beamZ = ORIGIN + 5;
        for (int x = ORIGIN + 2; x <= ORIGIN + 8; x++) {
            assertEquals(Blocks.SPRUCE_LOG, at(world, x, BEAM_Y, beamZ).getBlock(),
                    "the beam should cross the room at x=" + x);
            assertFalse(at(world, x, CEILING_Y, beamZ).isAir(),
                    "the ceiling plane above the beam at x=" + x);
        }
        assertTrue(at(world, ORIGIN + 5, BEAM_Y, ORIGIN + 4).isAir(),
                "the bay between two beams stays open");
    }

    /** A log laid across the room is laid <em>along</em> the run, not left at its placed default. */
    @Test
    void theBeamsAreLaidAlongTheirRun() {
        Map<String, BlockState> world = build(SQUARE, SQUARE);
        assertEquals(Direction.Axis.X,
                at(world, ORIGIN + 5, BEAM_Y, ORIGIN + 5).getValue(RotatedPillarBlock.AXIS),
                "a square room runs east-west");
    }

    /**
     * The rule that distinguishes a joist from a colonnade: a beam <em>spans</em>, so it crosses the
     * shorter side. In a 13x9 room the interior is 11x7, so the beams must run north-south.
     */
    @Test
    void theBeamsCrossTheShorterSideOfTheRoom() {
        Map<String, BlockState> world = build(13, 9);
        // v (Z) is the short axis at 7 against 11, so each beam is a column of constant x.
        // Stride along u: centred at u=5, spacing 3 -> u = 2, 5, 8 -> x = ORIGIN+3, +6, +9.
        for (int z = ORIGIN + 2; z <= ORIGIN + 6; z++) {
            assertEquals(Blocks.SPRUCE_LOG, at(world, ORIGIN + 6, BEAM_Y, z).getBlock(),
                    "the beam should cross the room at z=" + z);
        }
        assertEquals(Direction.Axis.Z,
                at(world, ORIGIN + 6, BEAM_Y, ORIGIN + 4).getValue(RotatedPillarBlock.AXIS));
    }

    /**
     * The beam row is unbroken from wall to wall. The bracket hangs a row lower, so it must not take
     * a cell out of the run: a corbel in the beam's own row is not supporting the beam, it is
     * interrupting it, which is what the first cut shipped and what Mark rejected on sight.
     */
    @Test
    void theBeamRunIsUnbrokenFromWallToWall() {
        Map<String, BlockState> world = build(SQUARE, SQUARE);
        for (int beamZ : new int[] {ORIGIN + 2, ORIGIN + 5, ORIGIN + 8}) {
            for (int x = ORIGIN + 1; x <= ORIGIN + 9; x++) {
                assertEquals(Blocks.SPRUCE_LOG, at(world, x, BEAM_Y, beamZ).getBlock(),
                        "the run should reach the wall at (" + x + "," + beamZ + ")");
            }
        }
    }

    /**
     * The scheme authors a corbel under each run end, turned {@code inward}.
     *
     * <h2>Why this asserts the authored value and not the placed block</h2>
     * <p>A {@code dungeonblocks:} id does not resolve under a bare {@code Bootstrap} &mdash; there
     * are no Forge registries &mdash; so the bracket degrades to none here and the beams come out
     * running wall to wall. That degrade is itself correct and is pinned in
     * {@code CeilingPatternSelectorTest}; what it means is that <strong>no headless test can see the
     * corbel</strong>, so asserting on the rendered block would only ever restate the bare-bootstrap
     * limitation.</p>
     *
     * <p>So the split is: the authored intent is pinned here, the per-end facing derivation is
     * pinned against vanilla stairs in {@code SurfacePatternProvidersTest}, and whether a corbel
     * <em>looks</em> right hanging under a beam is an in-game walk. {@code inward} is the value the
     * corbel's model asks for &mdash; its post sits on the far face and the arm cantilevers away
     * from it, so the block faces off its wall into the room.</p>
     */
    @Test
    void theRunEndsAreAuthoredAsCorbelsTurnedIntoTheRoom() {
        SurfacePatternEntry entry = joistedHall().ceiling().orElseThrow().patterns().get(0);
        JoistsCeilingPattern beams = assertInstanceOf(JoistsCeilingPattern.class, entry.pattern());
        assertEquals("dungeonblocks:spruce_corbel_block", beams.bracketBlock().orElse(null));
        assertEquals(SurfaceOrient.INWARD, beams.orient(),
                "the corbel faces off its wall, not at it -- see the model, not the name");
        assertEquals(1, entry.projection(),
                "beams at floorY+5 and therefore corbels at floorY+4; see the minHeight assertion");
    }

    /**
     * And with the bracket unresolvable, the row below the beams is left as the room's open air --
     * not a row of AIR blocks written into it, which would be indistinguishable here but is the
     * difference between "no bracket" and "a bracket made of holes" the moment the id does resolve.
     */
    @Test
    void anUnresolvableBracketWritesNothingBelowTheBeams() {
        Map<String, BlockState> world = build(SQUARE, SQUARE);
        for (int beamZ : new int[] {ORIGIN + 2, ORIGIN + 5, ORIGIN + 8}) {
            BlockState underWestEnd = at(world, ORIGIN + 1, BEAM_Y - 1, beamZ);
            assertNotNull(underWestEnd, "the hollow should have cleared this cell at z=" + beamZ);
            assertTrue(underWestEnd.isAir(), "nothing should hang there at z=" + beamZ);
        }
    }

    /**
     * Head height stays clear, and this is what {@code minHeight: 7} is buying. A <em>bracketed</em>
     * joist entry occupies <strong>two</strong> rows, not one -- beams at {@code floorY+5} and
     * corbels at {@code floorY+4} -- so the gate has to clear the door lintel at {@code floorY+3}
     * against the bracket row, not the beam row. At height 6 the corbels would land on the lintel.
     */
    @Test
    void theBracketRowClearsTheDoorRowsAtTheSchemesMinimumHeight() {
        int beamOffset = HEIGHT - 2;
        int bracketOffset = beamOffset - 1;
        assertTrue(bracketOffset > 3,
                "the bracket row at floorY+" + bracketOffset + " must clear the lintel at floorY+3");
    }
}
