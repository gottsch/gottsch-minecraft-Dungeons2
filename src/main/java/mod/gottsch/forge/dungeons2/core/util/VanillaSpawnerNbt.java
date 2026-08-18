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
package mod.gottsch.forge.dungeons2.core.util;

import mod.gottsch.forge.gottschcore.mobset.MobSetData;
import mod.gottsch.forge.gottschcore.mobset.WeightedMob;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the NBT a vanilla {@code minecraft:spawner} needs, from one of this mod's mob sets.
 *
 * <h2>Why a mob set has to be flattened at all</h2>
 * <p>A proximity spawner stores the set's <em>name</em> and rolls a mob when it fires, so the set
 * stays a live reference. Vanilla's {@code BaseSpawner} has never heard of a mob set: it wants
 * entity ids in its own tag and does its own rolling. So the set has to be converted at generation
 * time, and the conversion is lossy in one direction &mdash; a mob set edited in a datapack later
 * will change every proximity spawner in an existing world and no vanilla one.</p>
 *
 * <h2>{@code SpawnPotentials}, not just {@code SpawnData}</h2>
 * <p>Vanilla supports a weighted list, which is exactly what a mob set already is, so the whole set
 * maps across and the cage keeps drawing from it on every spawn. Writing only {@code SpawnData}
 * would have picked one mob at generation and frozen it, which is strictly less like the proximity
 * spawner this is meant to mirror.</p>
 *
 * <h2>No usable mobs means place nothing</h2>
 * <p>A {@code minecraft:spawner} with no spawn data is not the harmless no-op an unconfigured
 * proximity block is &mdash; it is a visible cage holding a spinning pig, in a dungeon. Callers
 * check {@link #usableMobs} and skip the placement entirely, which is what the proximity path
 * already does with an empty set.</p>
 *
 * <h2>SNBT rather than a CompoundTag, on purpose</h2>
 * <p>Both spawner routes can use this one output. The proximity spawner's two routes famously
 * cannot share code &mdash; the processor builds a typed {@code CompoundTag} while the procedural
 * side builds stringified key/values &mdash; but SNBT is a format both can carry: the procedural
 * side hands it to {@code BlockEntityData#withNbt} and the processor parses it directly. One
 * builder, so the two cannot drift the way the proximity pair had to be pinned against drifting.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public final class VanillaSpawnerNbt {

    /** NBT keys on {@code minecraft:spawner}. */
    public static final String SPAWN_DATA = "SpawnData";
    public static final String SPAWN_POTENTIALS = "SpawnPotentials";
    public static final String SPAWN_COUNT = "SpawnCount";
    public static final String SPAWN_RANGE = "SpawnRange";
    public static final String DELAY = "Delay";
    public static final String MIN_SPAWN_DELAY = "MinSpawnDelay";
    public static final String MAX_SPAWN_DELAY = "MaxSpawnDelay";
    public static final String REQUIRED_PLAYER_RANGE = "RequiredPlayerRange";
    public static final String MAX_NEARBY_ENTITIES = "MaxNearbyEntities";

    /**
     * Vanilla's own dungeon-spawner values, restated rather than left to defaults.
     *
     * <p>A {@code minecraft:spawner} placed with no tuning uses {@code BaseSpawner}'s field
     * initialisers, which are the same numbers &mdash; but relying on that would mean this mod's
     * spawners silently changing behaviour if Mojang ever retunes them, and would leave nothing in
     * this file saying what the intended cadence is.</p>
     */
    public static final int DEFAULT_SPAWN_RANGE = 4;
    public static final int DEFAULT_DELAY = 20;
    public static final int DEFAULT_MIN_SPAWN_DELAY = 200;
    public static final int DEFAULT_MAX_SPAWN_DELAY = 800;
    public static final int DEFAULT_REQUIRED_PLAYER_RANGE = 16;
    public static final int DEFAULT_MAX_NEARBY_ENTITIES = 6;

    private VanillaSpawnerNbt() {}

    /**
     * The mobs of {@code mobSet} that vanilla can actually draw, or empty if there are none.
     *
     * <p>Zero and negative weights are dropped rather than passed through: vanilla's weighted list
     * accepts them and then never draws them, so a set that is entirely zero-weighted would produce
     * a cage that spawns nothing while looking perfectly fine. Filtering here makes that case reach
     * the "no usable mobs" answer instead of shipping a dud.</p>
     */
    public static List<WeightedMob> usableMobs(MobSetData mobSet) {
        if (mobSet == null || mobSet.getMobs() == null) {
            return List.of();
        }
        return mobSet.getMobs().stream()
                .filter(mob -> mob != null && mob.id() != null && mob.weight() > 0)
                .toList();
    }

    /**
     * SNBT for the {@code SpawnData} key: the mob the cage shows and spawns first.
     *
     * <p>Written even though {@code SpawnPotentials} carries the whole set, because vanilla shows
     * {@code SpawnData}'s mob spinning in the cage and only rolls the potentials <em>after</em> the
     * first spawn &mdash; a spawner with potentials and no spawn data displays a pig. So the two
     * together are what make the cage look like what it will produce.</p>
     */
    public static String spawnData(String mobId) {
        return "{entity:{id:\"" + mobId + "\"}}";
    }

    /**
     * SNBT for the {@code SpawnPotentials} key: the whole mob set as vanilla's own weighted list.
     *
     * <p>This is what makes a vanilla spawner mirror the proximity one rather than merely resemble
     * it. Writing only {@code SpawnData} would freeze a single mob at generation time; mapping the
     * set across means the cage keeps drawing from it, which is what the proximity spawner does at
     * every trigger.</p>
     *
     * <p><strong>A list, not a compound</strong> &mdash; which is exactly why the renderer cannot
     * use {@code TagParser.parseTag} directly on these values. See
     * {@code DungeonPiece#parseNbtValue}.</p>
     */
    public static String spawnPotentials(List<WeightedMob> mobs) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < mobs.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append("{weight:").append(mobs.get(i).weight())
                    .append(",data:{entity:{id:\"").append(mobs.get(i).id()).append("\"}}}");
        }
        return out.append(']').toString();
    }

    /**
     * The scalar tuning fields, as the flat {@code String -> String} the existing channel takes.
     *
     * <p>Separate from the two SNBT values above because they genuinely are a different kind of
     * value: ints, which {@code BlockEntityData}'s original map already carries correctly. Only the
     * compound and the list needed a new channel, and keeping the split visible is what stops the
     * SNBT path becoming the way everything travels.</p>
     *
     * @param spawnCount how many mobs per attempt; clamped to at least 1, since 0 is a cage that
     *                   ticks forever and produces nothing
     */
    public static Map<String, String> tuning(int spawnCount) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(SPAWN_COUNT, String.valueOf(Math.max(1, spawnCount)));
        out.put(SPAWN_RANGE, String.valueOf(DEFAULT_SPAWN_RANGE));
        out.put(DELAY, String.valueOf(DEFAULT_DELAY));
        out.put(MIN_SPAWN_DELAY, String.valueOf(DEFAULT_MIN_SPAWN_DELAY));
        out.put(MAX_SPAWN_DELAY, String.valueOf(DEFAULT_MAX_SPAWN_DELAY));
        out.put(MAX_NEARBY_ENTITIES, String.valueOf(DEFAULT_MAX_NEARBY_ENTITIES));
        out.put(REQUIRED_PLAYER_RANGE, String.valueOf(DEFAULT_REQUIRED_PLAYER_RANGE));
        return out;
    }
}
