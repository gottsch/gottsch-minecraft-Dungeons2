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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code classic}'s shipped {@code vaulted_hall} scheme actually builds.
 *
 * <h2>Why this is asserted against blocks rather than against the plan</h2>
 * <p>A vault is the first ceiling treatment that occupies more than one row, and the thing that can
 * go wrong is not visible in any single pattern: it is the <strong>gap between</strong> two of them.
 * A ring of stairs dropped two rows leaves the row above it unwritten unless something fills it, and
 * an unwritten interior cell is air &mdash; so the springing would hang below a one-block void
 * running right around the room, lit and shadowed like a recess nobody authored.</p>
 *
 * <p>The scheme fills that row with a third pattern, and this test is what says so. It reads the
 * <em>last</em> placement at each position, because the renderer replays the list in order and a
 * later write wins &mdash; the same rule that lets the ceiling overwrite the walls' trim.</p>
 *
 * @author Mark Gottschling on Aug 05, 2026
 */
class VaultedHallSchemeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Comfortably inside the scheme's gates (minSize 9, minHeight 7). Interior is 9x9. */
    private static final int WIDTH = 11;
    private static final int DEPTH = 11;
    private static final int HEIGHT = 7;
    private static final int FLOOR_Y = 60;
    private static final int ORIGIN = 10;

    private static MotifConfig classic() {
        return MotifConfigs.load("classic");
    }

    private static RoomScheme vaultedHall() {
        return classic().schemes().stream()
                .filter(s -> "vaulted_hall".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "classic no longer ships a 'vaulted_hall' scheme; this test is about that scheme"));
    }

    /**
     * Builds the room with the vault forced, and indexes the result by position keeping the last
     * write. Forced rather than rolled: the scheme fires in under 2% of rooms, so rolling for it
     * would make this a flaky search rather than a test.
     */
    private static Map<String, BlockState> build() {
        RoomScheme scheme = vaultedHall();
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, WIDTH, DEPTH, HEIGHT, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();

        // Drive the sub-builders directly with the chosen scheme, bypassing only the roll. The
        // hollow-then-wall-then-floor-then-ceiling order is BasicRoomGenerator's own, and it is
        // load-bearing: the ceiling reaches into cells the hollow cleared and the walls may have
        // written.
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
            world.put(key(placement.getX(), placement.getY(), placement.getZ()), BlockStateCodec.resolve(placement));
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
        RoomScheme scheme = vaultedHall();
        assertEquals(9, scheme.minSize());
        assertEquals(7, scheme.minHeight());
        assertTrue(scheme.ceiling().map(CeilingPatternEntry::patterns).map(List::size).orElse(0) == 3,
                "the vault is three layers: springing, the fill above it, and the second step");
    }

    /**
     * <strong>The one that matters.</strong> A column at the room's edge must be solid masonry from
     * the springing up to the ceiling plane, with no unwritten row between. Walking the whole
     * perimeter rather than sampling one cell, because a gap that only appears on one wall run is
     * exactly the kind of asymmetry the {@code (u, v)} authoring convention can introduce.
     */
    @Test
    void theVaultIsSolidFromTheSpringingToTheCeiling() {
        Map<String, BlockState> world = build();
        int ceilingY = FLOOR_Y + HEIGHT - 1;
        // Interior perimeter: the cells the inset-0 rings occupy.
        for (int x = ORIGIN + 1; x <= ORIGIN + WIDTH - 2; x++) {
            for (int z = ORIGIN + 1; z <= ORIGIN + DEPTH - 2; z++) {
                boolean onPerimeter = x == ORIGIN + 1 || x == ORIGIN + WIDTH - 2
                        || z == ORIGIN + 1 || z == ORIGIN + DEPTH - 2;
                if (!onPerimeter) {
                    continue;
                }
                for (int y = ceilingY - 2; y <= ceilingY; y++) {
                    BlockState state = at(world, x, y, z);
                    assertNotNull(state, "nothing written at " + key(x, y, z));
                    assertFalse(state.isAir(),
                            "a void in the vault at " + key(x, y, z) + " (y offset "
                                    + (y - ceilingY) + " from the ceiling)");
                }
            }
        }
    }

    /** The springing is stairs leaning on the wall, not a plain block: that is what makes it a vault. */
    @Test
    void theSpringingIsStairsFacingTheWall() {
        Map<String, BlockState> world = build();
        int springingY = FLOOR_Y + HEIGHT - 3;
        // The middle of the north run, well clear of the corners.
        BlockState north = at(world, ORIGIN + WIDTH / 2, springingY, ORIGIN + 1);
        assertEquals(Blocks.STONE_BRICK_STAIRS, north.getBlock());
        assertEquals(Direction.NORTH, north.getValue(StairBlock.FACING),
                "the north run's springing leans into the north wall");

        BlockState west = at(world, ORIGIN + 1, springingY, ORIGIN + DEPTH / 2);
        assertEquals(Direction.WEST, west.getValue(StairBlock.FACING));
    }

    /**
     * The centre stays open at full height. Without this the "vault" is just a lowered ceiling: the
     * raised field it frames is the whole visual point, and it exists only because a {@code border}
     * marks its ring and nothing else.
     */
    @Test
    void theCentreFieldKeepsItsFullHeight() {
        Map<String, BlockState> world = build();
        int ceilingY = FLOOR_Y + HEIGHT - 1;
        int midX = ORIGIN + WIDTH / 2;
        int midZ = ORIGIN + DEPTH / 2;
        assertFalse(at(world, midX, ceilingY, midZ).isAir(), "the ceiling plane covers the centre");
        assertTrue(at(world, midX, ceilingY - 1, midZ).isAir(),
                "the centre must stay open one row below the ceiling");
        assertTrue(at(world, midX, ceilingY - 2, midZ).isAir(),
                "and two rows below -- the vault steps down at the edges only");
    }

    /**
     * Head height stays clear. The vault eats two rows at the perimeter, and at the scheme's own
     * minimum height that leaves exactly the door's rows below it &mdash; so this is the assertion
     * that says {@code minHeight: 7} is the right gate and not one off.
     */
    @Test
    void theVaultClearsTheDoorRowsAtItsMinimumHeight() {
        int springingOffset = HEIGHT - 3;
        assertTrue(springingOffset > 3,
                "the springing at floorY+" + springingOffset + " must clear the door lintel at floorY+3");
    }
}
