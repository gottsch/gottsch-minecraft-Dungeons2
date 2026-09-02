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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * Every cell at or inside {@code inset} &mdash; a filled rectangle, and the missing half of
 * {@link FloorBorderPatternProvider}'s ring. Backlog #75.
 *
 * <h2>What it is for</h2>
 * <p>Nothing could fill the panel a {@code border} frames. The nearest things were a
 * {@link CrossFloorPatternProvider} (a plus, not a panel) and a hand-sized {@code centre}, and
 * neither follows the room's size &mdash; a 3&times;3 boss is a dot in a 15-wide floor. This is the
 * ceiling's {@code field} (#68) laid flat, minus the rise: the ceiling grew one first because a
 * RISING vault needs a filled area rather than an outline, and the floor never had the equivalent
 * need until someone wanted to fill a border.</p>
 *
 * <h2>Inset counts from the room edge, so a field and a border at the same inset OVERLAP</h2>
 * <p>Deliberately, and the same as the ceiling's: {@code inset} is measured the way
 * {@code border}'s is, so the two are directly comparable. A field at inset {@code n} covers the
 * ring at inset {@code n} as well as everything inside it &mdash; so <strong>to fill the panel a
 * border at inset 2 frames, author the field at inset 3</strong> and list it after the border.
 * Listing it before is not an error and is occasionally what you want: the ring then draws over the
 * field's outermost row.</p>
 *
 * <h2>An inset with no field left</h2>
 * <p>An {@code inset} that meets or crosses the middle marks nothing at all, which is the same
 * answer the ceiling's gives and is right rather than degenerate: there is no floor left to fill.
 * It matters more here than there, because a floor pattern is rolled for whatever room the planner
 * produced &mdash; an inset that reads well in a 15-wide hall must simply do nothing in a 7-wide
 * chamber rather than drawing a one-cell dot.</p>
 *
 * <p>Also an {@link IFloorOverlayGenerator}: {@link #overlay} emits only the field cells, which is
 * what lets it be listed after a {@code border} inside a {@code composite} and fill only what the
 * ring encloses. Geometry lives in {@link #plan} as registry-free data for the same
 * unit-testability reason as {@code FloorBorderPatternProvider}.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public class FieldFloorPatternProvider implements IDungeonFloorGenerator, IFloorOverlayGenerator {

    /** Matches {@link FloorBorderPatternProvider#DEFAULT_INSET}, so the two line up when paired. */
    public static final int DEFAULT_INSET = 2;

    private final int inset;
    private final Block fieldBlock;
    private final BlockState baseState;

    public FieldFloorPatternProvider(int inset, Block fieldBlock) {
        this(inset, fieldBlock, Blocks.STONE_BRICKS.defaultBlockState());
    }

    /**
     * @param baseState what cells outside the field get from {@link #build} (the motif's own floor
     *                  base, supplied by {@code FloorPatternSelector}). Unused by {@link #overlay},
     *                  which leaves them to whatever it is layered over.
     */
    public FieldFloorPatternProvider(int inset, Block fieldBlock, BlockState baseState) {
        this.inset = Math.max(0, inset);
        this.fieldBlock = Objects.requireNonNull(fieldBlock, "fieldBlock");
        this.baseState = Objects.requireNonNull(baseState, "baseState");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                      List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /** Fills every cell: the field in its own block, everything else in the base block. */
    public void build(int width, int depth, int originX, int originZ, int floorY,
                      List<BlockPlacement> out) {
        emit(width, depth, originX, originZ, floorY, out, true);
    }

    /** Emits only the field cells, leaving the rest to whatever this is layered over. */
    @Override
    public void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                        List<BlockPlacement> out) {
        emit(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out,
                false);
    }

    private void emit(int width, int depth, int originX, int originZ, int floorY,
                      List<BlockPlacement> out, boolean includeBase) {
        boolean[][] grid = plan(width, depth, inset);
        BlockState field = fieldBlock.defaultBlockState();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (grid[x][z]) {
                    out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, field));
                } else if (includeBase) {
                    out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, baseState));
                }
            }
        }
    }

    /**
     * Pure geometry: {@code true} at or inside {@code inset} on both axes. Package-visible for
     * direct unit testing, and registry-free so it runs without a Forge instance.
     *
     * <p>An inset that leaves no cells returns an empty grid rather than throwing or clamping, the
     * same graceful degradation every other pattern here has.</p>
     */
    static boolean[][] plan(int width, int depth, int inset) {
        boolean[][] grid = new boolean[width][depth];
        if (inset < 0) {
            return grid;
        }
        for (int x = inset; x < width - inset; x++) {
            for (int z = inset; z < depth - inset; z++) {
                grid[x][z] = true;
            }
        }
        return grid;
    }
}
