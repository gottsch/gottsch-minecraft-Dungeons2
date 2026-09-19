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

import mod.gottsch.forge.gottschcore.mobset.MobCount;
import mod.gottsch.forge.gottschcore.mobset.MobSetData;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * How many mobs one spawner releases, resolved at generation and baked into its block entity.
 *
 * <h2>The MOB SET owns the count</h2>
 * <p>Per field, first that speaks wins:</p>
 * <ol>
 *   <li>A stated {@code min_mobs}/{@code max_mobs} &mdash; the scheme slot on the procedural route,
 *       the marker's NBT on the authored one. An exact per-spawner override: no bonus.</li>
 *   <li>The drawn mob set's {@code count}, plus the depth band's {@code bonus_mobs}.</li>
 *   <li>{@link SpawnerConfig#DEFAULT_MIN_MOBS}/{@link SpawnerConfig#DEFAULT_MAX_MOBS}, plus the
 *       bonus &mdash; only when the set is not in the registry, which in a real game means a
 *       misnamed set (and no mobs to release anyway) and headlessly means an empty registry.</li>
 * </ol>
 *
 * <p>Until 2026-09-17 the set's {@code count} was read by nothing: the processor's codec defaulted
 * to 1&ndash;3 and the depth bands stated absolute counts, so no floor ever reached the set and a
 * modder editing {@code count} changed nothing. There are deliberately no pool-level counts any more
 * for that reason &mdash; a processor list that stated one would silently shadow every set it
 * draws.</p>
 *
 * <p>Baked, not resolved at trigger time, because the vanilla cage must be handed a
 * {@code SpawnCount} at generation and the two routes must agree. The consequence: a datapack edit
 * to {@code count} reaches newly generated chunks only.</p>
 *
 * <p>{@code min} may be 0 (a set whose {@code count.min} is 0); {@code max} is clamped up to
 * {@code min}. The vanilla cage clamps its own {@code SpawnCount} to at least 1.</p>
 */
public record MobRange(int min, int max) {

    public MobRange {
        min = Math.max(0, min);
        max = Math.max(min, max);
    }

    /** Resolves against the live {@link MobSetDataRegistry}. */
    public static MobRange resolve(Optional<Integer> statedMin, Optional<Integer> statedMax,
                                   ResourceLocation mobSet, int bonus) {
        Optional<MobCount> count = mobSet == null ? Optional.empty()
                : MobSetDataRegistry.get(mobSet).map(MobSetData::getCount);
        return resolve(statedMin, statedMax, count, bonus);
    }

    /** The registry-free form, so the precedence is testable headlessly. */
    public static MobRange resolve(Optional<Integer> statedMin, Optional<Integer> statedMax,
                                   Optional<MobCount> setCount, int bonus) {
        int baseMin = setCount.map(MobCount::getMin).orElse(SpawnerConfig.DEFAULT_MIN_MOBS);
        int baseMax = setCount.map(MobCount::getMax).orElse(SpawnerConfig.DEFAULT_MAX_MOBS);
        return new MobRange(statedMin.orElse(baseMin + bonus), statedMax.orElse(baseMax + bonus));
    }
}
