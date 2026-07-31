/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds one corridor region as {@link BlockPlacement}s.
 *
 * <p>For each cell in the corridor: emits a floor block at Y={@code floorY},
 * 3 air blocks above (Y={@code floorY+1..floorY+3}), and a motif ceiling block
 * at Y={@code floorY+4}. For each grid cell <em>neighboring</em> the corridor
 * that is rock / wall / door / connector / out-of-bounds, emits a 5-block
 * wall column (Y={@code floorY..floorY+4}). Walls are deduped per builder
 * call so the same neighbor cell isn't emitted multiple times for a single
 * corridor; cross-corridor wall duplication is acceptable (the renderer
 * idempotently overwrites).</p>
 *
 * <p>A <strong>door</strong> neighbor gets that same column with the two
 * door-half levels left as air &mdash; see {@link #DOOR_HALF_LOW}.</p>
 *
 * @author Mark Gottschling on Dec 5, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicCorridorGenerator implements ICorridorGenerator {

    private MotifConfig motifConfig = MotifConfig.DEFAULT;

    /** See {@code BasicWallGenerator#withMotifConfig}. */
    public BasicCorridorGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    /**
     * Y offsets (above the floor surface) that {@code BasicDoorGenerator} fills
     * with the two door halves. A corridor bordering a door cell must not emit a
     * solid block there: the corridor's decoration pass runs before
     * {@code DungeonDoorPiece} carves the door, so a full cube in the door cell
     * anchors glow lichen in the corridor air beside it, facing the door cell.
     * Glow lichen is a MultifaceBlock and renders flush against its anchor's
     * face, so once the door lands it appears plastered onto the door. The door
     * belongs to a different piece, so no processor can see it coming. Mirrors
     * {@code BasicWallGenerator}'s handling on the room side.
     */
    private static final int DOOR_HALF_LOW = 1;
    private static final int DOOR_HALF_HIGH = 2;

    @Override
    public void build(CorridorData corridor, Grid2D grid, int floorY,
                      IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        Palette palette = palette(motif, random);

        Set<Coords2D> wallsEmitted = new HashSet<>();
        for (Coords2D cell : corridor.getCells()) {
            int x = cell.getX();
            int z = cell.getY();
            emitCorridorColumn(x, z, floorY, palette, random, out);

            // 8-neighbor wall columns, sourced live from the grid.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = x + dx;
                    int nz = z + dz;
                    Coords2D neighbor = new Coords2D(nx, nz);
                    if (wallsEmitted.contains(neighbor)) continue;
                    if (isDoorElement(grid, nx, nz)) {
                        emitDoorwayColumn(nx, nz, floorY, palette, out);
                        wallsEmitted.add(neighbor);
                    } else if (isWallElement(grid, nx, nz)) {
                        emitWallColumn(nx, nz, floorY, palette, out);
                        wallsEmitted.add(neighbor);
                    }
                }
            }
        }
    }

    @Override
    public void build(CorridorData corridor, int floorY,
                      IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        Palette palette = palette(motif, random);

        // Corridor columns first, then the pre-computed wall cells. The two sets
        // are disjoint within a single corridor, so order is immaterial; the
        // placements match the grid-based overload as a set.
        for (Coords2D cell : corridor.getCells()) {
            emitCorridorColumn(cell.getX(), cell.getY(), floorY, palette, random, out);
        }
        for (Coords2D wall : corridor.getWallCells()) {
            emitWallColumn(wall.getX(), wall.getY(), floorY, palette, out);
        }
        for (Coords2D door : corridor.getDoorCells()) {
            emitDoorwayColumn(door.getX(), door.getY(), floorY, palette, out);
        }
    }

    /**
     * A floor block at {@code floorY} (45% {@code floor}, 55% {@code alternateFloor}, matching
     * {@code BasicFloorGenerator}'s room-floor split), 3 air blocks above, and a ceiling block at
     * {@code floorY+4} (the top of the 5-tall corridor walls), closing the corridor.
     */
    private static void emitCorridorColumn(int x, int z, int floorY, Palette palette, RandomSource random,
                                            List<BlockPlacement> out) {
        BlockState floor = RandomHelper.checkProbability(random, 45) ? palette.floor : palette.alternateFloor;
        out.add(BlockStateCodec.placement(x, floorY, z, floor));
        for (int yOffset = 1; yOffset < 4; yOffset++) {
            out.add(BlockStateCodec.placement(x, floorY + yOffset, z, palette.air));
        }
        out.add(BlockStateCodec.placement(x, floorY + 4, z, palette.ceiling));
    }

    /** A 5-block wall column (Y = floorY .. floorY+4). */
    private static void emitWallColumn(int x, int z, int floorY, Palette palette, List<BlockPlacement> out) {
        for (int yOffset = 0; yOffset < 5; yOffset++) {
            out.add(BlockStateCodec.placement(x, floorY + yOffset, z, palette.wall));
        }
    }

    /**
     * A wall column with the two door-half levels left as air, so the doorway is
     * walkable and carries no full cube for the decoration pass to anchor to.
     * The sill ({@code floorY}) and lintel ({@code floorY+3}) levels stay solid:
     * they are full cubes in the finished doorway anyway, and keeping them means
     * a door piece that never runs leaves a 2-block gap rather than a full-height
     * hole in the corridor wall.
     */
    private static void emitDoorwayColumn(int x, int z, int floorY, Palette palette, List<BlockPlacement> out) {
        for (int yOffset = 0; yOffset < 5; yOffset++) {
            BlockState state = (yOffset == DOOR_HALF_LOW || yOffset == DOOR_HALF_HIGH)
                    ? palette.air : palette.wall;
            out.add(BlockStateCodec.placement(x, floorY + yOffset, z, state));
        }
    }

    /**
     * Resolves the floor / wall / air / ceiling block states once per build call. The corridor has
     * its own floor pair and ceiling ({@code CorridorConfig}) but shares the room's wall block
     * ({@code WallConfig}), matching the pre-merge {@code block_provider} split.
     */
    private Palette palette(IDungeonMotif motif, RandomSource random) {
        return new Palette(
                motifConfig.corridor().floorState(),
                motifConfig.corridor().alternateFloorState(),
                motifConfig.wall().wallState(),
                Blocks.AIR.defaultBlockState(),
                motifConfig.corridor().ceilingState());
    }

    /** True if the cell at (x,z) is a wall-equivalent for corridor-wall placement. */
    private static boolean isWallElement(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return true;
        }
        CellType type = grid.get(x, z).getType();
        return type == CellType.ROCK || type == CellType.WALL
                || type == CellType.DOOR || type == CellType.CONNECTOR;
    }

    /**
     * True if the cell at (x,z) is an opened doorway. CONNECTOR is deliberately
     * NOT included: an unopened connector is reverted to a plain wall and has no
     * door piece behind it, so piercing it would leave a hole.
     */
    private static boolean isDoorElement(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return false;
        }
        return grid.get(x, z).getType() == CellType.DOOR;
    }

    /** Resolved block states for one corridor render pass. */
    private record Palette(BlockState floor, BlockState alternateFloor, BlockState wall, BlockState air,
                            BlockState ceiling) {}
}
