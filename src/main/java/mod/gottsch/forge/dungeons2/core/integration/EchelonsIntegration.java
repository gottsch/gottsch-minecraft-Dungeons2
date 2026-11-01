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
package mod.gottsch.forge.dungeons2.core.integration;

import mod.gottsch.forge.dungeons2.core.config.EchelonConfig;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every Enemy Echelons API touchpoint in Dungeons2, in one file &mdash; {@link TreasureIntegration}'s
 * shape, for its reason: the guard lives in a class that names no EE type, and the calls live in
 * the nested {@link Delegate}, which the JVM only loads once the guard has passed. Merging the two
 * would compile and then throw {@code NoClassDefFoundError} on exactly the machines without EE.
 *
 * <h2>Integrate against EE, never Stronger Mobs Below</h2>
 * <p>EE is the API; SMB is a content pack that registers world-Y echelons into EE's global registry
 * and applies them on {@code EntityJoinLevelEvent}. Dungeons2 builds its <em>own</em>
 * {@code EchelonRegistry} from the motif's {@code echelon} block and applies it with an explicit
 * difficulty taken from the depth band, so it works with EE alone and never borrows SMB's numbers.</p>
 *
 * <h2>Whoever sets the difficulty first wins</h2>
 * <p>EE's {@code applyModifications} returns early on a mob whose difficulty is already set. So this
 * must run before the mob joins the level, or SMB's join handler gets there first and its Y-based
 * value stands &mdash; silently. {@code EchelonSpawnEvent} calls it from {@code FinalizeSpawn},
 * which both spawner kinds fire before {@code addFreshEntity}. The same rule then works in our
 * favour: SMB's later call finds the difficulty set and leaves the mob alone.</p>
 *
 * @author Mark Gottschling on Sep 23, 2026
 */
public final class EchelonsIntegration {

    public static final String EECHELONSAPI = "eechelonsapi";

    private EchelonsIntegration() {}

    /** Whether the Enemy Echelons API is installed. Safe to call unconditionally. */
    public static boolean isLoaded() {
        // Null outside a running game -- see TreasureIntegration#isLoaded, which learned it the
        // hard way. Absent Forge, EE is absent by definition.
        ModList list = ModList.get();
        return list != null && list.isLoaded(EECHELONSAPI);
    }

    /**
     * Scales {@code mob} to {@code difficulty} using {@code config}'s factors. A no-op without EE, on
     * a mob EE has no capability for, on a blacklisted mob, and on a mob already given a difficulty.
     *
     * @return whether the mob now carries Dungeons2's difficulty
     */
    public static boolean apply(Mob mob, EchelonConfig config, int difficulty) {
        if (!isLoaded() || mob == null || config == null) {
            return false;
        }
        return Delegate.apply(mob, config, difficulty);
    }

    /**
     * Locks {@code mob} at difficulty 0, unscaled, so nothing that runs later &mdash; Stronger Mobs
     * Below's join handler, by world Y &mdash; can scale it. A no-op without EE, and on a mob that
     * already has a difficulty.
     */
    public static void pinUnscaled(Mob mob) {
        if (!isLoaded() || mob == null) {
            return;
        }
        Delegate.pinUnscaled(mob);
    }

    /** The difficulty EE has recorded on {@code mob}, or empty when unset or EE is absent. */
    public static OptionalInt difficultyOf(Mob mob) {
        if (!isLoaded() || mob == null) {
            return OptionalInt.empty();
        }
        return Delegate.difficultyOf(mob);
    }

    /** The half that names EE types. Never touched unless {@link #isLoaded()} passed. */
    private static final class Delegate {

        /**
         * One registry per distinct factor table. Keyed on the record's VALUE, so a datapack reload
         * that decodes an identical table reuses the registry, and one that retunes it gets a new
         * one without anything having to invalidate the old.
         */
        private static final Map<EchelonConfig,
                mod.gottsch.forge.eechelonsapi.core.registry.EchelonRegistry> REGISTRIES =
                new ConcurrentHashMap<>();

        private Delegate() {}

        static boolean apply(Mob mob, EchelonConfig config, int difficulty) {
            if (!mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.hasDifficultyCapability(mob)) {
                return false;
            }
            mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.apply(
                    REGISTRIES.computeIfAbsent(config, Delegate::registryFor), mob, difficulty);
            // Read back rather than assumed: EE silently declines a mob whose difficulty was
            // already set, and one its registry has no config for (our blacklist) comes back at 0.
            return mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.getDifficulty(mob) == difficulty;
        }

        /**
         * An EMPTY registry, on purpose: EE answers "no config for this mob" by setting difficulty
         * 0 and applying nothing, which is exactly a pin. No factor table to get wrong.
         */
        private static mod.gottsch.forge.eechelonsapi.core.registry.EchelonRegistry unscaled;

        static void pinUnscaled(Mob mob) {
            if (unscaled == null) {
                unscaled = mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.customRegistry();
            }
            mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.apply(unscaled, mob, 0);
        }

        static OptionalInt difficultyOf(Mob mob) {
            if (!mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.hasDifficultyCapability(mob)) {
                return OptionalInt.empty();
            }
            int difficulty = mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.getDifficulty(mob);
            return difficulty < 0 ? OptionalInt.empty() : OptionalInt.of(difficulty);
        }

        /**
         * EE's config object for one factor table: every dimension, every mod, minus the blacklist.
         *
         * <p><strong>The one echelon is a formality.</strong> EE's {@code register} drops a config
         * with no echelons, and an echelon is how EE picks a difficulty from world Y &mdash; which
         * Dungeons2 never asks it to do, since every call passes an explicit difficulty. So it
         * spans all of Y and can only ever answer 0.</p>
         *
         * <p>XP is left at 0 here on purpose. EE applies {@code xp_factor} from its GLOBAL registry
         * only, so a factor on this private one would never be read; {@code EchelonSpawnEvent}
         * scales XP itself.</p>
         */
        private static mod.gottsch.forge.eechelonsapi.core.registry.EchelonRegistry registryFor(
                EchelonConfig config) {
            mod.gottsch.forge.eechelonsapi.core.config.EchelonConfigsHolder.Config ee =
                    new mod.gottsch.forge.eechelonsapi.core.config.EchelonConfigsHolder.Config();
            ee.setId("dungeons2:depth");
            ee.setHpFactor(config.hpFactor());
            ee.setDamageFactor(config.damageFactor());
            ee.setArmorFactor(config.armorFactor());
            ee.setArmorToughnessFactor(config.armorToughnessFactor());
            ee.setKnockbackIncrement(config.knockbackIncrement());
            ee.setKnockbackResistIncrement(config.knockbackResistIncrement());
            ee.setSpeedFactor(config.speedFactor());
            // Mutable copies: EE's register() rewrites these lists in place (wildcard handling).
            ee.setMobBlacklist(new ArrayList<>(config.mobBlacklist()));
            ee.setModBlacklist(new ArrayList<>());
            ee.setModWhitelist(new ArrayList<>());
            ee.setMobWhitelist(new ArrayList<>());
            ee.setDimensions(new ArrayList<>(List.of(".")));

            mod.gottsch.forge.eechelonsapi.core.config.EchelonConfigsHolder.DifficultyEntry zero =
                    new mod.gottsch.forge.eechelonsapi.core.config.EchelonConfigsHolder.DifficultyEntry();
            zero.setDifficulty(0);
            zero.setWeight(1.0D);
            mod.gottsch.forge.eechelonsapi.core.config.EchelonConfigsHolder.Echelon everywhere =
                    new mod.gottsch.forge.eechelonsapi.core.config.EchelonConfigsHolder.Echelon();
            everywhere.setId("dungeons2:depth");
            // Wider than any build height, but not MIN/MAX_VALUE: interval code that does max + 1
            // would overflow, and nothing here is worth finding out.
            everywhere.setMin(-4096);
            everywhere.setMax(4096);
            everywhere.setHistogram(new ArrayList<>(List.of(zero)));
            ee.setEchelons(new ArrayList<>(List.of(everywhere)));

            mod.gottsch.forge.eechelonsapi.core.registry.EchelonRegistry registry =
                    mod.gottsch.forge.eechelonsapi.api.EnemyEchelonsApi.customRegistry();
            registry.register(ee);
            return registry;
        }
    }
}
