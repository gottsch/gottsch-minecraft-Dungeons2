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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.config.FloorPatternConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Locale;

/**
 * Weighted pick from a {@link FloorPatternConfig}, mapping the chosen {@link FloorPatternEntry}
 * to a concrete {@link IDungeonFloorGenerator}. Kept separate from the config records themselves
 * (which stay pure data, same split {@code DungeonGenerationConfig} keeps from the planner) since
 * only this package needs to know what a {@code type} string actually builds.
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public final class FloorPatternSelector {

    private FloorPatternSelector() {}

    /**
     * Rolls one weighted entry from {@code config} using {@code random} and returns the
     * generator it maps to. An empty element list, a non-positive total weight, or an
     * unrecognized {@code type} all fall back to {@link BasicFloorGenerator} &mdash; the same
     * graceful degradation an absent/empty pool always has elsewhere in this codebase.
     */
    public static IDungeonFloorGenerator select(FloorPatternConfig config, RandomSource random) {
        List<FloorPatternEntry> elements = config.elements();
        if (elements.isEmpty()) {
            return new BasicFloorGenerator();
        }
        int totalWeight = elements.stream().mapToInt(FloorPatternEntry::weight).sum();
        if (totalWeight <= 0) {
            return new BasicFloorGenerator();
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (FloorPatternEntry entry : elements) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return toGenerator(entry);
            }
        }
        return new BasicFloorGenerator(); // unreachable: roll < totalWeight == cumulative sum
    }

    private static IDungeonFloorGenerator toGenerator(FloorPatternEntry entry) {
        return switch (entry.type().trim().toLowerCase(Locale.ROOT)) {
            case "border" -> new FloorBorderPatternProvider(entry.inset());
            default -> new BasicFloorGenerator(); // "empty" or unrecognized
        };
    }
}
