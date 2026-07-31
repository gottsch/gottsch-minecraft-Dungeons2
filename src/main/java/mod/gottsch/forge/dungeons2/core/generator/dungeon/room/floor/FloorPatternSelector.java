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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    /**
     * Maps a {@code type} to its generator. There is deliberately no Java-side default block for
     * any pattern's material slots (see {@code FloorBorderPatternProvider}/{@code
     * CheckerboardFloorPatternProvider}/{@code RandomSpeckleFloorPatternProvider}) &mdash; {@code
     * floor_pattern_config} is the single source of truth for which blocks a pattern renders, so
     * if a required slot fails to resolve (absent, malformed, or an unregistered id), the whole
     * entry degrades to plain floor rather than silently substituting a guessed block.
     */
    private static IDungeonFloorGenerator toGenerator(FloorPatternEntry entry) {
        return switch (entry.type().trim().toLowerCase(Locale.ROOT)) {
            case "border" -> {
                Block corner = resolveBlock(entry.cornerBlock());
                Block edgeLeft = resolveBlock(entry.edgeLeftBlock());
                Block edgeRight = resolveBlock(entry.edgeRightBlock());
                yield (corner == null || edgeLeft == null || edgeRight == null)
                        ? new BasicFloorGenerator()
                        : new FloorBorderPatternProvider(entry.inset(), corner, edgeLeft, edgeRight);
            }
            case "checkerboard" -> {
                Block primary = resolveBlock(entry.primaryBlock());
                Block secondary = resolveBlock(entry.secondaryBlock());
                yield (primary == null || secondary == null)
                        ? new BasicFloorGenerator()
                        : new CheckerboardFloorPatternProvider(primary, secondary);
            }
            case "speckle" -> {
                Block base = resolveBlock(entry.primaryBlock());
                Block accent = resolveBlock(entry.secondaryBlock());
                yield (base == null || accent == null)
                        ? new BasicFloorGenerator()
                        : new RandomSpeckleFloorPatternProvider(entry.probability(), base, accent);
            }
            case "composite" -> toComposite(entry.generators());
            default -> new BasicFloorGenerator(); // "empty" or unrecognized
        };
    }

    /**
     * The first nested entry becomes the base full fill; every entry after it is only kept if
     * its generator is overlay-capable ({@link IFloorOverlayGenerator}, currently just {@code
     * "border"}) &mdash; anything else in an overlay slot is silently skipped, same graceful
     * degradation an unrecognized top-level {@code type} already gets. An empty {@code
     * generators} list falls back to {@link BasicFloorGenerator}, same as an empty top-level
     * element list.
     */
    private static IDungeonFloorGenerator toComposite(List<FloorPatternEntry> generators) {
        if (generators.isEmpty()) {
            return new BasicFloorGenerator();
        }
        IDungeonFloorGenerator base = toGenerator(generators.get(0));
        List<IFloorOverlayGenerator> overlays = new ArrayList<>();
        for (int i = 1; i < generators.size(); i++) {
            IDungeonFloorGenerator generator = toGenerator(generators.get(i));
            if (generator instanceof IFloorOverlayGenerator overlay) {
                overlays.add(overlay);
            }
        }
        return new CompositeFloorPatternProvider(base, overlays);
    }

    /**
     * Resolves an optional block-id string to a {@link Block}, or {@code null} when absent,
     * malformed, or not a registered block id. {@code null} here means "this slot didn't resolve"
     * -- the caller is responsible for degrading the whole entry to plain floor, since there is no
     * per-slot default to fall back to (see {@link #toGenerator}).
     */
    private static Block resolveBlock(Optional<String> id) {
        if (id.isEmpty()) {
            return null;
        }
        Block block;
        try {
            block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id.get()));
        } catch (RuntimeException malformed) {
            return null;
        }
        // BLOCKS is a defaulted registry (falls back to minecraft:air for an unknown id rather
        // than null); either way an unresolved id counts as "didn't resolve".
        return (block == null || block == Blocks.AIR) ? null : block;
    }
}
