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
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

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
 * <p>{@code lootTable} is <strong>required</strong>, for the same reason {@code PotConfig} requires
 * it: a chest with no table is an empty chest, and an empty chest in a dungeon reads as a bug that
 * has already eaten the player's time by the time they notice. A required field turns that into a
 * load error.</p>
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
 * <p>No depth tiering either: {@code lootTable} is one table, not a table per floor band. #48 step 2
 * adds that, and it should follow {@code MobSetBand}'s shape rather than invent a second one.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public record ChestConfig(int minCount, int maxCount, String lootTable, List<ChestVariant> variants,
                          SizeGate gate) {

    /** Ungated -- placed whenever the scheme is rolled. */
    public ChestConfig(int minCount, int maxCount, String lootTable, List<ChestVariant> variants) {
        this(minCount, maxCount, lootTable, variants, SizeGate.UNBOUNDED);
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
                Codec.STRING.fieldOf("block").forGetter(ChestVariant::block),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(ChestVariant::weight)
        ).apply(instance, ChestVariant::new)));
    }

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<ChestConfig> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
            // Defaults of 0/1, not the pots' 1/3: a chest is a reward, and a room that always has
            // one is a room where finding one means nothing. An author who wants a guaranteed chest
            // says so.
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "minCount", 0)
                    .forGetter(ChestConfig::minCount),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "maxCount", 1)
                    .forGetter(ChestConfig::maxCount),
            Codec.STRING.fieldOf("lootTable").forGetter(ChestConfig::lootTable),
            ChestVariant.CODEC.listOf().fieldOf("variants").forGetter(ChestConfig::variants),
            SizeGate.MAP_CODEC.forGetter(ChestConfig::gate)
    ).apply(instance, ChestConfig::new)));

    /**
     * The inclusive count range, normalised. A {@code maxCount} below {@code minCount} is authoring
     * nonsense a codec range cannot express (the bound is another field), so it is clamped here
     * rather than producing an empty or negative range at generation time. Same treatment, and same
     * reasoning, as {@code PotConfig#clampedMaxCount}.
     */
    public int clampedMaxCount() {
        return Math.max(minCount, maxCount);
    }
}
