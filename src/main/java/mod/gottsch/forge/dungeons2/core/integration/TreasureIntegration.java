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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.ModList;

import java.util.Optional;

/**
 * Every Treasure2 touchpoint in Dungeons2, in one file. Backlog #48 step 4.
 *
 * <h2>Treasure2 is optional, and that dictates the shape of this class</h2>
 * <p>Dungeons2 compiles against Treasure2 {@code compileOnly} and declares it {@code mandatory=false}
 * in {@code mods.toml}, so the dungeon must generate perfectly well without it. The JVM loads a class
 * on first active use, so <strong>the guard has to live in a class that names no Treasure2 type</strong>
 * &mdash; which is why the actual calls sit in the nested {@link Delegate} and this outer class
 * imports nothing from Treasure2. Calling {@link #isLoaded()} cannot drag Treasure2 in; only a
 * successful guard reaches {@code Delegate}, and by then the classes exist.</p>
 *
 * <p>Putting the guard and the call in one class would compile and would crash on first use with a
 * {@code NoClassDefFoundError} on exactly the machines that do not have Treasure2 &mdash; the ones
 * that never run the code being guarded.</p>
 *
 * <h2>One file, deliberately</h2>
 * <p>Treasure2 has a NeoForge branch and Dungeons2 will follow. Keeping every touchpoint here means
 * the port is this file: {@code net.minecraftforge.fml.ModList} becomes
 * {@code net.neoforged.fml.ModList} and the Treasure2 imports keep their names, because
 * {@code TreasureApi.generateChest} is deliberately typed in vanilla terms.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public final class TreasureIntegration {

    public static final String TREASURE2 = "treasure2";

    private TreasureIntegration() {}

    /** Whether Treasure2 is installed. Safe to call unconditionally; names no Treasure2 type. */
    public static boolean isLoaded() {
        // ModList.get() is null until Forge has built the mod list, and NEVER becomes non-null
        // outside a running game -- a headless test, or anything running before mod construction.
        // Without this guard the NPE surfaces nowhere near here: it is thrown out of a structure
        // processor's finalizeProcessing, which reports as "postProcess failed" on a piece.
        //
        // Found 2026-08-30 by #61. Wiring dungeons2:chest into the shipped weathering lists made
        // ChestMarkerProcessor run in every headless harness that renders a piece, and eight tests
        // that had nothing to do with chests failed at once. Absent Forge, Treasure2 is absent by
        // definition, so false is the right answer and not merely a safe one.
        ModList list = ModList.get();
        return list != null && list.isLoaded(TREASURE2);
    }

    /**
     * A real Treasure2 chest for {@code pos}, or empty when Treasure2 is absent.
     *
     * <p>Empty means the caller should fall back to its own chest &mdash; it is not an error.</p>
     *
     * @param rarity                a Treasure2 rarity id to force (e.g. {@code treasure2:epic}), or
     *                              null to let Treasure2 draw one. An id Treasure2 does not know is
     *                              logged and drawn instead
     * @param supplementalLootTable D2's own table, rolled by Treasure2 into the slots its loot leaves
     *                              empty when the chest first fills
     */
    public static Optional<StructureTemplate.StructureBlockInfo> generateChest(
            LevelReader level, BlockPos pos, Direction facing, RandomSource random,
            ResourceLocation rarity, ResourceLocation supplementalLootTable) {

        if (!isLoaded() || level == null) {
            return Optional.empty();
        }
        return Delegate.generate(level, pos, facing, random, rarity, supplementalLootTable);
    }

    /**
     * Completes the cache entry for a chest {@link #generateChest} placed, now that a level that
     * knows its own dimension is available. Call from a processor's {@code finalizeProcessing}.
     */
    public static void finalizeChest(ServerLevelAccessor level, BlockPos pos) {
        if (!isLoaded() || level == null) {
            return;
        }
        Delegate.finalize(level, pos);
    }

    /** Whether a state is one of Treasure2's chests; false when Treasure2 is absent. */
    public static boolean isTreasureChest(net.minecraft.world.level.block.state.BlockState state) {
        return isLoaded() && Delegate.isTreasureChest(state);
    }

    /**
     * The half that names Treasure2 types. Never touched unless the guard above passed, which is the
     * whole reason it is a separate class rather than three more methods on the outer one.
     */
    private static final class Delegate {

        private Delegate() {}

        static Optional<StructureTemplate.StructureBlockInfo> generate(
                LevelReader level, BlockPos pos, Direction facing, RandomSource random,
                ResourceLocation rarityId, ResourceLocation supplementalLootTable) {

            // The rarity is forced only when the pool names one (the boss chest's size tier, Mark
            // 2026-10-05); otherwise Treasure2 draws. A forced rarity does not feed T2's adaptive
            // weights -- see ChestGeneration.
            Optional<mod.gottsch.forge.treasure2.core.rarity.IRarity> rarity = Optional.empty();
            if (rarityId != null) {
                rarity = mod.gottsch.forge.treasure2.core.rarity.TreasureRarities.getRarityByName(rarityId);
                if (rarity.isEmpty()) {
                    mod.gottsch.forge.dungeons2.Dungeons.LOGGER.warn("[D2-CHEST] treasure2 has no"
                            + " rarity {}; letting it draw one", rarityId);
                }
            }
            // The loot table is left to Treasure2. Forcing it would produce a
            // Treasure2-branded chest holding Dungeons2's idea of loot, which is the imitation this
            // whole route exists to avoid -- the point of asking Treasure2 is to get ITS chest, with
            // its chest block, its locks and its table. D2's table goes in as the SUPPLEMENTAL one
            // (Mark, 2026-10-05): rolled only into the slots Treasure2's loot leaves empty, so it can
            // add to the chest but never displace what Treasure2 put there. Needs Treasure2 4.1.0.
            return Optional.ofNullable(mod.gottsch.forge.treasure2.api.TreasureApi.generateChest(
                    level, pos, random, facing,
                    mod.gottsch.forge.treasure2.core.world.feature.FeatureType.TERRANEAN,
                    rarity, Optional.empty(), Optional.ofNullable(supplementalLootTable)));
        }

        static void finalize(ServerLevelAccessor level, BlockPos pos) {
            mod.gottsch.forge.treasure2.api.TreasureApi.finalizeChest(level, pos);
        }

        static boolean isTreasureChest(net.minecraft.world.level.block.state.BlockState state) {
            return mod.gottsch.forge.treasure2.api.TreasureApi.isTreasureChest(state);
        }
    }
}
