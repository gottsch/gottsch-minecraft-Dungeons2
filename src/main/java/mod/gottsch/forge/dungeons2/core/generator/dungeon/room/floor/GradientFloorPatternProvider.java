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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * Two materials mixed with a bias that runs from the room's edge to its centre &mdash; one
 * dominating the cells against the walls and giving way to the other as the floor opens out.
 *
 * <h2>The floor's answer to {@code GradientWallPatternProvider}</h2>
 * <p>Same idea, same arithmetic, and deliberately the same field names one axis over: a probability
 * that falls with distance, so the boundary between the two materials lands somewhere different in
 * every row and there is no line anywhere you could point at. What the wall gets from height, the
 * floor gets from distance to the nearest wall &mdash; silt and litter gather at the edges of a room
 * and the middle is walked clean, which is the one direction a floor gradient can run and still read
 * as wear rather than as decoration.</p>
 *
 * <h2>Why not a straight ramp along one axis</h2>
 * <p>A wall has an up. A floor does not, so the wall type's single-axis ramp has no obvious
 * counterpart: a floor shading from its west side to its east side reads as arbitrary unless
 * something else in the room justifies the direction, and nothing in a procedural room does. The
 * distance field is the version that always has a reason, and it has the useful property of being
 * symmetric &mdash; it composes with {@code border} instead of fighting it, because both are
 * organised around the same rectangle.</p>
 *
 * <h2>Distance, and what "the edge" means</h2>
 * <p>Distance is Chebyshev &mdash; {@code min(x, z, width-1-x, depth-1-z)} &mdash; measured on the
 * room's FULL footprint, wall ring included, the same grid every other floor provider fills. Cell
 * 0 is therefore under the wall and is overwritten by it; the first cell a player ever sees is at
 * distance 1. That is worth knowing when authoring {@link #holdCells}: a hold of 1 is spent entirely
 * on cells nobody sees, and 2 is the smallest hold that shows as a visible band at the wall.</p>
 *
 * <h2>Randomness</h2>
 * <p>Drawn from the room's own {@link RandomSource}, like the wall version and like
 * {@link RandomSpeckleFloorPatternProvider}. So it is NOT a pure function of {@code (x, z)}: a floor
 * computed purely from its coordinates would come out identically speckled in every room in the
 * dungeon.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public class GradientFloorPatternProvider implements IDungeonFloorGenerator {

    private final Block edgeBlock;
    private final Block centreBlock;
    private final double edgeProbability;
    private final double centreProbability;
    private final int holdCells;

    public GradientFloorPatternProvider(Block edgeBlock, Block centreBlock, double edgeProbability,
                                        double centreProbability, int holdCells) {
        this.edgeBlock = Objects.requireNonNull(edgeBlock, "edgeBlock");
        this.centreBlock = Objects.requireNonNull(centreBlock, "centreBlock");
        this.edgeProbability = edgeProbability;
        this.centreProbability = centreProbability;
        this.holdCells = Math.max(0, holdCells);
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                      List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, random, out);
    }

    /**
     * Builds the pattern for a floor of the given size at the given origin, independent of
     * {@link RoomData} &mdash; the same escape hatch {@link RandomSpeckleFloorPatternProvider}
     * offers, for use outside the room pipeline.
     */
    public void build(int width, int depth, int originX, int originZ, int floorY, RandomSource random,
                      List<BlockPlacement> out) {
        BlockState edge = edgeBlock.defaultBlockState();
        BlockState centre = centreBlock.defaultBlockState();
        int maxDistance = maxDistance(width, depth);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                double probability = probabilityAt(distance(x, z, width, depth), maxDistance);
                out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z,
                        random.nextDouble() < probability ? edge : centre));
            }
        }
    }

    /** Cells from the nearest edge of the footprint; 0 on the outermost ring. */
    static int distance(int x, int z, int width, int depth) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(z, depth - 1 - z));
    }

    /**
     * The largest distance any cell of this footprint reaches &mdash; the centre of the narrower
     * axis. This is what the ramp is scaled against rather than the room's size, so a long thin room
     * still completes its gradient: scaling against the long axis would leave a 5x15 room's ramp
     * only a third finished at the centreline it actually has.
     */
    static int maxDistance(int width, int depth) {
        return Math.min((width - 1) / 2, (depth - 1) / 2);
    }

    /**
     * The chance of the EDGE material at {@code distance} cells from the wall.
     *
     * <p>Package-visible and pure so the ramp can be tested directly, exactly as the wall version's
     * {@code probabilityAt} is: a room is only a handful of cells from edge to centre, so an
     * off-by-one here is a large fraction of the gradient and is very hard to see in game against
     * the scatter it produces.</p>
     */
    double probabilityAt(int distance, int maxDistance) {
        if (distance < holdCells) {
            return edgeProbability;
        }
        // Spans from the first cell after the hold to the centre inclusive, so the centre lands
        // exactly on centreProbability rather than one step short of it -- the same convention the
        // wall's ramp uses for its top row.
        int span = maxDistance - holdCells;
        if (span <= 0) {
            // The hold ate the whole room (or the room is too small to have a middle at all). Every
            // cell keeps the full edge bias, which is what "hold_cells past the centre" was asking
            // for; a room too small for the gradient should not silently invert it.
            return edgeProbability;
        }
        double t = (double) (distance - holdCells) / span;
        return edgeProbability + t * (centreProbability - edgeProbability);
    }
}
