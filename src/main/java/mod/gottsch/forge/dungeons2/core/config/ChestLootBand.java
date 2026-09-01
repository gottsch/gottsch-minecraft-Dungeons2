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
 * Which loot tables a chest draws from at a given depth. Backlog #48 step 2, and deliberately the
 * same shape as {@link MobSetBand} rather than a second one.
 *
 * <h2>Why the motif owns this and not the scheme</h2>
 * <p>Exactly the argument the mob sets already make: a hall scheme should be authorable <em>once</em>
 * and get richer the deeper it is rolled, instead of needing a near-duplicate scheme per depth band.
 * A scheme that does name its own tables keeps them &mdash; see {@code ChestConfig#resolvedAgainst}
 * &mdash; so a treasury room can be a treasury on every floor.</p>
 *
 * <h2>Bands are open-ended downward</h2>
 * <p>A band starts at {@code min_floor_index} and runs until the next one starts, so the deepest band
 * covers every floor below it however deep a dungeon goes. That is what makes an uncovered floor
 * unrepresentable, which is the property that matters: the alternative &mdash; ranges with an upper
 * bound &mdash; lets an author leave floor 5 with no entry at all and get chests full of nothing
 * long after they stopped thinking about it. Same reasoning, same trap, as the depth axis for mobs.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public record ChestLootBand(int minFloorIndex, List<ChestConfig.LootTableEntry> lootTables) {

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<ChestLootBand> CODEC = Codecs.closed(RecordCodecBuilder.<ChestLootBand>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_floor_index", 0)
                    .forGetter(ChestLootBand::minFloorIndex),
            ChestConfig.LootTableEntry.CODEC.listOf().fieldOf("loot_tables")
                    .forGetter(ChestLootBand::lootTables)
    ).apply(instance, ChestLootBand::new))).flatXmap(ChestLootBand::validateBand, ChestLootBand::validateBand);

    /**
     * An empty band is a load error for the same reason an empty {@code mob_sets} is: it reads as
     * "this depth has no loot", and what it actually produces is a chest that holds nothing, which
     * a player finds by walking to it and opening it.
     */
    private static DataResult<ChestLootBand> validateBand(ChestLootBand band) {
        if (band.lootTables.isEmpty()) {
            return DataResult.error(() -> "chest loot band at floor " + band.minFloorIndex
                    + ": 'loot_tables' is empty, so every chest on those floors would be an empty chest");
        }
        return DataResult.success(band);
    }

    /**
     * The band covering {@code floorIndex}, or empty if the table is empty. Reads the table for the
     * deepest band that has started; see {@link MobSetBand#forFloor}, which this mirrors.
     */
    public static Optional<ChestLootBand> forFloor(List<ChestLootBand> table, int floorIndex) {
        ChestLootBand best = null;
        for (ChestLootBand band : table) {
            if (band.minFloorIndex <= floorIndex
                    && (best == null || band.minFloorIndex > best.minFloorIndex)) {
                best = band;
            }
        }
        return Optional.ofNullable(best);
    }
}
