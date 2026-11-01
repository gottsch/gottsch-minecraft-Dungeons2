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

import mod.gottsch.forge.dungeons2.core.config.floor.WornPathFloorPattern.PathRouting;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The track people wear into a floor: a band of {@code pathBlock} joining the room's doors, soft at
 * its edges and solid down the middle.
 *
 * <h2>The only pattern in this package that reads the ROOM rather than its dimensions</h2>
 * <p>Every other floor provider is a pure function of {@code (width, depth)} and could be drawn
 * knowing nothing else. This one needs to know where the doors are, and it can: the maze copies each
 * room's opened doorways into {@link RoomData} at plan time and {@code PieceNbt} round-trips them
 * through the save, so the piece rendering the floor has them in hand. They are in the same
 * floor-local space as the room's own origin, which is what makes the conversion a subtraction.</p>
 *
 * <p>That also makes it the one provider whose output cannot be reproduced from a size alone, so the
 * {@code build(width, depth, ...)} escape hatch the other providers offer takes an explicit door
 * list here rather than being omitted.</p>
 *
 * <h2>Routing</h2>
 * <p>Two shapes, because one does not cover both cases (see {@link PathRouting}). Up to three doors
 * the paths run door to door, which is the line a person actually walks. Above that the pairs
 * multiply &mdash; four doors is six lines, and six lines across a room is not a path, it is a
 * repaint &mdash; so the tracks converge on the middle of the room and fan out again, which is both
 * legible and what people really do in a room with four exits.</p>
 *
 * <h2>Softness</h2>
 * <p>A path drawn as a hard 3-wide stripe reads as a road, not as wear. Each cell's chance of being
 * path falls with its distance from the centre line &mdash; {@code centreProbability} on the line
 * itself, {@code edgeProbability} at the outer edge of {@code width} &mdash; so the band frays. The
 * roll consumes the room's own {@link RandomSource}, so two rooms with the same door layout still
 * fray differently.</p>
 *
 * <h2>Composing</h2>
 * <p>Implements {@link IFloorOverlayGenerator}, so a scheme can author {@code composite: [gradient,
 * path]} and get the path over a silted floor rather than over bare stone. Alone, its unmarked cells
 * fall to the motif's own floor base like any other sparse pattern's.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public class WornPathFloorPatternProvider implements IDungeonFloorGenerator, IFloorOverlayGenerator {

    /** A single-cell track, with no frayed edge to speak of. */
    public static final int DEFAULT_WIDTH = 3;

    /** Solid down the middle. */
    public static final double DEFAULT_CENTRE_PROBABILITY = 1.0;

    /** Ragged at the edge: about a third of the outermost cells. */
    public static final double DEFAULT_EDGE_PROBABILITY = 0.35;

    /** Above this many doors, all-pairs stops being a path and becomes a repaint. */
    public static final int PAIRS_LIMIT = 3;

    private final int width;
    private final double centreProbability;
    private final double edgeProbability;
    private final PathRouting routing;
    private final Block pathBlock;
    private final BlockState baseState;

    public WornPathFloorPatternProvider(int width, double centreProbability, double edgeProbability,
                                        PathRouting routing, Block pathBlock) {
        this(width, centreProbability, edgeProbability, routing, pathBlock,
                Blocks.STONE_BRICKS.defaultBlockState());
    }

    /**
     * @param baseState what non-path cells get from {@link #build} (the motif's own floor base,
     *                  supplied by the pattern). Unused by {@link #overlay}, which leaves those
     *                  cells to whatever it is layered over.
     */
    public WornPathFloorPatternProvider(int width, double centreProbability, double edgeProbability,
                                        PathRouting routing, Block pathBlock, BlockState baseState) {
        this.width = Math.max(1, width);
        this.centreProbability = centreProbability;
        this.edgeProbability = edgeProbability;
        this.routing = Objects.requireNonNull(routing, "routing");
        this.pathBlock = Objects.requireNonNull(pathBlock, "pathBlock");
        this.baseState = Objects.requireNonNull(baseState, "baseState");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                      List<BlockPlacement> out) {
        emit(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY,
                doorsOf(room), random, out, true);
    }

    @Override
    public void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                        List<BlockPlacement> out) {
        emit(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY,
                doorsOf(room), random, out, false);
    }

    /**
     * As {@link #build}, for a floor of the given size with the given doors, independent of
     * {@link RoomData}. Doors are ROOM-LOCAL here ({@code 0..width-1}), already converted.
     */
    public void build(int width, int depth, int originX, int originZ, int floorY,
                      List<int[]> doors, RandomSource random, List<BlockPlacement> out) {
        emit(width, depth, originX, originZ, floorY, doors, random, out, true);
    }

    /**
     * The room's doorways in room-local cells.
     *
     * <p>A doorway is stored in the same floor-local space as the room's origin, so this is a
     * subtraction &mdash; but it is the step that would silently draw a path across the wrong corner
     * of the room if it were wrong, since an out-by-the-origin door still lands somewhere plausible.
     * Doors outside the footprint are dropped rather than clamped: a clamp would invent a door on the
     * wall nearest the mistake, which is indistinguishable from a real one.</p>
     */
    static List<int[]> doorsOf(RoomData room) {
        List<int[]> doors = new ArrayList<>();
        for (Coords2D door : room.getDoorways()) {
            int x = door.getX() - room.getOriginX();
            int z = door.getY() - room.getOriginZ();
            if (x >= 0 && x < room.getWidth() && z >= 0 && z < room.getDepth()) {
                doors.add(new int[] {x, z});
            }
        }
        return doors;
    }

    private void emit(int width, int depth, int originX, int originZ, int floorY, List<int[]> doors,
                      RandomSource random, List<BlockPlacement> out, boolean includeBase) {
        List<double[]> segments = route(doors, width, depth, routing);
        BlockState path = pathBlock.defaultBlockState();
        double halfWidth = (this.width - 1) / 2.0D;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                double distance = distanceToNearestSegment(x, z, segments);
                boolean isPath = distance <= halfWidth + 1.0e-9
                        && random.nextDouble() < probabilityAt(distance, halfWidth);
                if (isPath) {
                    out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, path));
                } else if (includeBase) {
                    out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, baseState));
                }
            }
        }
    }

    /**
     * The lines the traffic runs along, as {@code {x1, z1, x2, z2}} in room-local cells. Empty for a
     * room with fewer than two doors &mdash; which is not a degenerate case to guard but the common
     * one for a dead end, and it correctly draws no path at all.
     */
    static List<double[]> route(List<int[]> doors, int width, int depth, PathRouting routing) {
        List<double[]> segments = new ArrayList<>();
        if (doors.size() < 2) {
            return segments;
        }
        boolean star = switch (routing) {
            case STAR -> true;
            case PAIRS -> false;
            case AUTO -> doors.size() > PAIRS_LIMIT;
        };
        if (star) {
            double centreX = (width - 1) / 2.0D;
            double centreZ = (depth - 1) / 2.0D;
            for (int[] door : doors) {
                segments.add(new double[] {door[0], door[1], centreX, centreZ});
            }
            return segments;
        }
        for (int i = 0; i < doors.size(); i++) {
            for (int j = i + 1; j < doors.size(); j++) {
                segments.add(new double[] {doors.get(i)[0], doors.get(i)[1],
                        doors.get(j)[0], doors.get(j)[1]});
            }
        }
        return segments;
    }

    /** Perpendicular distance from a cell to the nearest run, or +inf when there are no runs. */
    static double distanceToNearestSegment(int x, int z, List<double[]> segments) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] segment : segments) {
            best = Math.min(best, distanceToSegment(x, z, segment[0], segment[1], segment[2], segment[3]));
        }
        return best;
    }

    /**
     * Point-to-SEGMENT distance, not point-to-line.
     *
     * <p>The difference is the whole behaviour at a door: a line through two doors continues past
     * both of them forever, so a path would carry on into the corners behind each door. Clamping the
     * projection to {@code [0, 1]} is what makes the track stop where the traffic does.</p>
     */
    static double distanceToSegment(double px, double pz, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0.0D) {
            return Math.hypot(px - x1, pz - z1);
        }
        double t = ((px - x1) * dx + (pz - z1) * dz) / lengthSquared;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return Math.hypot(px - (x1 + t * dx), pz - (z1 + t * dz));
    }

    /**
     * The chance a cell {@code distance} from the centre line is path: {@code centreProbability} on
     * the line, {@code edgeProbability} at {@code halfWidth}.
     *
     * <p>Package-visible and pure so the falloff can be tested without the scatter it produces, the
     * same reason the two gradient providers expose theirs.</p>
     */
    double probabilityAt(double distance, double halfWidth) {
        if (halfWidth <= 0.0D) {
            // A one-cell path has no edge, so there is nothing to fade to and the edge probability
            // is not silently applied to the only cells the path has.
            return centreProbability;
        }
        double t = Math.min(1.0D, distance / halfWidth);
        return centreProbability + t * (edgeProbability - centreProbability);
    }
}
