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
package mod.gottsch.forge.dungeons2.core.config.pit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.CentrePitShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;

import java.util.Map;
import java.util.Optional;

/**
 * A square sunken court of {@code size} cells a side, centred, terraced one block per ring inward.
 *
 * <p>{@code depth} is a MAXIMUM in two directions. It is clamped to the floor's {@code sink_offset}
 * as the generator writes, and it is bounded by the footprint too &mdash; the court steps down one
 * block per ring, so a 3x3 reaches two and stops however deep it is authored. 5x5 is the smallest
 * that descends three.</p>
 *
 * <h2>{@code centre_block} makes this a SUNKEN DAIS (backlog #86)</h2>
 * <p>An altar standing in the middle of the court, on its floor rather than in it. At the shallow
 * {@code depth: 1} a court is authored at for this &mdash; the inverted dais Mark asked for &mdash;
 * that puts the block's top flush with the room's own walking plane, so the player walks around a
 * centrepiece they can see over, standing one step down. That is the whole read, and it is why this
 * is a field on the existing court rather than a provider of its own: the geometry is a centre pit
 * exactly as it already was, and only the middle cell changed.</p>
 *
 * <p>{@code centre_chest} is the same cell's other tenant: a REAL chest, with the loot table that
 * makes it worth walking down to. It is mutually exclusive with {@code centre_block} (they stand in
 * the same cell) and its {@code loot_tables} are REQUIRED, because a pit provider is handed no
 * floor chest band to fall back on and a silent fallback failure would leave an empty chest at the
 * heart of the room &mdash; the one outcome {@code RoomChestGenerator} refuses everywhere else.</p>
 *
 * <p>The one thing it does constrain is parity. A court needs a MIDDLE cell for the block to stand
 * in, so an even {@code size} is a load error when {@code centre_block} is authored &mdash; the same
 * rule, for the same reason, that {@code PlatformPatternEntry} imposes on the raised dais's
 * {@code top_block}. The provider ALSO steps an even fit down to odd, because {@code size} is a
 * maximum and a small interior can shrink an odd authored size into an even one, which no load-time
 * check can see.</p>
 */
public record CentrePitShape(int size, int depth, Optional<String> rimBlock,
                             SurfaceOrient rimOrient, Optional<String> centreBlock,
                             Map<String, String> centreProperties,
                             Optional<mod.gottsch.forge.dungeons2.core.config.ChestConfig> centreChest)
        implements PitShapePattern {

    /** The shape this record had before {@code centre_chest}. */
    public CentrePitShape(int size, int depth, Optional<String> rimBlock, SurfaceOrient rimOrient,
                          Optional<String> centreBlock, Map<String, String> centreProperties) {
        this(size, depth, rimBlock, rimOrient, centreBlock, centreProperties, Optional.empty());
    }

    public static final String NAME = "centre";

    /** Three: the smallest square that has a middle and an edge on every side. */
    public static final int DEFAULT_SIZE = 3;

    /** Two: one step down and one along, the shallowest thing that reads as a court not a kerb. */
    public static final int DEFAULT_DEPTH = 2;

    /** The default rim: a vanilla stair's solid half away from the pit, low edge toward it. */
    public static final SurfaceOrient DEFAULT_RIM_ORIENT = SurfaceOrient.OUTWARD;

    public CentrePitShape() {
        this(DEFAULT_SIZE, DEFAULT_DEPTH, Optional.empty(), DEFAULT_RIM_ORIENT, Optional.empty(),
                Map.of());
    }

    /** Un-rimmed, for a test or a court that wants a plain kerb. */
    public CentrePitShape(int size, int depth) {
        this(size, depth, Optional.empty(), DEFAULT_RIM_ORIENT, Optional.empty(), Map.of());
    }


    public static final MapCodec<CentrePitShape> CODEC = Codecs.closedMap(
            // Explicit type witness: chaining flatXmap onto an un-witnessed mapCodec infers
            // Object here and reports the failure against group(), not against the chain.
            RecordCodecBuilder.<CentrePitShape>mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "size",
                            DEFAULT_SIZE).forGetter(CentrePitShape::size),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 24), "depth", DEFAULT_DEPTH)
                            .forGetter(CentrePitShape::depth),
                    // A ring of stairs on the floor cells just OUTSIDE the court. Omit for a plain
                    // kerb; see PitPlans#stairRim for what it buys and which way it faces.
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "rim_block")
                            .forGetter(CentrePitShape::rimBlock),
                    Codecs.strictOptionalFieldOf(SurfaceOrient.CODEC, "rim_orient",
                            DEFAULT_RIM_ORIENT).forGetter(CentrePitShape::rimOrient),
                    // The altar in the middle of the court -- see the class doc. Omit for a bare
                    // sunken floor, which is how every court authored before this shipped reads.
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "centre_block")
                            .forGetter(CentrePitShape::centreBlock),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "centre_properties", Map.of())
                            .forGetter(CentrePitShape::centreProperties),
                    // A real chest in the middle of the court -- see the class doc for why its
                    // loot_tables are required where the room's own chests slot leaves them optional.
                    Codecs.strictOptionalFieldOf(
                                    mod.gottsch.forge.dungeons2.core.config.ChestConfig.CODEC,
                                    "centre_chest")
                            .forGetter(CentrePitShape::centreChest)
            ).apply(instance, CentrePitShape::new)).flatXmap(CentrePitShape::validate,
                    CentrePitShape::validate));

    private static DataResult<CentrePitShape> validate(CentrePitShape shape) {
        if (shape.centreChest().isPresent() && shape.centreBlock().isPresent()) {
            return DataResult.error(() -> "pit 'centre': centre_block and centre_chest both stand in"
                    + " the court's middle cell, so only one of them may be authored");
        }
        if (shape.centreChest().isPresent()
                && shape.centreChest().orElseThrow().declaredLootTables().isEmpty()) {
            return DataResult.error(() -> "pit 'centre': centre_chest declares no loot_tables, and a"
                    + " pit has no floor band to fall back to -- name the table, or the chest would"
                    + " generate empty");
        }
        if (shape.hasCentrepiece() && shape.size() % 2 == 0) {
            return DataResult.error(() -> "pit 'centre': size " + shape.size() + " is even, so the"
                    + " court has no middle cell for its centrepiece to stand in -- use an odd"
                    + " size");
        }
        if (shape.centreBlock().isEmpty() && !shape.centreProperties().isEmpty()) {
            return DataResult.error(() -> "pit 'centre': centre_properties without a centre_block,"
                    + " so the properties describe nothing -- either name the block or drop them");
        }
        return DataResult.success(shape);
    }

    /** Whether anything at all is authored to stand in the court's middle cell. */
    public boolean hasCentrepiece() {
        return centreBlock().isPresent() || centreChest().isPresent();
    }

    /** See {@link PitShapePattern#withRoles}. */
    @Override
    public PitShapePattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        Optional<String> resolvedRimBlock = Codecs.resolveRole(rimBlock, resolver);
        Optional<String> resolvedCentreBlock = Codecs.resolveRole(centreBlock, resolver);
        if (resolvedRimBlock.equals(rimBlock) && resolvedCentreBlock.equals(centreBlock)) {
            return this;
        }
        return new CentrePitShape(size, depth, resolvedRimBlock, rimOrient, resolvedCentreBlock,
                centreProperties, centreChest);
    }

    @Override
    public MapCodec<? extends PitShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPitShapeProvider provider() {
        return new CentrePitShapeProvider(size, depth, rimBlock.orElse(null), rimOrient,
                centreBlock.orElse(null), centreProperties, centreChest.orElse(null));
    }
}
