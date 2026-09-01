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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code deep_vaulted_hall} &mdash; the three-step vault backlog #28b's raised projection cap made
 * authorable. The sibling of {@link VaultedHallSchemeTest}, and it exists for one reason above all
 * the others: <strong>three steps need three fill layers, and a missing one is invisible in the
 * JSON.</strong>
 *
 * <h2>The trap this is here for</h2>
 * <p>A surface plan is sparse: it writes only what it marks, and the space between two layers
 * belongs to nobody. So every row between a step and the ceiling has to be filled explicitly or the
 * step hangs below a void. The two-step scheme needed one such layer and
 * {@code VaultedHallSchemeTest} was written when the first authoring of it forgot that layer. This
 * scheme needs <em>three</em>, at (inset 0, projections 1 and 2) and (inset 1, projection 1), and
 * the JSON reads as correct without any of them.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
class DeepVaultedHallSchemeTest {

    /** 11 is the scheme's own minSize, and the footprint the ring arithmetic was derived for. */
    private static final int WIDTH = 11;
    private static final int DEPTH = 11;

    /** The scheme's own minHeight: the tightest case, which is the one worth testing. */
    private static final int HEIGHT = 8;

    private static final int FLOOR_Y = 60;
    private static final int ORIGIN = 10;

    /** Three corbelled steps. Everything below is derived from this rather than restating it. */
    private static final int STEPS = 3;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MotifConfig classic() {
        return MotifConfigs.load("classic");
    }

    private static RoomScheme deepVault() {
        return classic().schemes().stream()
                .filter(s -> "deep_vaulted_hall".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "classic no longer ships a 'deep_vaulted_hall' scheme"));
    }

    /** Forced rather than rolled, for the reason the two-step test gives: it is far too rare to search for. */
    private static Map<String, BlockState> build() {
        RoomScheme scheme = deepVault();
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, WIDTH, DEPTH, HEIGHT, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();

        BasicRoomGenerator forced = new BasicRoomGenerator().withMotifConfig(classic());
        RoomVolumeGenerator.hollow(room, FLOOR_Y, out.getBlocks());
        forced.selectWallGenerator(DungeonMotif.CLASSIC, scheme, WIDTH, DEPTH, HEIGHT)
                .build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out.getBlocks());
        forced.selectFloorGenerator(DungeonMotif.CLASSIC, scheme, WIDTH, DEPTH, HEIGHT)
                .build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out.getBlocks());
        forced.selectCeilingGenerator(DungeonMotif.CLASSIC, scheme, WIDTH, DEPTH, HEIGHT)
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

    /** An unparseable scheme would never reach the roll, so this fails first and most cheaply. */
    @Test
    void classicShipsTheScheme() {
        RoomScheme scheme = deepVault();
        assertTrue(scheme.ceiling().isPresent(), "the scheme is its ceiling");
        assertEquals(6, scheme.ceiling().orElseThrow().patterns().size(),
                "three steps means three stair rings and three fill layers; a count other than 6"
                        + " means a layer was added or lost without the arithmetic being redone");
    }

    /**
     * <strong>The one that matters.</strong> Every cell of every ring, from the deepest step up to
     * the ceiling plane, must be solid. A missing fill layer shows up here and nowhere else.
     *
     * <p>Walks each ring at its own depth rather than the whole block: ring {@code k} carries its
     * stair at projection {@code STEPS - k} and must be solid from there to the ceiling, while the
     * cells further in must NOT be filled at that depth &mdash; which is what
     * {@link #theCentreFieldKeepsItsFullHeight} covers.</p>
     */
    @Test
    void everyRingIsSolidFromItsStepToTheCeiling() {
        Map<String, BlockState> world = build();
        int ceilingY = FLOOR_Y + HEIGHT - 1;

        for (int ring = 0; ring < STEPS; ring++) {
            int lo = ORIGIN + 1 + ring;
            int hiX = ORIGIN + WIDTH - 2 - ring;
            int hiZ = ORIGIN + DEPTH - 2 - ring;
            int deepest = ceilingY - (STEPS - ring);

            for (int x = lo; x <= hiX; x++) {
                for (int z = lo; z <= hiZ; z++) {
                    if (x != lo && x != hiX && z != lo && z != hiZ) {
                        continue;
                    }
                    for (int y = deepest; y <= ceilingY; y++) {
                        BlockState state = at(world, x, y, z);
                        assertNotNull(state, "ring " + ring + ": nothing written at " + key(x, y, z));
                        assertFalse(state.isAir(), "ring " + ring + ": a void at " + key(x, y, z)
                                + " (" + (ceilingY - y) + " below the ceiling) -- a fill layer is missing");
                    }
                }
            }
        }
    }

    /** Three steps, not two: the deepest ring hangs one row lower than the shipped vault's does. */
    @Test
    void theVaultIsThreeStepsDeep() {
        Map<String, BlockState> world = build();
        int ceilingY = FLOOR_Y + HEIGHT - 1;
        int midX = ORIGIN + WIDTH / 2;

        assertEquals(Blocks.STONE_BRICK_STAIRS,
                at(world, midX, ceilingY - 3, ORIGIN + 1).getBlock(),
                "the outermost ring's step must sit three rows below the ceiling");
        assertTrue(at(world, midX, ceilingY - 4, ORIGIN + 1) == null
                        || at(world, midX, ceilingY - 4, ORIGIN + 1).isAir(),
                "and nothing below it -- a fourth step would eat the headroom min_height 8 budgets");
    }

    /** Each ring's step leans outward, on all four runs, exactly as the two-step vault's does. */
    @Test
    void everyStepLeansOnTheWallItFaces() {
        Map<String, BlockState> world = build();
        int ceilingY = FLOOR_Y + HEIGHT - 1;

        for (int ring = 0; ring < STEPS; ring++) {
            int y = ceilingY - (STEPS - ring);
            BlockState north = at(world, ORIGIN + WIDTH / 2, y, ORIGIN + 1 + ring);
            assertEquals(Blocks.STONE_BRICK_STAIRS, north.getBlock(), "ring " + ring);
            assertEquals(Direction.NORTH, north.getValue(StairBlock.FACING),
                    "ring " + ring + "'s north run must lean north");

            BlockState west = at(world, ORIGIN + 1 + ring, y, ORIGIN + DEPTH / 2);
            assertEquals(Direction.WEST, west.getValue(StairBlock.FACING),
                    "ring " + ring + "'s west run must lean west");
        }
    }

    /**
     * The field the vault frames. Three rings eat 6 of the 9 interior cells, leaving 3x3 &mdash;
     * which is the whole reason minSize is 11 and not 9. At 9 this would be a 1x1 apex and the
     * scheme would read as a stepped pyramid.
     */
    @Test
    void theCentreFieldKeepsItsFullHeight() {
        Map<String, BlockState> world = build();
        int ceilingY = FLOOR_Y + HEIGHT - 1;
        int midX = ORIGIN + WIDTH / 2;
        int midZ = ORIGIN + DEPTH / 2;

        assertFalse(at(world, midX, ceilingY, midZ).isAir(), "the ceiling plane covers the centre");
        for (int drop = 1; drop <= STEPS; drop++) {
            BlockState state = at(world, midX, ceilingY - drop, midZ);
            assertTrue(state == null || state.isAir(),
                    "the centre field must stay open " + drop + " below the ceiling");
        }
    }

    /**
     * Head height at the perimeter. The vault eats three rows, so at the scheme's own minHeight the
     * clear wall below it is {@code HEIGHT - 2 - STEPS} = 3 rows &mdash; exactly what the two-step
     * vault leaves at ITS minimum. This is the assertion that says minHeight 8 is the right gate:
     * at 7 the same vault would leave 2, which is below the shipped clearance.
     */
    @Test
    void theGateLeavesTheSameClearanceTheShippedVaultDoes() {
        int clear = HEIGHT - 2 - STEPS;
        assertEquals(3, clear,
                "min_height " + HEIGHT + " with " + STEPS + " steps must leave 3 clear rows");

        Map<String, BlockState> world = build();
        int ceilingY = FLOOR_Y + HEIGHT - 1;
        for (int y = FLOOR_Y + 1; y <= ceilingY - STEPS - 1; y++) {
            BlockState state = at(world, ORIGIN + WIDTH / 2, y, ORIGIN + 1);
            assertTrue(state == null || state.isAir(),
                    "the wall below the vault must stay clear at " + key(ORIGIN + WIDTH / 2, y, ORIGIN + 1));
        }
    }
}
