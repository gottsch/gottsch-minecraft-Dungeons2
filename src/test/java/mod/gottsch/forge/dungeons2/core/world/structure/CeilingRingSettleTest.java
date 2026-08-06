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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CeilingSurface;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.dungeons2.diagnostic.FakeWorldGenLevel;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether vanilla's own corner derivation mitres an oriented ceiling ring, or whether this side has
 * to author {@code shape} itself the way the corridor arch does.
 *
 * <h2>Why this had to be measured rather than reasoned about</h2>
 * <p>The two precedents point opposite ways and both are documented as correct. A corridor arch
 * haunch <strong>cannot</strong> be settled by vanilla &mdash; {@code StairBlock.getStairsShape}
 * looks for a stair at {@code pos.relative(facing)} to decide OUTER, a haunch faces into solid wall,
 * so OUTER can never fire and INNER fires off a perpendicular haunch across the passage. That is why
 * {@code DungeonCorridorPiece} opts out of {@link DungeonPiece#settlesJoinShapes} entirely. A room's
 * projecting cornice ring, on the other hand, is settled by vanilla and comes out right, which is
 * what {@code settlesJoinShapes}' javadoc means by "a rectangle with four runs".</p>
 *
 * <p>An oriented ceiling ring resembles both: it is a rectangle with four runs, but at {@code inset}
 * greater than zero it hangs in open air with no wall behind it, which is the condition that breaks
 * the corridor case. Reasoning gave a confident answer either way, so the backlog entry for this
 * work said to check before writing any shape derivation. This is that check, and the answer is that
 * <strong>vanilla gets it right and no derivation is needed</strong> &mdash; the corner's neighbour
 * along the ring lies in its own {@code facing.getOpposite()} direction, which is exactly where
 * vanilla's INNER branch looks.</p>
 *
 * <p>If this test ever fails, the fix is not to patch the ring: it is to author shapes in
 * {@code BorderSurfacePatternProvider} and have the room piece stop settling them, mirroring the
 * corridor. That is a much larger change, which is why the cheap check earns its keep.</p>
 *
 * @author Mark Gottschling on Aug 05, 2026
 */
class CeilingRingSettleTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The Y the ring hangs at. Arbitrary; nothing here depends on it. */
    private static final int RING_Y = 40;

    /**
     * Writes an oriented ring into a fake level and settles it exactly as {@code DungeonPiece} does,
     * returning the settled states in {@code (u, v)} order.
     *
     * <p>Everything not in the ring is left unwritten, which {@code FakeWorldGenLevel} reads back as
     * air &mdash; the honest model of a ring hanging below a ceiling with room air all around it.</p>
     */
    private static SurfacePlan settledRing(int uSize, int vSize, int inset) {
        BlockState stairs = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
        SurfacePlan planned = new BorderSurfacePatternProvider(inset, stairs, stairs,
                SurfaceOrient.OUTWARD, CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION)
                .plan(uSize, vSize, Direction.DOWN);

        FakeWorldGenLevel fake = FakeWorldGenLevel.create();
        List<BlockPos> written = new ArrayList<>();
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                BlockState state = planned.get(u, v);
                if (state != null) {
                    BlockPos pos = new BlockPos(u, RING_Y, v);
                    fake.level().setBlock(pos, state, Block.UPDATE_CLIENTS);
                    written.add(pos);
                }
            }
        }
        // The same second pass DungeonPiece#settleJoinShapes runs: derive every cell only after the
        // whole ring exists, because a mitre needs both of its arms placed.
        for (BlockPos pos : written) {
            BlockState settled = Block.updateFromNeighbourShapes(fake.level().getBlockState(pos),
                    fake.level(), pos);
            fake.level().setBlock(pos, settled, Block.UPDATE_CLIENTS);
        }

        SurfacePlan out = SurfacePlan.of(uSize, vSize);
        for (BlockPos pos : written) {
            out.set(pos.getX(), pos.getZ(), fake.blockAt(pos));
        }
        return out;
    }

    private static StairsShape shapeAt(SurfacePlan plan, int u, int v) {
        return plan.get(u, v).getValue(StairBlock.SHAPE);
    }

    /**
     * The four corners mitre. A room's walls are concave seen from inside, so a ring hugging them
     * turns <em>inner</em> corners, not outer ones &mdash; the block's solid mass has to cover both
     * of the quarters its two arms cover.
     */
    @Test
    void vanillaMitresTheCornersOfAFlushRing() {
        SurfacePlan settled = settledRing(7, 7, 0);
        for (int[] corner : new int[][]{{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
            StairsShape shape = shapeAt(settled, corner[0], corner[1]);
            assertTrue(shape == StairsShape.INNER_LEFT || shape == StairsShape.INNER_RIGHT,
                    "corner (" + corner[0] + "," + corner[1] + ") should mitre, was " + shape);
        }
    }

    /**
     * The condition that breaks the corridor case is absent even at {@code inset > 0}, where the ring
     * hangs in open air with no wall behind it. This is the case reasoning got wrong, and the reason
     * the check exists.
     */
    @Test
    void vanillaAlsoMitresARingHangingInOpenAir() {
        SurfacePlan settled = settledRing(9, 9, 2);
        for (int[] corner : new int[][]{{2, 2}, {6, 2}, {2, 6}, {6, 6}}) {
            StairsShape shape = shapeAt(settled, corner[0], corner[1]);
            assertTrue(shape == StairsShape.INNER_LEFT || shape == StairsShape.INNER_RIGHT,
                    "corner (" + corner[0] + "," + corner[1] + ") should mitre, was " + shape);
        }
    }

    /**
     * The runs between the corners stay straight. Paired with the corner assertions on purpose: a
     * derivation that mitred everything would satisfy those alone, and a ring of all-inner stairs is
     * a solid band, not a springing. See the Aug 05 handoff on assertions that pass either way.
     */
    @Test
    void theRunsBetweenTheCornersStayStraight() {
        SurfacePlan settled = settledRing(7, 7, 0);
        for (int u = 1; u <= 5; u++) {
            assertEquals(StairsShape.STRAIGHT, shapeAt(settled, u, 0), "north run at u=" + u);
            assertEquals(StairsShape.STRAIGHT, shapeAt(settled, u, 6), "south run at u=" + u);
        }
        for (int v = 1; v <= 5; v++) {
            assertEquals(StairsShape.STRAIGHT, shapeAt(settled, 0, v), "west run at v=" + v);
            assertEquals(StairsShape.STRAIGHT, shapeAt(settled, 6, v), "east run at v=" + v);
        }
    }

    /**
     * Settling must not move a block or change which way it faces &mdash; only its corner shape.
     * A facing that vanilla felt free to rewrite would silently undo the orientation this whole
     * feature exists to set.
     */
    @Test
    void settlingChangesOnlyTheShapeNeverTheFacing() {
        SurfacePlan planned = new BorderSurfacePatternProvider(0,
                Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
                Blocks.STONE_BRICK_STAIRS.defaultBlockState(),
                SurfaceOrient.OUTWARD, CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION)
                .plan(7, 7, Direction.DOWN);
        SurfacePlan settled = settledRing(7, 7, 0);
        for (int u = 0; u < 7; u++) {
            for (int v = 0; v < 7; v++) {
                if (planned.get(u, v) != null) {
                    assertEquals(planned.get(u, v).getValue(StairBlock.FACING),
                            settled.get(u, v).getValue(StairBlock.FACING),
                            "cell (" + u + "," + v + ")");
                }
            }
        }
    }
}
