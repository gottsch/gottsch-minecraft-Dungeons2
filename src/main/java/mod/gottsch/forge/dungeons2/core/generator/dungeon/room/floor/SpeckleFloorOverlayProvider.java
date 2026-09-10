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
 * A {@code speckle} authored with <strong>no {@code primary_block}</strong>: it scatters its accent
 * and leaves every other cell to whatever drew it.
 *
 * <h2>Why this exists</h2>
 * <p>Mark, 2026-09-09, on the corridor speckle replacing the {@code floor}/{@code alternate_floor}
 * pair outright: <em>"can't we do a composite pattern where the speckle overlays the floor? I don't
 * really want to add another pattern with an accent just to get some square stone bricks in."</em>
 * Both halves of that are answered here rather than by a new pattern type. A composite alone could
 * not have done it &mdash; {@code CompositeFloorPatternProvider} layers over a base fill, but only
 * over generators that implement {@link IFloorOverlayGenerator}, and until now speckle filled every
 * cell, so layering it over anything simply erased the thing underneath.</p>
 *
 * <p>So the accent-only mode is the same {@code speckle}, minus one field. It is the first accent
 * that is <em>random</em> rather than geometric: {@code border}, {@code cross}, {@code spokes},
 * {@code path} and {@code field} could all already layer, and all of them mark a shape.</p>
 *
 * <h2>Two things it must not do</h2>
 * <ul>
 *   <li><strong>Never emit an unaccented cell.</strong> That is {@link IFloorOverlayGenerator}'s
 *       whole contract; a placement for a cell it does not want would stomp the base fill.</li>
 *   <li><strong>Always draw once per cell.</strong> Accented or not, the stream advances by exactly
 *       one, so the cells after it do not shift when the probability changes and, in a corridor,
 *       do not shift with how the piece was cut into chunks. See {@code CellLocalFloorPattern}.</li>
 * </ul>
 *
 * <p>Used as a room's <em>whole</em> floor rather than as a layer, it still has to fill: the
 * {@code underlay} handed in is run first so a floor never comes out with holes in it.</p>
 */
public class SpeckleFloorOverlayProvider implements IDungeonFloorGenerator, IFloorOverlayGenerator {

    private final double probability;
    private final Block accentBlock;
    /** What {@link #build} fills with when this is a whole floor; never consulted by an overlay. */
    private final IDungeonFloorGenerator underlay;

    public SpeckleFloorOverlayProvider(double probability, Block accentBlock,
                                       IDungeonFloorGenerator underlay) {
        this.probability = probability;
        this.accentBlock = Objects.requireNonNull(accentBlock, "accentBlock");
        this.underlay = Objects.requireNonNull(underlay, "underlay");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                      List<BlockPlacement> out) {
        // Fill, then accent over it: a later placement in the same cell wins, the same layering
        // rule CompositeFloorPatternProvider uses.
        underlay.build(room, floorY, motif, random, out);
        overlay(room, floorY, motif, random, out);
    }

    @Override
    public void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                        List<BlockPlacement> out) {
        BlockState accent = accentBlock.defaultBlockState();
        for (int x = 0; x < room.getWidth(); x++) {
            for (int z = 0; z < room.getDepth(); z++) {
                if (random.nextFloat() < probability) {
                    out.add(BlockStateCodec.placement(
                            room.getOriginX() + x, floorY, room.getOriginZ() + z, accent));
                }
            }
        }
    }
}
