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

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.OptionalInt;

/**
 * One depth band of the motif's {@code mob_sets_by_floor_index} table: the mob sets a dungeon's spawners
 * draw from from this floor down, until the next band takes over.
 *
 * <h2>Bands are open-ended downward, and that is the design</h2>
 * <p>A band declares only where it <em>starts</em>. Band <em>n</em> runs from its
 * {@link #minFloorIndex} until band <em>n+1</em> begins, and the last band runs forever. The
 * alternative &mdash; a min and a max on each &mdash; can leave a floor covered by nothing, and the
 * consequence of that is not a visible hole but a silently disarmed spawner on every room of that
 * floor. Open-ended bands make the gap <strong>unrepresentable</strong> rather than something a test
 * has to go looking for; the one remaining requirement, that some band covers floor 0, is a single
 * load-time check ({@link #validate}).</p>
 *
 * <h2>floorIndex, not floorY</h2>
 * <p>{@code min_floor_index} counts floors from the entrance: <strong>0 is the entrance floor</strong>,
 * 1 the one below it, and so on. It is deliberately not a world Y. A dungeon under a mountain has its
 * third floor higher than a ravine dungeon's first, so a Y threshold would make "deep" mean something
 * different per dungeon &mdash; whereas an author writing {@code minFloorIndex: 3} means the fourth
 * floor down, every time. (Note that Stronger Mobs Below's own scaling <em>is</em> keyed on world Y;
 * the two axes coexist deliberately and answer different questions.)</p>
 *
 * <h2>The band may also ADD to how many mobs a spawner releases</h2>
 * <p>{@code bonus_mobs} is added to both ends of the drawn mob set's own {@code count}, so a deeper
 * floor can release more without taking the count away from the mob set. It used to be an absolute
 * {@code min_mobs}/{@code max_mobs} that replaced the set's count, which meant a modder editing a
 * mob set's {@code count} changed nothing on any floor a band covered &mdash; that is, every floor.
 * Additive keeps the mob set in charge of the base and the depth table in charge of the escalation.</p>
 *
 * <p><strong>Independent of {@code mob_sets}, deliberately.</strong> A scheme that names its own sets
 * still picks up the band's bonus, because "which mobs" and "how many more at this depth" are
 * separate authoring decisions. A scheme that states its own {@code min_mobs}/{@code max_mobs} is an
 * exact override and takes no bonus &mdash; see {@link MobRange}.</p>
 *
 * <h2>...and how HARD they are: {@code difficulty}</h2>
 * <p>The Enemy Echelons difficulty a mob spawned on these floors is set to, read only when the EE
 * API is installed. Either a number, or a weighted list
 * {@code [{"difficulty": 2, "weight": 3}, {"difficulty": 3, "weight": 1}]} drawn per mob. What a
 * step is worth is the motif's {@code echelon} block, not the band's &mdash; see
 * {@link EchelonConfig}.</p>
 *
 * <p><strong>Absent means "Dungeons2 has no opinion", not "difficulty 0".</strong> A mob from a
 * band with no {@code difficulty} is left alone, so if Stronger Mobs Below is installed it scales
 * that mob by world Y exactly as it would any other. Writing {@code 0} is the way to say "this depth
 * is unscaled, and SMB should keep its hands off too".</p>
 *
 * <p>Independent of the other two axes, for the same reason they are independent of each other: a
 * scheme that names its own mob sets still gets its floor's difficulty. It is resolved at SPAWN time
 * from the spawner's motif and floor, not baked at generation, so a datapack edit reaches spawners
 * already in the world.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public record MobSetBand(int minFloorIndex, List<SpawnerConfig.MobSetEntry> mobSets,
                         int bonusMobs, List<DifficultyEntry> difficulty) {

    /** The shape before {@code difficulty}: a band with no opinion on how hard its mobs are. */
    public MobSetBand(int minFloorIndex, List<SpawnerConfig.MobSetEntry> mobSets, int bonusMobs) {
        this(minFloorIndex, mobSets, bonusMobs, List.of());
    }

    /** A band that changes what spawns, not how many. */
    public MobSetBand(int minFloorIndex, List<SpawnerConfig.MobSetEntry> mobSets) {
        this(minFloorIndex, mobSets, 0);
    }

    /**
     * One weighted Enemy Echelons difficulty. Min 0 on the difficulty because EE treats 0 as "set,
     * and unscaled" and -1 as "not set yet", and an author has no business writing the latter.
     */
    public record DifficultyEntry(int difficulty, int weight) {
        public static final Codec<DifficultyEntry> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("difficulty").forGetter(DifficultyEntry::difficulty),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(DifficultyEntry::weight)
        ).apply(instance, DifficultyEntry::new)));
    }

    /**
     * A plain number or a weighted list. Written by hand rather than as {@code Codec.either} for
     * {@code SlotOptions.field}'s reason: {@code either} reports both branches' failures glued
     * together, so a typo inside a list entry would come back as a complaint about it not being a
     * number either.
     */
    static final Codec<List<DifficultyEntry>> DIFFICULTY_CODEC = new Codec<>() {
        private final Codec<List<DifficultyEntry>> list = DifficultyEntry.CODEC.listOf();

        @Override
        public <U> DataResult<Pair<List<DifficultyEntry>, U>> decode(DynamicOps<U> ops, U input) {
            if (ops.getStream(input).result().isPresent()) {
                return list.decode(ops, input).flatMap(pair -> pair.getFirst().isEmpty()
                        ? DataResult.error(() -> "'difficulty' is an empty list; omit the key to leave"
                                + " these floors' mobs to Stronger Mobs Below, or write 0 for unscaled")
                        : DataResult.success(pair));
            }
            return Codec.intRange(0, Integer.MAX_VALUE).decode(ops, input)
                    .map(pair -> Pair.of(List.of(new DifficultyEntry(pair.getFirst(), 1)), pair.getSecond()));
        }

        @Override
        public <U> DataResult<U> encode(List<DifficultyEntry> input, DynamicOps<U> ops, U prefix) {
            if (input.size() == 1 && input.get(0).weight() == 1) {
                return ops.mergeToPrimitive(prefix, ops.createInt(input.get(0).difficulty()));
            }
            return list.encode(input, ops, prefix);
        }
    };

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<MobSetBand> CODEC = Codecs.closed(RecordCodecBuilder.<MobSetBand>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_floor_index", 0)
                    .forGetter(MobSetBand::minFloorIndex),
            SpawnerConfig.MobSetEntry.CODEC.listOf().fieldOf("mob_sets").forGetter(MobSetBand::mobSets),
            // Min 0, not 1: a bonus of 0 is the ordinary "this depth adds nothing" band.
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "bonus_mobs", 0)
                    .forGetter(MobSetBand::bonusMobs),
            Codecs.strictOptionalFieldOf(DIFFICULTY_CODEC, "difficulty", List.of())
                    .forGetter(MobSetBand::difficulty)
    ).apply(instance, MobSetBand::new))).flatXmap(MobSetBand::validateBand, MobSetBand::validateBand);

    private static DataResult<MobSetBand> validateBand(MobSetBand band) {
        if (band.mobSets.isEmpty()) {
            return DataResult.error(() -> "mob set band at floor " + band.minFloorIndex
                    + ": 'mob_sets' is empty, so every spawner on those floors would be an invisible"
                    + " block that spawns nothing");
        }
        return DataResult.success(band);
    }

    /**
     * This band's Enemy Echelons difficulty for one mob, or empty when the band declares none.
     * Drawn per mob, so a weighted band gives a mixed pack rather than a uniform one.
     */
    public OptionalInt drawDifficulty(RandomSource random) {
        if (difficulty.isEmpty()) {
            return OptionalInt.empty();
        }
        int total = difficulty.stream().mapToInt(DifficultyEntry::weight).sum();
        int roll = random.nextInt(total);
        for (DifficultyEntry entry : difficulty) {
            roll -= entry.weight();
            if (roll < 0) {
                return OptionalInt.of(entry.difficulty());
            }
        }
        return OptionalInt.of(difficulty.get(difficulty.size() - 1).difficulty());
    }

    /**
     * The band covering {@code floorIndex}, or empty if the table is empty.
     *
     * <p>Reads the table backwards for the deepest band that has started. Linear, over a list an
     * author is realistically going to keep to a handful of entries, and called once per room
     * &mdash; not worth an index.</p>
     */
    public static java.util.Optional<MobSetBand> forFloor(List<MobSetBand> table, int floorIndex) {
        MobSetBand best = null;
        for (MobSetBand band : table) {
            if (band.minFloorIndex <= floorIndex
                    && (best == null || band.minFloorIndex > best.minFloorIndex)) {
                best = band;
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    /**
     * Rejects a table that cannot answer for every floor a dungeon can have.
     *
     * <p>Two faults, and the reasoning for each being an <em>error</em> rather than a repair is the
     * same one that runs through this whole feature: a spawner is invisible, so a floor the table
     * cannot answer for produces a dungeon that looks finished and is quietly empty.</p>
     *
     * <ul>
     *   <li><strong>No band covers floor 0.</strong> Floors below the lowest band are covered by
     *       construction; floors above it are not covered at all. Since the entrance floor is
     *       always index 0, requiring a band there is exactly equivalent to requiring full
     *       coverage &mdash; no sweep needed.</li>
     *   <li><strong>Two bands start on the same floor.</strong> One of them is dead, and which one
     *       depends on list order, which is not something an author should have to reason about.</li>
     * </ul>
     *
     * <p>An <em>empty</em> table is fine and means "this motif's schemes must name their own sets" —
     * see {@code SpawnerConfig}.</p>
     */
    public static DataResult<List<MobSetBand>> validate(List<MobSetBand> table) {
        if (table.isEmpty()) {
            return DataResult.success(table);
        }
        java.util.Set<Integer> starts = new java.util.HashSet<>();
        for (MobSetBand band : table) {
            if (!starts.add(band.minFloorIndex)) {
                return DataResult.error(() -> "mob_sets_by_floor_index: two bands both start at floor "
                        + band.minFloorIndex + ", so one of them can never be reached");
            }
        }
        if (!starts.contains(0)) {
            return DataResult.error(() -> "mob_sets_by_floor_index: no band covers floor 0 (the entrance"
                    + " floor), so its spawners would draw from nothing. Bands run from their"
                    + " min_floor_index downward, so the shallowest must start at 0. Found: " + starts);
        }
        return DataResult.success(table);
    }
}
