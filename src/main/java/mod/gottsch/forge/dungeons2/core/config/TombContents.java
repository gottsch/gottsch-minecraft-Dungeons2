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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a sealed tomb holds (#104): the loot it may spill, the guardian it may raise, and the
 * weights of the one roll DungeonBlocks makes between them.
 *
 * <h2>Dungeons2 writes the odds, DungeonBlocks rolls them</h2>
 * <p>A {@code dungeonblocks} sarcophagus rolls ONCE, on its first opening, between its loot, its
 * guardian and nothing &mdash; never both &mdash; and is empty for good afterwards. So nothing here
 * decides the outcome. What is drawn at generation is only <em>which</em> table and <em>which</em>
 * mob this tomb would release, one of each, per tomb; the weights are written through untouched.
 * Mark's call (2026-09-26), and the trade it makes is stated rather than hidden: the loot seed
 * fixes what the loot is, but not whether the tomb pays loot at all, so reloading a save before
 * opening one re-rolls that.</p>
 *
 * <h2>Every field is optional, for one reason: this record is also an override</h2>
 * <p>The same five keys are authored in three places &mdash; a motif's {@link TombBand}s, a
 * scheme's {@code tombs} slot, and the {@code dungeons2:sarcophagus} processor entry &mdash; and in
 * the second one each key overrides the band <em>on its own</em>, so a crypt can restate its weights
 * and still take the floor's loot. {@link #over} is that rule, once. An {@code int} with a default
 * cannot say "the author wrote nothing", which is the {@code MobSetBand} argument again.</p>
 *
 * <p>An absent weight is <strong>not written</strong>, and DungeonBlocks' own default then applies
 * (loot 1, guardian 1, empty 0). Loot and the guardian only count when this names them, exactly as
 * on the block.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
public record TombContents(Optional<List<ChestConfig.LootTableEntry>> lootTables,
                           Optional<List<GuardianEntry>> guardians,
                           Optional<Integer> lootWeight, Optional<Integer> guardianWeight,
                           Optional<Integer> emptyWeight) {

    /** States nothing: every field is left to whatever this is laid over. */
    public static final TombContents NONE = new TombContents(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());

    /** DungeonBlocks' own defaults, for the arithmetic in {@link #releasesAnything}. */
    static final int BLOCK_LOOT_WEIGHT = 1;
    static final int BLOCK_GUARDIAN_WEIGHT = 1;

    /**
     * One weighted guardian: an entity id. The same shape as a pot variant's {@code entity}, and not
     * a mob SET: a tomb raises exactly one mob, and a set's weights and counts would both be read
     * wrongly here. Swept against the registered entities by {@code ShippedTombsTest}, since nothing
     * at load can resolve an entity id.
     */
    public record GuardianEntry(String entity, int weight) {
        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<GuardianEntry> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("entity").forGetter(GuardianEntry::entity),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(GuardianEntry::weight)
        ).apply(instance, GuardianEntry::new)));

        /** Weighted draw, or {@code null} for an empty list. Mirrors {@code LootTableEntry#pick}. */
        public static String pick(List<GuardianEntry> guardians, RandomSource random) {
            if (guardians.isEmpty()) {
                return null;
            }
            int roll = random.nextInt(guardians.stream().mapToInt(GuardianEntry::weight).sum());
            for (GuardianEntry entry : guardians) {
                roll -= entry.weight();
                if (roll < 0) {
                    return entry.entity();
                }
            }
            return guardians.get(guardians.size() - 1).entity();
        }
    }

    /**
     * The five keys, flat on whatever embeds them. Open, for {@link SlotOptions}'s reason: the
     * enclosing record closes the schema over the union of its keys and these.
     */
    public static final MapCodec<TombContents> MAP_CODEC = RecordCodecBuilder.<TombContents>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(ChestConfig.LootTableEntry.CODEC.listOf(), "loot_tables")
                    .forGetter(TombContents::lootTables),
            Codecs.strictOptionalFieldOf(GuardianEntry.CODEC.listOf(), "guardians")
                    .forGetter(TombContents::guardians),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "loot_weight")
                    .forGetter(TombContents::lootWeight),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "guardian_weight")
                    .forGetter(TombContents::guardianWeight),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "empty_weight")
                    .forGetter(TombContents::emptyWeight)
    ).apply(instance, TombContents::new)).flatXmap(TombContents::validateLists, TombContents::validateLists);

    /**
     * A present-but-empty list is a load error. It reads as "override the floor's loot with none",
     * which is a real thing to want and is said with {@code loot_weight: 0} &mdash; an empty list
     * would say it silently and look exactly like a list someone forgot to fill.
     */
    private static DataResult<TombContents> validateLists(TombContents contents) {
        if (contents.lootTables.map(List::isEmpty).orElse(false)) {
            return DataResult.error(() -> "tomb contents: 'loot_tables' is empty. To keep a tomb"
                    + " from paying loot, set loot_weight to 0 instead");
        }
        if (contents.guardians.map(List::isEmpty).orElse(false)) {
            return DataResult.error(() -> "tomb contents: 'guardians' is empty. To keep a tomb"
                    + " from raising a guardian, set guardian_weight to 0 instead");
        }
        return DataResult.success(contents);
    }

    /** Every field this states, with each field it does not state taken from {@code under}. */
    public TombContents over(TombContents under) {
        return new TombContents(
                lootTables.isPresent() ? lootTables : under.lootTables,
                guardians.isPresent() ? guardians : under.guardians,
                lootWeight.isPresent() ? lootWeight : under.lootWeight,
                guardianWeight.isPresent() ? guardianWeight : under.guardianWeight,
                emptyWeight.isPresent() ? emptyWeight : under.emptyWeight);
    }

    /**
     * Whether a tomb drawn from this can ever release anything &mdash; loot or a guardian with a
     * non-zero share of the roll. False means every opening comes up empty, and a tomb like that is
     * placed by neither route: an inert tomb is scenery pretending to be an encounter, the tomb
     * equivalent of the empty chest {@code RoomChestGenerator} refuses to place.
     */
    public boolean releasesAnything() {
        int loot = lootTables.isPresent() ? lootWeight.orElse(BLOCK_LOOT_WEIGHT) : 0;
        int guardian = guardians.isPresent() ? guardianWeight.orElse(BLOCK_GUARDIAN_WEIGHT) : 0;
        return loot > 0 || guardian > 0;
    }

    /**
     * One tomb's contents: a table and a mob drawn from the lists, and the weights as stated.
     *
     * <p>Draws in a fixed order &mdash; table, then its seed, then the guardian &mdash; and only for
     * what is named, so a tomb with no guardians consumes no guardian draw. Callers hand in the
     * room's own seeded random (procedural) or one seeded from the marker's position (authored).</p>
     */
    public Drawn draw(RandomSource random) {
        Optional<String> table = lootTables.map(list -> ChestConfig.LootTableEntry.pick(list, random));
        // Non-zero, for the reason RoomChestGenerator#chestData gives: 0 means "roll fresh".
        long seed = table.isPresent() ? nonZero(random.nextLong()) : 0L;
        Optional<String> guardian = guardians.map(list -> GuardianEntry.pick(list, random));
        return new Drawn(table, seed, guardian, lootWeight, guardianWeight, emptyWeight);
    }

    private static long nonZero(long seed) {
        return seed == 0L ? 1L : seed;
    }

    /**
     * What one tomb was given, in DungeonBlocks' own NBT vocabulary.
     *
     * <p>{@link #fields} is the ONE place the keys and their values are written; the procedural
     * route stringifies it into {@code BlockEntityData} and the marker route types it into a
     * {@link CompoundTag}. Two encodings, one source, so the two routes cannot disagree about what a
     * tomb holds &mdash; the drift {@code SpawnerTagParityTest} exists to catch for the spawners,
     * made impossible here instead.</p>
     */
    public record Drawn(Optional<String> lootTable, long lootSeed, Optional<String> guardian,
                        Optional<Integer> lootWeight, Optional<Integer> guardianWeight,
                        Optional<Integer> emptyWeight) {

        public static final String LOOT_TABLE = "LootTable";
        public static final String LOOT_TABLE_SEED = "LootTableSeed";
        public static final String GUARDIAN = "Guardian";
        public static final String LOOT_WEIGHT = "LootWeight";
        public static final String GUARDIAN_WEIGHT = "GuardianWeight";
        public static final String EMPTY_WEIGHT = "EmptyWeight";

        /** Whether the tomb holds anything for its first opening; DungeonBlocks' own test. */
        public boolean isSealed() {
            return lootTable.isPresent() || guardian.isPresent();
        }

        /** {@link TombContents#releasesAnything}, for one drawn tomb. */
        public boolean releasesAnything() {
            int loot = lootTable.isPresent() ? lootWeight.orElse(BLOCK_LOOT_WEIGHT) : 0;
            int mob = guardian.isPresent() ? guardianWeight.orElse(BLOCK_GUARDIAN_WEIGHT) : 0;
            return loot > 0 || mob > 0;
        }

        /**
         * The tomb's NBT, key to value: {@code String}, {@code Long} or {@code Integer}. A weight is
         * written only beside the thing it weighs &mdash; a loot weight on a tomb with no table would
         * be read by nothing &mdash; and the empty weight whenever it was stated.
         */
        public Map<String, Object> fields() {
            Map<String, Object> out = new LinkedHashMap<>();
            lootTable.ifPresent(table -> {
                out.put(LOOT_TABLE, table);
                out.put(LOOT_TABLE_SEED, lootSeed);
                lootWeight.ifPresent(weight -> out.put(LOOT_WEIGHT, weight));
            });
            guardian.ifPresent(mob -> {
                out.put(GUARDIAN, mob);
                guardianWeight.ifPresent(weight -> out.put(GUARDIAN_WEIGHT, weight));
            });
            emptyWeight.ifPresent(weight -> out.put(EMPTY_WEIGHT, weight));
            return out;
        }

        /** {@link #fields} as typed NBT, for the marker route. */
        public CompoundTag tag() {
            CompoundTag tag = new CompoundTag();
            fields().forEach((key, value) -> {
                if (value instanceof Long l) {
                    tag.putLong(key, l);
                } else if (value instanceof Integer i) {
                    tag.putInt(key, i);
                } else {
                    tag.putString(key, value.toString());
                }
            });
            return tag;
        }

        /** One line for a log: what this tomb could release, and the odds. */
        public String describe() {
            return describe(fields());
        }

        /**
         * {@link #describe} from the written fields, however they are typed &mdash; so the
         * procedural probe, which only has the placement's stringified data by the time it knows the
         * world position, prints exactly what the marker route prints. A dash is a weight not
         * written, which DungeonBlocks reads as its own default.
         */
        public static String describe(Map<String, ?> fields) {
            return "loot " + field(fields, LOOT_TABLE, "none")
                    + ", guardian " + field(fields, GUARDIAN, "none")
                    + ", weights " + field(fields, LOOT_WEIGHT, "-") + "/"
                    + field(fields, GUARDIAN_WEIGHT, "-") + "/" + field(fields, EMPTY_WEIGHT, "-");
        }

        private static String field(Map<String, ?> fields, String key, String absent) {
            Object value = fields.get(key);
            return value == null ? absent : value.toString();
        }
    }
}
