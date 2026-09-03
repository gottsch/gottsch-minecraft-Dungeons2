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
 * Alternating bands at 45 degrees &mdash; {@code checkerboard}'s diagonal partner, and the other
 * genuinely two-line pattern. Backlog #82.
 *
 * <h2>The geometry</h2>
 * <p>A cell's band is {@code (x + z) / width}, and every other band takes the secondary block; a
 * {@code width} of 1 is therefore diagonal pinstripes and 3 is a broad ribbon. {@code flipped}
 * uses {@code (x - z)} instead, which is the other diagonal &mdash; the one thing about this
 * pattern an author is otherwise stuck with.</p>
 *
 * <h2>It is anchored to the room, not to the world</h2>
 * <p>This is the decision the backlog entry said to make before writing the provider, and it is
 * made the same way {@link CheckerboardFloorPatternProvider} already made it: the plan is computed
 * in floor-local {@code (x, z)} and the room's origin is added only when emitting. So every room
 * starts its first band at its own corner. Computing it in world coordinates would instead run one
 * continuous striped field through the whole dungeon, with adjacent rooms' bands meeting through
 * the wall between them &mdash; a real effect, but one that reads as the floor ignoring the rooms
 * rather than belonging to them.</p>
 *
 * <h2>A fill, so it goes FIRST in a composite</h2>
 * <p>Both blocks are required and every cell gets one of them, exactly like the checkerboard, which
 * is why this is deliberately <strong>not</strong> an {@link IFloorOverlayGenerator}: an overlay
 * that wrote every cell would erase whatever it was layered over, which is the one thing that
 * contract forbids. Inside a {@code "composite"} it is a base layer with the sparse patterns
 * ({@code border}, {@code cross}, {@code chevron}) listed after it &mdash; and listed anywhere but
 * first it is silently dropped, because {@code CompositeFloorPattern} takes its base from the first
 * entry and keeps only the later ones that are overlays.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public class DiagonalFloorPatternProvider implements IDungeonFloorGenerator {

    /** Two cells, so a band reads as a band rather than as a pinstripe. */
    public static final int DEFAULT_WIDTH = 2;

    private final Block primaryBlock;
    private final Block secondaryBlock;
    private final int bandWidth;
    private final boolean flipped;

    public DiagonalFloorPatternProvider(Block primaryBlock, Block secondaryBlock, int bandWidth,
                                        boolean flipped) {
        this.primaryBlock = Objects.requireNonNull(primaryBlock, "primary_block");
        this.secondaryBlock = Objects.requireNonNull(secondaryBlock, "secondary_block");
        this.bandWidth = bandWidth;
        this.flipped = flipped;
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                      List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /**
     * Builds the pattern for a floor of the given size at the given origin, independent of
     * {@link RoomData} (e.g. for use outside the room pipeline).
     */
    public void build(int width, int depth, int originX, int originZ, int floorY,
                      List<BlockPlacement> out) {
        boolean[][] grid = plan(width, depth, bandWidth, flipped);
        BlockState primary = primaryBlock.defaultBlockState();
        BlockState secondary = secondaryBlock.defaultBlockState();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                BlockState state = grid[x][z] ? primary : secondary;
                out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, state));
            }
        }
    }

    /**
     * Pure geometry: {@code true} where the primary band runs. Package-visible for direct unit
     * testing, and registry-free so it runs without a Forge instance.
     *
     * <p>{@code (x - z)} goes negative on the far side of the diagonal, so the band index and its
     * parity both use {@link Math#floorDiv}/{@link Math#floorMod} rather than {@code /} and
     * {@code %} &mdash; truncating toward zero would double the width of the band that straddles
     * zero and leave a seam down the room's own diagonal.</p>
     */
    static boolean[][] plan(int width, int depth, int bandWidth, boolean flipped) {
        boolean[][] grid = new boolean[width][depth];
        // A non-positive width has no meaning and cannot be authored (the codec starts at 1); it is
        // clamped rather than returning an empty grid, which here would read as "all secondary".
        int band = Math.max(1, bandWidth);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int diagonal = flipped ? x - z : x + z;
                grid[x][z] = Math.floorMod(Math.floorDiv(diagonal, band), 2) == 0;
            }
        }
        return grid;
    }
}
