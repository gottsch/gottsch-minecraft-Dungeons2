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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.GradientFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import net.minecraft.world.level.block.Block;

/**
 * Two materials mixed with a bias that runs from the walls to the middle of the room:
 * {@code edge_block} dominates the cells against the wall and gives way to {@code centre_block} as
 * the floor opens out.
 *
 * <h2>The floor's {@code gradient}, named to match the wall's</h2>
 * <p>Same id, same arithmetic, same reading &mdash; no line anywhere, because the boundary between
 * the two materials falls in a different place in every row. A {@code speckle} scatters its accent
 * at one flat rate over the whole floor and {@code checkerboard} alternates on a fixed beat; neither
 * can say "silted at the walls, walked clean in the middle", which is what a floor in a dungeon the
 * ground is reclaiming actually looks like.</p>
 *
 * <h2>The fields</h2>
 * <p>Named against the wall type's, one axis over: what {@code bottom}/{@code top} are to a wall,
 * {@code edge}/{@code centre} are to a floor. The pairing is deliberate, so an author who has
 * written one can write the other without looking it up.</p>
 * <ul>
 *   <li>{@code edge_block} / {@code centre_block} &mdash; both required. Neither has a default,
 *       because a gradient with one material is a plain floor and should be authored as one.</li>
 *   <li>{@code edge_probability} &mdash; the chance of {@code edge_block} on the outermost ring.
 *       Defaults to 1.0, a solid band at the wall.</li>
 *   <li>{@code centre_probability} &mdash; the chance of {@code edge_block} at the room's CENTRE.
 *       Defaults to 0.0. Both ends name the same material for the same reason the wall's do: a
 *       {@code centre_probability} of 0.1 means "a tenth of the middle is still the edge material",
 *       not "a tenth is the centre material".</li>
 *   <li>{@code hold_cells} &mdash; rings at the wall held at {@code edge_probability} before the
 *       ramp starts. 0 gives a plain linear ramp. Note that ring 0 is under the wall itself, so a
 *       hold of 1 buys nothing visible and 2 is the smallest hold that shows.</li>
 * </ul>
 *
 * <h2>No block properties, and no axis knob</h2>
 * <p>The wall type carries a {@code properties} map per material because a wall is built of stairs
 * and slabs that need a {@code facing}. Floor patterns in this package take plain blocks, and this
 * one follows the package rather than its own namesake. There is likewise no "ramp along X"
 * option: see {@link GradientFloorPatternProvider} for why a floor's gradient only has one direction
 * that means anything.</p>
 *
 * <h2>List it FIRST</h2>
 * <p>Like the wall version, this fills every cell rather than marking a few &mdash; it is the
 * floor's material, not a treatment over one. Inside a {@code composite}, a later pattern's marked
 * cells win, so name this first and a border or cross draws on top of it; name it last and it
 * erases them.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public record GradientFloorPattern(String edgeBlock, String centreBlock, double edgeProbability,
                                   double centreProbability, int holdCells) implements FloorPattern {

    public static final String NAME = "gradient";

    public static final MapCodec<GradientFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("edge_block").forGetter(GradientFloorPattern::edgeBlock),
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("centre_block").forGetter(GradientFloorPattern::centreBlock),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D),
                                    "edge_probability", 1.0D)
                            .forGetter(GradientFloorPattern::edgeProbability),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D),
                                    "centre_probability", 0.0D)
                            .forGetter(GradientFloorPattern::centreProbability),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE),
                                    "hold_cells", 0)
                            .forGetter(GradientFloorPattern::holdCells)
            ).apply(instance, GradientFloorPattern::new)));

    /** See {@link FloorPattern#withRoles}. */
    @Override
    public FloorPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedEdgeBlock = Codecs.resolveRole(edgeBlock, resolver);
        String resolvedCentreBlock = Codecs.resolveRole(centreBlock, resolver);
        if (resolvedEdgeBlock.equals(edgeBlock) && resolvedCentreBlock.equals(centreBlock)) {
            return this;
        }
        return new GradientFloorPattern(resolvedEdgeBlock, resolvedCentreBlock, edgeProbability,
                centreProbability, holdCells);
    }

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block edge = FloorPatterns.block(edgeBlock);
        Block centre = FloorPatterns.block(centreBlock);
        // Either one missing degrades the WHOLE pattern to plain floor rather than filling with the
        // survivor -- a floor drawn entirely in one of the two materials looks authored and would
        // never be reported, where a plain floor reads as a plain floor. Same rule the wall version
        // states, and the same one every pattern in this package follows.
        return FloorPatterns.allResolve(edge, centre)
                ? new GradientFloorPatternProvider(edge, centre, edgeProbability, centreProbability,
                        holdCells)
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
