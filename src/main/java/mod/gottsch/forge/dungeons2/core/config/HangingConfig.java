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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The {@code hangings} scheme slot: what hangs from a room's ceiling &mdash; a chandelier, a gibbet,
 * meat hooks, a censer &mdash; each on a run of chain.
 *
 * <h2>Why this is not the {@code props} slot</h2>
 * <p>A prop stands on a floor cell and claims it. A hanging claims nothing on the floor: it is
 * always hung with {@code RoomHangingGenerator#CLEARANCE} rows of air beneath it, so a player walks
 * under it and a barrel or a pot may stand below it. What decides whether a cell can take one is
 * the COLUMN above the floor &mdash; how much air there is and what closes it off at the top &mdash;
 * which is a question no floor-cell slot asks.</p>
 *
 * <h2>{@code min_drop} / {@code max_drop}</h2>
 * <p>How many chain links between the ceiling and the fixture. Rolled per hanging, then CLAMPED to
 * what that column has room for, rather than the cell being refused: a slot authored for tall halls
 * still draws in a lower room, hung shorter. The same rule the rising vault follows
 * ({@code BasicCeilingGenerator#withRiseBudget}). A cell is refused only when even a fixture hung
 * flush to the ceiling would leave less than the clearance.</p>
 *
 * <h2>What a variant may name</h2>
 * <p>Anything that hangs: the DungeonBlocks chain fixtures, a chandelier, a gibbet (three blocks
 * tall; the generator knows its parts), or a vanilla {@code minecraft:lantern}, which is written
 * with {@code hanging: true}. A block that neither hangs nor survives under a chain places and then
 * drops at its first block update &mdash; a block id is a string to this codec. Chain fixtures that
 * the weathering can orphan must also be members of the {@code dungeons2:hanging_sweep} list in the
 * motif's processor lists, or a severed chain leaves them floating.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public record HangingConfig(int minCount, int maxCount, HangingPlacement placement,
                            List<HangingVariant> variants, String chain, int minDrop, int maxDrop,
                            SizeGate gate) {

    /** Ungated, plain chain. */
    public HangingConfig(int minCount, int maxCount, HangingPlacement placement,
                         List<HangingVariant> variants, int minDrop, int maxDrop) {
        this(minCount, maxCount, placement, variants, DEFAULT_CHAIN, minDrop, maxDrop,
                SizeGate.UNBOUNDED);
    }

    public static final String DEFAULT_CHAIN = "minecraft:chain";

    /**
     * Which ceiling cells a hanging may hang from. The same kind of closed candidate-set enum as
     * {@code PropConfig.PropPlacement}, and for its reason: a placement that offers nothing places
     * nothing, rather than spilling somewhere the author did not choose.
     */
    public enum HangingPlacement implements StringRepresentable {
        /**
         * The middle of the room: one cell, or the two or four that share the middle when an
         * interior extent is even. For the single deliberate object &mdash; the hall's chandelier.
         */
        CENTRE("centre"),
        /** Anywhere in the interior. The default: meat hooks, censers, a gibbet in a gaol. */
        FREE("free"),
        /**
         * The interior ring, minus the doorway approaches: a row of hooks along a larder wall.
         * A projecting cornice closes the column off lower than the ceiling, and whether a fixture
         * can hang from ITS underside is the block's own support shape &mdash; usually not.
         */
        AGAINST_WALL("against_wall");

        private final String name;

        HangingPlacement(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Failing, for {@code PropPlacement#CODEC}'s reason: a closed set, so an unknown is a typo. */
        public static final Codec<HangingPlacement> CODEC =
                StringRepresentable.fromEnum(HangingPlacement::values);
    }

    /** One weighted fixture block. Facing, where the block has one, is the generator's. */
    public record HangingVariant(String block, int weight) {

        public HangingVariant(String block) {
            this(block, 1);
        }

        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<HangingVariant> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(HangingVariant::block),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(HangingVariant::weight)
        ).apply(instance, HangingVariant::new)));
    }

    /** Open, for {@link SlotOptions}; see {@code PropConfig#MAP_CODEC}. */
    public static final MapCodec<HangingConfig> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_count", 1)
                    .forGetter(HangingConfig::minCount),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_count", 2)
                    .forGetter(HangingConfig::maxCount),
            Codecs.strictOptionalFieldOf(HangingPlacement.CODEC, "placement", HangingPlacement.FREE)
                    .forGetter(HangingConfig::placement),
            HangingVariant.CODEC.listOf().fieldOf("variants").forGetter(HangingConfig::variants),
            Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "chain", DEFAULT_CHAIN)
                    .forGetter(HangingConfig::chain),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_drop", 0)
                    .forGetter(HangingConfig::minDrop),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_drop", 1)
                    .forGetter(HangingConfig::maxDrop),
            SizeGate.MAP_CODEC.forGetter(HangingConfig::gate)
    ).apply(instance, HangingConfig::new));

    public static final Codec<HangingConfig> CODEC = Codecs.closed(MAP_CODEC);

    /** This slot with its variants' blocks and its chain resolved against the palette in scope. */
    public HangingConfig withRoles(UnaryOperator<String> resolver) {
        List<HangingVariant> resolved = null;
        for (int i = 0; i < variants.size(); i++) {
            HangingVariant variant = variants.get(i);
            String block = Codecs.resolveRole(variant.block(), resolver);
            if (block.equals(variant.block())) {
                if (resolved != null) {
                    resolved.add(variant);
                }
                continue;
            }
            if (resolved == null) {
                resolved = new ArrayList<>(variants.subList(0, i));
            }
            resolved.add(new HangingVariant(block, variant.weight()));
        }
        String newChain = Codecs.resolveRole(chain, resolver);
        if (resolved == null && newChain.equals(chain)) {
            return this;
        }
        return new HangingConfig(minCount, maxCount, placement,
                resolved == null ? variants : List.copyOf(resolved), newChain, minDrop, maxDrop, gate);
    }

    /** See {@code PropConfig#clampedMaxCount}. */
    public int clampedMaxCount() {
        return Math.max(minCount, maxCount);
    }

    /** The drop range's upper bound, normalised the same way. */
    public int clampedMaxDrop() {
        return Math.max(minDrop, maxDrop);
    }
}
