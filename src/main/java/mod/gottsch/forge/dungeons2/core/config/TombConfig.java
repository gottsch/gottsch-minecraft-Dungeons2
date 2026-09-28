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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The {@code tombs} scheme slot (#104): how many sealed sarcophagi a room gets, which block they
 * are, and &mdash; optionally &mdash; what they hold.
 *
 * <h2>A tomb is two blocks, and stands like a bed</h2>
 * <p>{@code dungeonblocks}' sarcophagus is a HEAD and a FOOT. Its head backs onto a wall and its
 * foot points into the room, which is how a crypt reads &mdash; rows of the dead along the walls with
 * the aisle between them. That is the only placement there is, so there is no {@code placement} key
 * to author; a second one (free-standing, on a dais) would be a new key with this as its default.
 * {@code RoomTombGenerator} has the rest: the gap it keeps between tombs, and the doorways.</p>
 *
 * <h2>Contents</h2>
 * <p>Every {@link TombContents} key is optional here and overrides the motif's
 * {@link TombBand} for this depth on its own, so a scheme can change the odds and still take the
 * floor's dead and loot. A slot that resolves to nothing it could release places no tomb at all,
 * for the empty chest's reason.</p>
 *
 * <p>{@code variants[].block} takes a {@code $role} &mdash; the shipped schemes say
 * {@code $sarcophagus}, which is stone above and deepslate in the deepslate band.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
public record TombConfig(int minCount, int maxCount, List<TombVariant> variants,
                         TombContents contents, SizeGate gate) {

    /** Ungated, and deferring every content key to the motif's band. */
    public TombConfig(int minCount, int maxCount, List<TombVariant> variants) {
        this(minCount, maxCount, variants, TombContents.NONE, SizeGate.UNBOUNDED);
    }

    /**
     * One weighted tomb block. It must be a two-block, bed-shaped block &mdash; {@code part} and
     * {@code facing} &mdash; with DungeonBlocks' sarcophagus block entity; nothing at load can check
     * that, so {@code ShippedTombsTest} sweeps what ships.
     */
    public record TombVariant(String block, int weight) {
        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<TombVariant> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(TombVariant::block),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(TombVariant::weight)
        ).apply(instance, TombVariant::new)));
    }

    /**
     * Open, for {@link SlotOptions}: an option writes a {@code weight} key alongside these, so the
     * closed check is re-imposed one level up over the union of both.
     */
    public static final MapCodec<TombConfig> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
            // 0/1, the chests' defaults and for the chests' reason: a tomb is an encounter, and a
            // room that always has one is a room where finding one means nothing.
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_count", 0)
                    .forGetter(TombConfig::minCount),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_count", 1)
                    .forGetter(TombConfig::maxCount),
            TombVariant.CODEC.listOf().fieldOf("variants").forGetter(TombConfig::variants),
            TombContents.MAP_CODEC.forGetter(TombConfig::contents),
            SizeGate.MAP_CODEC.forGetter(TombConfig::gate)
    ).apply(instance, TombConfig::new));

    public static final Codec<TombConfig> CODEC = Codecs.closed(MAP_CODEC);

    /** This slot with every content key it does not state taken from the depth's band. */
    public TombConfig resolvedAgainst(Optional<TombBand> band) {
        return band.map(b -> new TombConfig(minCount, maxCount, variants,
                        contents.over(b.contents()), gate))
                .orElse(this);
    }

    /** This slot with each variant's block resolved; {@code this} when none names a role. */
    public TombConfig withRoles(UnaryOperator<String> resolver) {
        List<TombVariant> resolved = null;
        for (int i = 0; i < variants.size(); i++) {
            TombVariant variant = variants.get(i);
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
            resolved.add(new TombVariant(block, variant.weight()));
        }
        return resolved == null ? this
                : new TombConfig(minCount, maxCount, List.copyOf(resolved), contents, gate);
    }

    /** The inclusive count range, normalised; see {@code ChestConfig#clampedMaxCount}. */
    public int clampedMaxCount() {
        return Math.max(minCount, maxCount);
    }
}
