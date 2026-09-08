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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backlog #15. Which surface of a piece each of its cells belongs to, decided <strong>once from
 * the whole block list</strong> rather than one block at a time -- the thing that lets a weathering
 * rule say "beams, not posts" or "the ceiling, not the walls" without being keyed on block
 * identity.
 *
 * <h2>Why a per-block gate could not do this</h2>
 * <p>{@link PieceSurface} started life deciding from piece-relative Y alone, because that is all
 * {@code StructureProcessor#processBlock} is given: one block, the piece origin, and a
 * {@code StructurePlaceSettings} whose bounding box is the <em>chunk</em> box. Layer 0 is the
 * floor, so {@code FLOOR} and its complement {@code ABOVE_FLOOR} were expressible and nothing else
 * was. Every remaining question -- is this the ceiling? is this log a joist or a post? -- is a
 * question about a cell's <em>neighbours</em>, and needs the list.</p>
 *
 * <p>{@code finalizeProcessing} is handed that list, which is why {@link SurfaceAgingProcessor}
 * decides there now. See its class doc for why that move costs no ordering.</p>
 *
 * <h2>The three surfaces above the floor, and the two tests that separate them</h2>
 * <p>Everything above the floor is classified by two questions, both answered from the piece's own
 * cells:</p>
 * <ol>
 *   <li><strong>Is it grounded?</strong> -- is there an unbroken run of this piece's blocks from
 *       the course just above the floor plane all the way up to this cell. A wall, a pilaster, a
 *       partition, a free-standing post and a prop all are; a ceiling slab and a joist are not.</li>
 *   <li><strong>Is anything of this piece above it?</strong> -- a joist hangs <em>under</em> the
 *       ceiling slab, so the slab is higher up its column. A ceiling has nothing above it.</li>
 * </ol>
 *
 * <table>
 *   <caption>The partition of {@link PieceSurface#ABOVE_FLOOR}</caption>
 *   <tr><th></th><th>grounded</th><th>hanging</th></tr>
 *   <tr><td><strong>nothing above</strong></td>
 *       <td>{@link PieceSurface#WALL}</td><td>{@link PieceSurface#CEILING}</td></tr>
 *   <tr><td><strong>something above</strong></td>
 *       <td>{@link PieceSurface#WALL}</td><td>{@link PieceSurface#JOIST}</td></tr>
 * </table>
 *
 * <h2>Why grounded, and not simply "the block below is solid"</h2>
 * <p>Both of the obvious one-line tests are wrong, in opposite directions, and each is wrong about
 * something that happens in every dungeon:</p>
 * <ul>
 *   <li><strong>"The highest block in its column is the ceiling."</strong> Wrong for the wall ring.
 *       {@code CeilingSurface.forRoom} insets by one and covers the <strong>interior</strong> only,
 *       while {@code BasicWallGenerator} spans {@code [floorY+1 .. floorY+height-2]}, so the ring's
 *       top course is the top of its own column with nothing above it. This would have called the
 *       top course of every procedural room a ceiling.</li>
 *   <li><strong>"Anything resting on a block below it is a wall."</strong> Wrong for the ceiling
 *       over a beam. A joist sits one course under the slab, so the slab cell directly above it
 *       rests on something and would have been read as a wall -- a row of misclassified ceiling per
 *       joist, in exactly the rooms ({@code joisted_hall}) the joist value exists for.</li>
 * </ul>
 *
 * <p>Reaching the floor separates them because it is what "wall" actually means: a side, standing
 * on the ground. A ceiling over a beam is not grounded, because the beam is not; the wall ring is,
 * course by course, all the way down.</p>
 *
 * <p><strong>The one cell this leaves ambiguous is the slab directly above a free-standing post</strong>,
 * which is grounded through the post and so reads as {@link PieceSurface#WALL}. That is one cell per
 * post where a column meets the ceiling, it is genuinely both, and no test drawn from the block list
 * separates it -- a post's top course and a wall's top course are the same thing seen from
 * different sides.</p>
 *
 * <h2>Layer 1 is grounded whether or not the piece paved the floor</h2>
 * <p>A run starts at relative Y 1 rather than at an occupied floor cell, because
 * {@link PieceSurface#FLOOR_RELATIVE_Y} <em>is</em> the floor plane by decision. Also not
 * hypothetical: a plain procedural floor insets by one, so there is no floor block under the wall
 * ring at all, and requiring one would have classified the bottom course of every procedural wall
 * as hanging -- and with it, since a run is only as grounded as its lowest cell, the whole wall.</p>
 *
 * <h2>Chunk-seam safety</h2>
 * <p>A procedural piece is processed once per chunk it overlaps and must resolve identically each
 * time. This map is safe because it is never built from a clipped list: {@code PieceProcessors}
 * gives every {@code LevelIndependentProcessor} the whole piece unclipped, and on the prefab side
 * vanilla's {@code placeInWorld} hands {@code processBlockInfos} the palette's blocks in full and
 * applies {@code settings.getBoundingBox()} afterwards, in the <em>placement</em> loop. Both halves
 * classify against the same complete piece.</p>
 *
 * <h2>What "occupied" means</h2>
 * <p>A cell is occupied if this piece puts a real block there. Air is not, and neither is
 * {@code structure_void} -- the honest reading in both directions, since a void cell places nothing
 * and leaves whatever the world generated, so the piece has no surface there. Note the deliberate
 * full-height void column in the three {@code 11x11_corner_*} templates: it is not a light shaft,
 * and this map must not invent a ceiling over it.</p>
 *
 * <p>The map is built from the block list <em>before</em> aging, and every processor that adds
 * cosmetic blocks -- {@code dungeons2:decoration} and the marker processors -- runs later in the
 * list, so it describes the architecture and never the growth clinging to it.</p>
 *
 * @author Mark Gottschling on Sep 4, 2026
 */
public final class PieceSurfaceMap {

    private final BlockPos origin;
    private final Set<BlockPos> occupied;
    /** Highest occupied world Y per column, keyed by {@link #columnKey}. */
    private final Map<Long, Integer> columnTop;
    /**
     * Highest world Y still part of the unbroken run that starts one course above the floor plane,
     * per column. Absent for a column whose bottom course is not occupied -- nothing in it is
     * grounded.
     */
    private final Map<Long, Integer> groundedTop;

    private PieceSurfaceMap(BlockPos origin, Set<BlockPos> occupied,
                            Map<Long, Integer> columnTop, Map<Long, Integer> groundedTop) {
        this.origin = origin;
        this.occupied = occupied;
        this.columnTop = columnTop;
        this.groundedTop = groundedTop;
    }

    /**
     * The answer from Y alone, with no piece to consult and nothing allocated.
     *
     * <p>For rules that only ever name {@link PieceSurface#ANY}, {@link PieceSurface#FLOOR} and
     * {@link PieceSurface#ABOVE_FLOOR} &mdash; which is every rule shipped today &mdash; this is
     * not an approximation but the same answer a built map gives: {@code FLOOR} on layer 0,
     * {@code WALL} above it, and {@code ABOVE_FLOOR} is a union {@code WALL} belongs to. Which is
     * what lets {@link SurfaceAgingProcessor} keep such rules in {@code processBlock}, where they
     * have always been.</p>
     */
    public static PieceSurface byHeight(BlockPos origin, BlockPos worldPos) {
        return PieceSurface.relativeY(origin, worldPos) <= PieceSurface.FLOOR_RELATIVE_Y
                ? PieceSurface.FLOOR
                : PieceSurface.WALL;
    }

    /**
     * Builds the map for one piece.
     *
     * @param origin the piece origin -- a processor's {@code piecePos}, whose Y is
     *               {@link PieceSurface#FLOOR_RELATIVE_Y}
     * @param blocks the piece's blocks in <strong>world</strong> space, as
     *               {@code finalizeProcessing} receives them
     */
    public static PieceSurfaceMap of(BlockPos origin,
                                     List<StructureTemplate.StructureBlockInfo> blocks) {
        Set<BlockPos> occupied = new HashSet<>(blocks.size());
        Map<Long, Integer> columnTop = new HashMap<>();
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            if (!fills(info.state())) {
                continue;
            }
            BlockPos pos = info.pos();
            occupied.add(pos);
            columnTop.merge(columnKey(pos), pos.getY(), Math::max);
        }
        return new PieceSurfaceMap(origin, occupied, columnTop,
                groundedTops(origin, occupied, columnTop));
    }

    /**
     * Walks each column upward from the course above the floor plane and records how far the run
     * goes unbroken. One pass over the piece rather than a walk per cell, since every cell in a
     * column shares the answer.
     */
    private static Map<Long, Integer> groundedTops(BlockPos origin, Set<BlockPos> occupied,
                                                   Map<Long, Integer> columnTop) {
        Map<Long, Integer> grounded = new HashMap<>();
        for (Map.Entry<Long, Integer> column : columnTop.entrySet()) {
            long key = column.getKey();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                    columnX(key), origin.getY() + PieceSurface.FLOOR_RELATIVE_Y + 1, columnZ(key));
            int top = column.getValue();
            int reached = Integer.MIN_VALUE;
            while (cursor.getY() <= top && occupied.contains(cursor)) {
                reached = cursor.getY();
                cursor.move(0, 1, 0);
            }
            if (reached != Integer.MIN_VALUE) {
                grounded.put(key, reached);
            }
        }
        return grounded;
    }

    /**
     * The one surface {@code worldPos} belongs to. Exactly one of {@link PieceSurface#FLOOR},
     * {@link PieceSurface#WALL}, {@link PieceSurface#CEILING} and {@link PieceSurface#JOIST} is
     * ever returned; {@link PieceSurface#ANY} and {@link PieceSurface#ABOVE_FLOOR} are unions over
     * those and are answered by {@link PieceSurface#matches(PieceSurface)}.
     *
     * <p>A cell this piece does not fill classifies by Y alone -- floor on layer 0, otherwise
     * {@link PieceSurface#WALL}. Nothing reaches that branch today, since a rule is looked up by
     * the block it is keyed on and no shipped rule is keyed on air.</p>
     */
    public PieceSurface classify(BlockPos worldPos) {
        if (relativeY(worldPos) <= PieceSurface.FLOOR_RELATIVE_Y) {
            return PieceSurface.FLOOR;
        }
        if (!occupied.contains(worldPos) || isGrounded(worldPos)) {
            return PieceSurface.WALL;
        }
        return hasSomethingAbove(worldPos) ? PieceSurface.JOIST : PieceSurface.CEILING;
    }

    /** Whether this piece puts a real block at {@code worldPos}. */
    public boolean isOccupied(BlockPos worldPos) {
        return occupied.contains(worldPos);
    }

    /** {@code worldPos}'s height above the piece's floor plane. */
    public int relativeY(BlockPos worldPos) {
        return PieceSurface.relativeY(origin, worldPos);
    }

    /**
     * Whether an unbroken run of this piece's blocks reaches {@code worldPos} from the course above
     * the floor plane. See the class doc for why this and not "the block below is solid".
     */
    private boolean isGrounded(BlockPos worldPos) {
        Integer reached = groundedTop.get(columnKey(worldPos));
        return reached != null && worldPos.getY() <= reached;
    }

    /** Whether this piece has a block higher up {@code worldPos}'s column. */
    private boolean hasSomethingAbove(BlockPos worldPos) {
        Integer top = columnTop.get(columnKey(worldPos));
        return top != null && top > worldPos.getY();
    }

    /**
     * Whether a state occupies its cell. {@code structure_void} is not air and so has to be named:
     * it places nothing and leaves the terrain, which is exactly "no surface here".
     */
    private static boolean fills(BlockState state) {
        return !state.isAir() && !state.is(Blocks.STRUCTURE_VOID);
    }

    /** Packs a cell's x and z into one key; the column is what both neighbour tests walk. */
    private static long columnKey(BlockPos pos) {
        return ((long) pos.getX() << 32) | (pos.getZ() & 0xFFFFFFFFL);
    }

    private static int columnX(long key) {
        return (int) (key >> 32);
    }

    private static int columnZ(long key) {
        return (int) key;
    }
}
