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
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

/**
 * The {@code chests} scheme slot: how many chests a room gets, which block they are, and what they
 * hold. Backlog #48, the procedural route.
 *
 * <h2>These are blocks with block-entity data, like the spawner slot &mdash; not entities</h2>
 * <p>Pots are the odd one out ({@code PotEntity} instances with per-entity loot tables); a chest is
 * an ordinary block whose {@code LootTable}/{@code LootTableSeed} ride along in
 * {@link mod.gottsch.forge.dungeons2.core.data.BlockEntityData}, exactly as the proximity spawner's
 * fields do. That path has carried the chest shape in its documentation since it was written and has
 * never had a caller; this is the caller.</p>
 *
 * <h2>Loot</h2>
 * <p>{@code loot_tables} is optional <em>here</em> because the motif's {@link ChestLootBand} table
 * supplies it by depth; a scheme naming its own wins. What is not optional is that <strong>something</strong>
 * supplies one: a chest with no table resolves to no chest at all, because an empty chest in a
 * dungeon reads as a bug that has already eaten the player's time by the time they notice.</p>
 *
 * <p>Unlike a pot's, this must be a <strong>{@code "type": "minecraft:chest"}</strong> table &mdash;
 * the block's own unpack path builds {@code LootParams} with the ORIGIN parameter, not ENTITY. Each
 * chest gets a non-zero {@code LootTableSeed}, so its contents are fixed when the dungeon generates
 * rather than rolled when a player opens it. That is what vanilla does for a structure chest, and it
 * is what stops a player re-rolling the same chest by reloading a save.</p>
 *
 * <h2>What is deliberately NOT here yet</h2>
 * <p>No Treasure2 opt-in. That is a <em>per marker</em> decision on the authored route (#48 step 3),
 * because the case for it is the boss chest &mdash; a specific chest in a specific room, not a
 * property of every chest a scheme happens to roll. A procedural slot has no way to say "this one".</p>
 *
 * <p>Depth tiering IS here as of #48 step 2: see {@link ChestLootBand}, which follows
 * {@code MobSetBand}'s shape rather than inventing a second one.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public record ChestConfig(int minCount, int maxCount, Optional<List<LootTableEntry>> lootTables,
                         List<ChestVariant> variants, SizeGate gate) {

    /** Ungated -- placed whenever the scheme is rolled. */
    public ChestConfig(int minCount, int maxCount, Optional<List<LootTableEntry>> lootTables,
                       List<ChestVariant> variants) {
        this(minCount, maxCount, lootTables, variants, SizeGate.UNBOUNDED);
    }

    /** The single-table form, for a scheme (or a test) naming one table outright. */
    public ChestConfig(int minCount, int maxCount, String lootTable, List<ChestVariant> variants) {
        this(minCount, maxCount, lootTable, variants, SizeGate.UNBOUNDED);
    }

    /** The single-table form, gated. */
    public ChestConfig(int minCount, int maxCount, String lootTable, List<ChestVariant> variants,
                       SizeGate gate) {
        this(minCount, maxCount, Optional.of(List.of(new LootTableEntry(lootTable, 1))), variants,
                gate);
    }

    /**
     * This config with the motif's depth table filled in where the scheme said nothing.
     *
     * <p>The scheme wins when it names its own tables: a treasury room is a treasury at every depth,
     * and that is the whole reason the field is an {@link Optional} rather than a defaulted value
     * &mdash; a default cannot tell "the author named this table" from "the author said nothing".
     * The same argument, and the same shape, as {@code SpawnerConfig#resolvedAgainst}.</p>
     */
    public ChestConfig resolvedAgainst(Optional<ChestLootBand> band) {
        if (band.isEmpty() || lootTables.isPresent()) {
            return this;
        }
        return new ChestConfig(minCount, maxCount, Optional.of(band.get().lootTables()), variants,
                gate);
    }

    /**
     * The tables this config names outright, or empty when it defers to a motif depth table that
     * had nothing to offer either.
     *
     * <p>Empty means <strong>place no chest</strong>, not "place an empty one". The spawner slot
     * makes the same call for an unresolvable mob set, and for the same reason: the thing with no
     * contents is worse than the absence of the thing, because the player pays a walk to find out.</p>
     */
    public List<LootTableEntry> declaredLootTables() {
        return lootTables.orElseGet(List::of);
    }

    /**
     * One weighted loot table. Weighted rather than a single id so a floor can be "usually the
     * common table, occasionally something better" without needing a second band &mdash; the same
     * shape, and the same argument, as {@code SpawnerConfig.MobSetEntry}.
     */
    public record LootTableEntry(String lootTable, int weight) {
        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<LootTableEntry> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("loot_table").forGetter(LootTableEntry::lootTable),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(LootTableEntry::weight)
        ).apply(instance, LootTableEntry::new)));

        /**
         * Weighted draw over a list of these, or {@code null} for an empty list.
         *
         * <p>Lives here rather than in either caller because there are now two: the procedural
         * route ({@code RoomChestGenerator}) draws from the scheme's list resolved against the
         * motif's depth band, and the authored route ({@code ChestMarkerProcessor}) draws from its
         * processor entry's. Same vocabulary, same arithmetic; a second copy is how the two would
         * come to disagree about what a weight means.</p>
         *
         * <p><strong>Drawn per chest, not once per placement.</strong> A list that is "mostly the
         * common table, occasionally something better" must not turn a two-chest room into two rare
         * chests on one roll &mdash; the caller passes a random seeded per position for exactly
         * that reason.</p>
         */
        public static String pick(List<LootTableEntry> tables, RandomSource random) {
            if (tables.isEmpty()) {
                return null;
            }
            int totalWeight = tables.stream().mapToInt(LootTableEntry::weight).sum();
            int roll = random.nextInt(totalWeight);
            for (LootTableEntry entry : tables) {
                roll -= entry.weight();
                if (roll < 0) {
                    return entry.lootTable();
                }
            }
            return tables.get(tables.size() - 1).lootTable();
        }
    }

    /**
     * One weighted chest block. A record rather than a bare id list so a motif can say "usually a
     * chest, occasionally a barrel" without repeating ids.
     *
     * <p>The block must have a chest-shaped block entity &mdash; something that reads
     * {@code LootTable} from its NBT. {@code minecraft:chest} and {@code minecraft:barrel} both do.
     * Nothing here can check that: a block id is a string to this codec, and the registry is not
     * available at load time. A block that has no such entity places quietly and holds nothing,
     * which is the one failure this slot cannot turn into a load error.</p>
     */
    public record ChestVariant(String block, int weight) {
        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<ChestVariant> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(ChestVariant::block),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(ChestVariant::weight)
        ).apply(instance, ChestVariant::new)));
    }

    // Codecs.closed -- see RoomScheme.CODEC.
        /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<ChestConfig> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
            // Defaults of 0/1, not the pots' 1/3: a chest is a reward, and a room that always has
            // one is a room where finding one means nothing. An author who wants a guaranteed chest
            // says so.
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_count", 0)
                    .forGetter(ChestConfig::minCount),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_count", 1)
                    .forGetter(ChestConfig::maxCount),
            // Optional, and absent means "take the motif's depth table" -- see resolvedAgainst.
            // A required field here would force every scheme to restate loot it has no opinion on,
            // which is exactly what the depth axis exists to avoid.
            Codecs.strictOptionalFieldOf(LootTableEntry.CODEC.listOf(), "loot_tables")
                    .forGetter(ChestConfig::lootTables),
            ChestVariant.CODEC.listOf().fieldOf("variants").forGetter(ChestConfig::variants),
            SizeGate.MAP_CODEC.forGetter(ChestConfig::gate)
    ).apply(instance, ChestConfig::new));

    public static final Codec<ChestConfig> CODEC = Codecs.closed(MAP_CODEC);

    /**
     * This slot with each variant's chest block resolved. #65 phase 6, and the last of the element
     * slots.
     *
     * <p>Only {@code variants[].block} is a block. {@code loot_table} names a loot table, and
     * {@code LootTableEntry} is shared with {@link ChestLootBand} and the chest marker processor
     * &mdash; a role there would be a different feature against a different registry, and is not
     * this one.</p>
     */
    public ChestConfig withRoles(java.util.function.UnaryOperator<String> resolver) {
        List<ChestVariant> resolved = null;
        for (int i = 0; i < variants.size(); i++) {
            ChestVariant variant = variants.get(i);
            String block = Codecs.resolveRole(variant.block(), resolver);
            if (block.equals(variant.block())) {
                if (resolved != null) {
                    resolved.add(variant);
                }
                continue;
            }
            if (resolved == null) {
                resolved = new java.util.ArrayList<>(variants.subList(0, i));
            }
            resolved.add(new ChestVariant(block, variant.weight()));
        }
        return resolved == null ? this
                : new ChestConfig(minCount, maxCount, lootTables, List.copyOf(resolved), gate);
    }

    /**
     * The inclusive count range, normalised. A {@code max_count} below {@code min_count} is authoring
     * nonsense a codec range cannot express (the bound is another field), so it is clamped here
     * rather than producing an empty or negative range at generation time. Same treatment, and same
     * reasoning, as {@code PotConfig#clampedMaxCount}.
     */
    public int clampedMaxCount() {
        return Math.max(minCount, maxCount);
    }
}
