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
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * A {@link RoomScheme}'s {@code spawners} slot: how many invisible mob-set spawners a procedural
 * room gets, which {@code mob_sets} they draw from, and the tuning the block entity needs.
 *
 * <h2>Why a scheme slot at all</h2>
 * <p>Backlog #10 shipped the spawner as a marker <em>block</em> converted by
 * {@code SpawnerMarkerProcessor} &mdash; which only an <strong>authored template</strong> can
 * contain. Procedural rooms, which are most of a dungeon, had no way to place one. This slot is the
 * procedural half: the room generator emits a {@code BlockPlacement} carrying
 * {@link mod.gottsch.forge.dungeons2.core.data.BlockEntityData}, and
 * {@code DungeonPiece.applyBlockEntity} &mdash; wired since Phase 3 and until now dormant, because
 * nothing emitted it &mdash; loads it into the block entity. Both halves converge on the same tag,
 * so a spawner from a scheme and a spawner from a marker are indistinguishable in the world.</p>
 *
 * <h2>The mob set is chosen HERE, at generation time</h2>
 * <p>{@code ProximityMobSetSpawnerBlockEntity} can also take a {@code mobSetNames} <em>list</em> and
 * defer the pick to trigger time. This slot deliberately does not use it, for two reasons: the tag
 * travels as stringified key/values (see {@code BlockEntityData}) and cannot express a list at all;
 * and a weighted roll made here is a pure function of the piece's chunk-independent seed, which is
 * the property every other procedural decision in this mod is built on. A pick made when the player
 * walks in would make the same dungeon spawn different things on a revisit.</p>
 *
 * <h2>Content is not validated here</h2>
 * <p>A named set that does not exist is a build failure via {@code ShippedMobSetsTest}, not a load
 * error: {@code MobSetDataRegistry} is filled from datapacks at reload while a room is built during
 * worldgen, so at that point "not loaded yet" and "does not exist" are indistinguishable &mdash; the
 * same split {@code SpawnerMarkerProcessor} documents.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public record SpawnerConfig(int minCount, int maxCount, Optional<Integer> minMobs,
                            Optional<Integer> maxMobs, double proximity,
                            Optional<List<MobSetEntry>> mobSets, SizeGate gate) {

    /** The tuning defaults, spelled the same as {@code SpawnerMarkerProcessor}'s so the two agree. */
    public static final int DEFAULT_MIN_MOBS = 1;
    public static final int DEFAULT_MAX_MOBS = 3;
    public static final double DEFAULT_PROXIMITY = 8.0D;

    /** Ungated spawners with explicit counts -- placed whenever the scheme is rolled. */
    public SpawnerConfig(int minCount, int maxCount, int minMobs, int maxMobs, double proximity,
                         List<MobSetEntry> mobSets) {
        this(minCount, maxCount, Optional.of(minMobs), Optional.of(maxMobs), proximity,
                Optional.of(mobSets), SizeGate.UNBOUNDED);
    }

    /**
     * One spawner drawing from one set, stating no counts -- the common authoring case.
     *
     * <p>Counts are left <em>absent</em> rather than set to the defaults, which is the difference
     * between "whatever this depth calls for" and "one to three, at every depth". The resolved
     * values are the same until a band says otherwise, and that is exactly the point.</p>
     */
    public SpawnerConfig(String mobSet) {
        this(1, 1, Optional.empty(), Optional.empty(), DEFAULT_PROXIMITY,
                Optional.of(List.of(new MobSetEntry(mobSet, 1))), SizeGate.UNBOUNDED);
    }

    /**
     * This config with its mob sets resolved: its own if it names any, otherwise the motif's
     * {@code mobSetsByFloorIndex} band for the floor being built.
     *
     * <p><strong>Absent and empty are different things here, deliberately.</strong> A scheme that
     * omits {@code mobSets} is saying "whatever this depth calls for", which is the case that lets
     * one scheme be reused at every depth and get harder as it goes. A scheme that writes
     * {@code "mobSets": []} is saying nothing at all, and is rejected at load &mdash; which is only
     * expressible because the field is an {@link Optional} rather than a list defaulting to empty.
     * That distinction is the whole reason the override works.</p>
     *
     * <p>Resolution happens here, at build time, rather than being stored on the floor by the
     * planner: it is a pure function of (motif, floorIndex) and both are already in hand where a
     * room is built, so plan-time storage would buy nothing and add a field to serialise. The
     * <em>caps</em> of backlog #44 are the opposite case &mdash; they are stateful across rooms and
     * genuinely cannot be decided here.</p>
     */
    public SpawnerConfig resolvedAgainst(Optional<MobSetBand> band) {
        if (band.isEmpty()) {
            return this;
        }
        MobSetBand b = band.get();
        return new SpawnerConfig(minCount, maxCount,
                minMobs.or(b::minMobs),
                maxMobs.or(b::maxMobs),
                proximity,
                mobSets.isPresent() ? mobSets : Optional.of(b.mobSets()),
                gate);
    }

    /**
     * The sets-only form, for callers that have a floor's mob sets but no band.
     *
     * <p>Kept because it is what the pre-band tests exercise, and because the two forms genuinely
     * differ: this one cannot carry counts, so it leaves them to the scheme and the defaults.</p>
     */
    public SpawnerConfig resolvedAgainst(List<MobSetEntry> floorSets) {
        if (mobSets.isPresent()) {
            return this;
        }
        return new SpawnerConfig(minCount, maxCount, minMobs, maxMobs, proximity,
                Optional.of(floorSets), gate);
    }

    /** The sets this config names outright, or empty when it defers to the motif's depth table. */
    public List<MobSetEntry> declaredMobSets() {
        return mobSets.orElseGet(List::of);
    }

    /**
     * One weighted {@code mob_sets} id. A record rather than a bare id list so a motif can say
     * "mostly vermin, occasionally something worse" without repeating ids &mdash; the same shape
     * {@code PotConfig.PotVariant} uses, and for the same reason.
     */
    public record MobSetEntry(String mobSet, int weight) {
        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<MobSetEntry> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("mobSet").forGetter(MobSetEntry::mobSet),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(MobSetEntry::weight)
        ).apply(instance, MobSetEntry::new)));
    }

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<SpawnerConfig> CODEC = Codecs.closed(RecordCodecBuilder.<SpawnerConfig>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "minCount", 1)
                    .forGetter(SpawnerConfig::minCount),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "maxCount", 1)
                    .forGetter(SpawnerConfig::maxCount),
            // Absent, not defaulted: a scheme that states no count defers to the motif's depth
            // band, and only falls back to DEFAULT_MIN_MOBS/DEFAULT_MAX_MOBS if no band speaks
            // either. A defaulted int could not tell those two cases apart. See MobSetBand.
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "minMobs")
                    .forGetter(SpawnerConfig::minMobs),
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "maxMobs")
                    .forGetter(SpawnerConfig::maxMobs),
            // A proximity of 0 is not "trigger on contact": the block entity clamps the squared
            // distance up to 1.0, so it would fire only when the player stands in the cell -- an
            // invisible block in an open room, which reads as a spawner that never works.
            Codecs.strictOptionalFieldOf(Codec.doubleRange(1.0D, 64.0D), "proximity", DEFAULT_PROXIMITY)
                    .forGetter(SpawnerConfig::proximity),
            // Optional, and absent means "use the motif's mobSetsByFloorIndex band for this floor".
            // See resolvedAgainst for why absent and empty must stay distinguishable.
            Codecs.strictOptionalFieldOf(MobSetEntry.CODEC.listOf(), "mobSets")
                    .forGetter(SpawnerConfig::mobSets),
            SizeGate.MAP_CODEC.forGetter(SpawnerConfig::gate)
    ).apply(instance, SpawnerConfig::new))).flatXmap(SpawnerConfig::validate, SpawnerConfig::validate);

    /**
     * Rejects a slot that can only ever produce a spawner spawning nothing.
     *
     * <p>An empty {@code mobSets} is the whole of it, and it is worth a load error rather than a
     * silent skip for the reason every check around this feature exists: a spawner is invisible, so
     * one that was configured to draw from nothing is indistinguishable in game from one that was
     * never placed. The count range is <em>clamped</em> instead (see {@link #clampedMaxCount}),
     * matching {@code PotConfig} -- an inverted count is nonsense but not ambiguous.</p>
     */
    private static DataResult<SpawnerConfig> validate(SpawnerConfig config) {
        if (config.mobSets.isPresent() && config.mobSets.get().isEmpty()) {
            return DataResult.error(() -> "spawners slot: 'mobSets' is present but empty. Omit the"
                    + " key entirely to draw from the motif's mobSetsByFloorIndex table for the"
                    + " floor; an empty list places invisible blocks that spawn nothing");
        }
        return DataResult.success(config);
    }

    /** The inclusive spawner-count range, normalised. See {@code PotConfig#clampedMaxCount}. */
    public int clampedMaxCount() {
        return Math.max(minCount, maxCount);
    }

    /**
     * The mobs-per-spawn floor actually written to the block entity: this slot's own value, or the
     * built-in default when neither the scheme nor the floor's band stated one.
     *
     * <p>Call this rather than {@link #minMobs()} anywhere a number is needed &mdash; the record
     * component answers "what did the author write", which is a different question.</p>
     */
    public int effectiveMinMobs() {
        return minMobs.orElse(DEFAULT_MIN_MOBS);
    }

    /** The inclusive mobs-per-spawn range, normalised the same way. */
    public int clampedMaxMobs() {
        return Math.max(effectiveMinMobs(), maxMobs.orElse(DEFAULT_MAX_MOBS));
    }
}
