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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.gottschcore.json.StrictCodecs;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingStage;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.BlockIds;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * One decay chain, scoped to a {@link PieceSurface} and a {@link PieceElevation} band.
 *
 * <p>Identical to GottschCore's {@code AgingRule} but for the added {@code surface}, and it reuses
 * that class's {@link AgingStage} verbatim &mdash; so a chain reads the same here as it does in a
 * {@code dungeons2:aging} entry, and an author who knows one knows the other. {@code surface}
 * defaults to {@link PieceSurface#ANY}, which makes an ungated rule behave exactly as the shared
 * processor's would.</p>
 *
 * <p><strong>Probabilities are conditional, not absolute</strong>, exactly as in
 * {@code AgingRule}: each stage's chance is <em>given the stage before it was reached</em>.</p>
 *
 * <p>{@code elevation} is a SECOND and independent gate (added 2026-09-18 for the entrance ruin).
 * The two are ANDed: a rule may name a surface, a band, both, or neither. They have to be separate
 * fields rather than more {@code surface} values because a block has one surface and one band at
 * the same time &mdash; see {@link PieceElevation} for why that rules out the simpler design.</p>
 */
public record SurfaceAgingRule(PieceSurface surface, PieceElevation elevation, Block block,
                               List<AgingStage> outputBlocks) {

    /** An ungated-by-height rule, which is nearly all of them. */
    public SurfaceAgingRule(PieceSurface surface, Block block, List<AgingStage> outputBlocks) {
        this(surface, PieceElevation.ANY, block, outputBlocks);
    }

    public static final Codec<SurfaceAgingRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Both optional, so this record stays a strict superset of AgingRule -- an entry that
            // names neither decays everything, which is what `dungeons2:aging` already does.
            StrictCodecs.strictOptionalFieldOf(PieceSurface.CODEC, "surface", PieceSurface.ANY)
                    .forGetter(SurfaceAgingRule::surface),
            StrictCodecs.strictOptionalFieldOf(PieceElevation.CODEC, "elevation", PieceElevation.ANY)
                    .forGetter(SurfaceAgingRule::elevation),
            BlockIds.CODEC.fieldOf("block").forGetter(SurfaceAgingRule::block),
            AgingStage.CODEC.listOf().fieldOf("output_blocks").forGetter(SurfaceAgingRule::outputBlocks)
    ).apply(instance, SurfaceAgingRule::new));
}
