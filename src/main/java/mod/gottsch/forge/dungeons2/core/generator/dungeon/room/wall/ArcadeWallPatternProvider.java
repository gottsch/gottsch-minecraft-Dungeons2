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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Objects;

/**
 * A <strong>blind arcade</strong>: a run of arch outlines drawn flat against the wall. Backlog #78.
 *
 * <p>The detail that makes a hall read as <em>built by someone</em> rather than merely aged, which
 * courses alone never quite manage: a stringcourse says the wall was finished, an arcade says it was
 * designed.</p>
 *
 * <h2>The shape, and why it is this shape</h2>
 * <p>Each arch is an outline, {@code width} cells across and {@code height} rows tall:</p>
 * <pre>
 *   v = height-1   .  C  C  C  .      crown, inset one cell from each leg
 *   v = height-2   S  .  .  .  S      the two shoulders (stairs)
 *   v = height-3   L  .  .  .  L      legs, down to the floor
 *   v = 0          L  .  .  .  L
 * </pre>
 * <p>Blind means the opening is not cut &mdash; the cells inside the outline are left null, so
 * whatever this composes over shows through them. That is what makes it flat decoration rather than
 * a niche, and it is also why it needs no depth: see {@link #plan}.</p>
 *
 * <p><strong>The shoulders are the whole trick.</strong> An arch drawn only in full cubes is a
 * doorframe; what turns it into an arch is one cell at each upper corner whose mass is on the outer
 * side and whose cut faces down into the opening. That is a stair with {@code half=top} facing
 * <em>along</em> the wall, away from the arch's centre &mdash; the full-height half sits on a
 * stair's own {@code facing} side, the same rule {@code CoursesWallPatternProvider#oriented}
 * documents and the single most error-prone thing in this package.</p>
 *
 * <p><strong>The {@code dungeonblocks} mouldings are modelled facing-inverted relative to vanilla
 * stairs</strong>, so an arcade authored in one of those will come out mirrored and wants its
 * shoulders swapped at the authoring layer. Same caveat {@code CourseOrient} carries; nothing here
 * can detect it, because a block id is a string until the world exists.</p>
 *
 * <h2>Height is the binding constraint, and a short wall gets nothing</h2>
 * <p>A wall's {@code vSize} is {@code roomHeight - 2} &mdash; 3 to 8 rows, most often at the low
 * end. Rather than clip, which would leave a row of legs with no heads and read as a fence nobody
 * authored, a wall too short or too narrow for one whole arch draws <strong>nothing</strong>. Same
 * rule, and the same reasoning, as {@code DiamondWallPatternProvider}. A scheme wanting a guarantee
 * states {@code min_height}.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public class ArcadeWallPatternProvider implements ISurfacePatternProvider {

    /** Five: two legs and a three-cell opening, the narrowest that reads as an arch and not a slot. */
    public static final int DEFAULT_WIDTH = 5;

    /** Five: three rows of leg under a shoulder and a crown. Fits a room of height 7. */
    public static final int DEFAULT_HEIGHT = 5;

    /** One cell of plain wall between arches: a rhythm, not a colonnade of touching piers. */
    public static final int DEFAULT_SPACING = 1;

    /** The narrowest arch that has an inside: two legs and one cell of opening. */
    public static final int MIN_WIDTH = 3;

    /** A leg row, a shoulder row and a crown row. Anything less has no shoulder to turn on. */
    public static final int MIN_HEIGHT = 3;

    private final BlockState block;
    private final BlockState stairBlock;
    private final BlockState impostBlock;
    private final int width;
    private final int height;
    private final int spacing;

    /**
     * @param block       the legs and the crown
     * @param stairBlock  the two shoulders, or null to turn the corners in {@code block} &mdash; a
     *                    squared arch, which is a real look and the honest degrade when a motif has
     *                    no matching stair
     * @param impostBlock the springing course on each leg, or null for none
     */
    public ArcadeWallPatternProvider(BlockState block, BlockState stairBlock,
                                     BlockState impostBlock, int width, int height, int spacing) {
        this.block = Objects.requireNonNull(block, "block");
        this.stairBlock = stairBlock;
        this.impostBlock = impostBlock;
        this.width = Math.max(MIN_WIDTH, width);
        this.height = Math.max(MIN_HEIGHT, height);
        this.spacing = Math.max(0, spacing);
    }

    /**
     * The world direction {@code u} advances in on a run with this facing.
     *
     * <p>{@code WallSurface} builds its four runs so that {@code u} always advances along +X or +Z,
     * never backwards: the two Z-facing runs step in X, the two X-facing runs step in Z. So the
     * answer falls out of the facing's axis, which is the only thing a provider is handed. Getting
     * this wrong mirrors every arch on two of the four walls, which is exactly the class of fault
     * that looks deliberate in a screenshot.</p>
     */
    static Direction uDirection(Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        // Whole arches only -- see the class note on why a clipped one is not drawn short.
        if (uSize < width || vSize < height) {
            return plan;
        }

        int pitch = width + spacing;
        int count = 1 + (uSize - width) / pitch;
        int used = width + (count - 1) * pitch;
        // Centre the run, so the leftover is split between the two ends rather than trailing off
        // one of them. Same treatment the colonnade and the diamond run both get.
        int uStart = (uSize - used) / 2;

        Direction along = uDirection(facing);
        for (int i = 0; i < count; i++) {
            arch(plan, uStart + i * pitch, along);
        }
        return plan;
    }

    /** One arch, with its left leg at {@code u0}. */
    private void arch(SurfacePlan plan, int u0, Direction along) {
        int uRight = u0 + width - 1;
        int vShoulder = height - 2;
        int vCrown = height - 1;

        // Legs, floor to just under the shoulder.
        for (int v = 0; v < vShoulder; v++) {
            plan.set(u0, v, block);
            plan.set(uRight, v, block);
        }

        // The springing course, if the author asked for one: the top leg cell on each side, which
        // is where a real impost sits -- the shelf the arch is launched from.
        if (impostBlock != null && vShoulder >= 1) {
            plan.set(u0, vShoulder - 1, impostBlock);
            plan.set(uRight, vShoulder - 1, impostBlock);
        }

        // The shoulders. Mass on the OUTER side, cut facing down into the opening: a stair's
        // full-height half sits on its own facing side, so the left shoulder faces -u and the right
        // faces +u, both upside down.
        plan.set(u0, vShoulder, shoulder(along.getOpposite()));
        plan.set(uRight, vShoulder, shoulder(along));

        // The crown, spanning between the legs.
        for (int u = u0 + 1; u < uRight; u++) {
            plan.set(u, vCrown, block);
        }
    }

    /**
     * One shoulder. Falls back to {@code block} when no stair was authored, which squares the arch
     * off rather than leaving a hole in its outline &mdash; a partial outline reads as damage, and
     * the weathering pass is what is supposed to say that.
     */
    private BlockState shoulder(Direction facing) {
        if (stairBlock == null) {
            return block;
        }
        return BlockStateCodec.withProperties(stairBlock,
                Map.of("facing", facing.getSerializedName(), "half", "top"));
    }
}
