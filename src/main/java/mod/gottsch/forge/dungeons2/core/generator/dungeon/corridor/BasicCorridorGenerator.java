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

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.pattern.ceiling.CeilingPattern;
import mod.gottsch.forge.dungeons2.core.pattern.floor.CorridorFloorPattern;
import mod.gottsch.forge.dungeons2.core.pattern.wall.WallPattern;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns.CORRIDOR_CEILING_PATTERN;
import static mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns.WALL_PATTERN;

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
 * @author Mark Gottschling on Dec 5, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicCorridorGenerator implements ICorridorGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    @Override
    public void build(CorridorData corridor, Grid2D grid, int floorY,
                      IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        Palette palette = palette(motif, random);

        Set<Coords2D> wallsEmitted = new HashSet<>();
        for (Coords2D cell : corridor.getCells()) {
            int x = cell.getX();
            int z = cell.getY();
            emitCorridorColumn(x, z, floorY, palette, out);

            // 8-neighbor wall columns, sourced live from the grid.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = x + dx;
                    int nz = z + dz;
                    Coords2D neighbor = new Coords2D(nx, nz);
                    if (wallsEmitted.contains(neighbor)) continue;
                    if (isWallElement(grid, nx, nz)) {
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
            emitCorridorColumn(cell.getX(), cell.getY(), floorY, palette, out);
        }
        for (Coords2D wall : corridor.getWallCells()) {
            emitWallColumn(wall.getX(), wall.getY(), floorY, palette, out);
        }
    }

    /**
     * A floor block at {@code floorY}, 3 air blocks above, and a ceiling block at
     * {@code floorY+4} (the top of the 5-tall corridor walls), closing the corridor.
     */
    private static void emitCorridorColumn(int x, int z, int floorY, Palette palette, List<BlockPlacement> out) {
        out.add(BlockStateCodec.placement(x, floorY, z, palette.floor));
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
     * Resolves the floor / wall / air / ceiling block states once per build call.
     * Wall and corridor-floor blocks come from the same WALL_PATTERN BlockSet
     * (matching the original behavior; FLOOR is a slot inside the wall pattern);
     * the ceiling comes from its own CORRIDOR_CEILING_PATTERN BlockSet, mirroring
     * {@code BasicCeilingGenerator}'s room ceiling.
     */
    private static Palette palette(IDungeonMotif motif, RandomSource random) {
        BlockSet blockSet = BlockProvider.get(motif, WALL_PATTERN, random);
        BlockSet ceilingBlockSet = BlockProvider.get(motif, CORRIDOR_CEILING_PATTERN, random);
        return new Palette(
                blockSet.get(CorridorFloorPattern.FLOOR).orElse(DEFAULT),
                blockSet.get(WallPattern.WALL).orElse(DEFAULT),
                Blocks.AIR.defaultBlockState(),
                ceilingBlockSet.get(CeilingPattern.CEILING).orElse(DEFAULT));
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

    /** Resolved block states for one corridor render pass. */
    private record Palette(BlockState floor, BlockState wall, BlockState air, BlockState ceiling) {}
}
