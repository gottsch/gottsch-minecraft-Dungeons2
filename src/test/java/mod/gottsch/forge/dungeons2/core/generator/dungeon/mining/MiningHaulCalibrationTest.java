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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.mining;

import mod.gottsch.forge.dungeons2.core.config.MiningConfig;
import mod.gottsch.forge.dungeons2.core.config.MiningConfigHelper;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.dungeons2.diagnostic.TestRegistries;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the shipped ore table actually pays out, across sizes and seeds &mdash; backlog #7.
 *
 * <h2>The table is numbers, and numbers need measuring</h2>
 * <p>Every rate in {@code mining_config/default.json} is a guess until a dungeon is planned against
 * it. The estimate multiplies excavated volume by a per-thousand rate, and excavated volume is not
 * something anybody has an intuition for &mdash; a MEDIUM dungeon removes about 48,000 blocks, which
 * is five times what it looks like on a floor plan. This test prints the distribution and asserts
 * the bounds that Mark's two requirements turn into.</p>
 *
 * <p><strong>Re-run this after touching room sizes, floor counts or corridor width.</strong> All
 * three move excavated volume, and the table is calibrated against it.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
class MiningHaulCalibrationTest {

    private static final int SEEDS = 24;
    private static final String MOTIF = "classic";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MiningConfig config() {
        return MiningConfigHelper.get(TestRegistries.get());
    }

    private static DungeonLayout plan(long seed, DungeonSize size) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, MOTIF, new TemplateCatalog())
                .withSize(size)
                .withCorridorWidth(3)
                .withCorridorStyles(DungeonStructure.corridorStyleWeights(
                        MotifConfigs.load(MOTIF).corridor()))
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
    }

    /**
     * The shipped table is loaded, and it is the shipped file rather than {@link MiningConfig#DEFAULT}.
     *
     * <p>{@code MiningConfigHelper} falls back to an empty table when the registry is missing, and an
     * empty table pays out nothing at all &mdash; so without this check every assertion below would
     * pass vacuously against a dungeon that produced no chest for the most boring possible reason.
     * This is {@code theWeatheringPassActuallyRan}'s argument, one registry over.</p>
     */
    @Test
    void theShippedOreTableIsLoaded() {
        MiningConfig config = config();
        assertTrue(!config.ores().isEmpty(),
                "the mining_config registry resolved to the empty DEFAULT table, so nothing below"
                        + " is testing the shipped file");
        assertTrue(config.payoutFraction() > 0.0D, "payoutFraction is 0, so no chest ever pays out");
    }

    /**
     * Every {@code item} in the shipped table is a real item.
     *
     * <p>The item-registry twin of {@code ShippedBlockIdsTest}, and it exists for the same reason
     * #13 did: a misspelled id does not fail to load, it produces a chest quietly missing one ore.
     * The block sweep cannot cover these &mdash; an ore band names an ITEM, so it is classified into
     * that test's {@code NON_BLOCK_KEYS} and lands here instead.</p>
     */
    @Test
    void everyOreBandNamesARealItem() {
        List<String> unknown = new ArrayList<>();
        for (MiningConfig.OreBand band : config().ores()) {
            ResourceLocation id = ResourceLocation.tryParse(band.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                unknown.add(band.item());
            }
        }
        assertTrue(unknown.isEmpty(), "mining_config names item(s) that do not exist, so each would"
                + " silently vanish from the Mining Chest: " + unknown);
    }

    /**
     * Prints the payout distribution per size. Not an assertion &mdash; the numbers this produces are
     * what the shipped table was tuned against, and they are quoted in {@code default.json}.
     */
    @Test
    void printThePayoutDistribution() {
        MiningConfig config = config();
        for (DungeonSize size : DungeonSize.values()) {
            Map<String, Integer> totals = new LinkedHashMap<>();
            int planned = 0;
            int items = 0;
            int maxItems = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                Optional<MiningChestPlanner.MiningChestPlan> plan =
                        MiningChestPlanner.plan(plan(seed, size), config);
                if (plan.isEmpty()) {
                    continue;
                }
                planned++;
                MiningHaul haul = plan.get().haul();
                items += haul.totalItems();
                maxItems = Math.max(maxItems, haul.totalItems());
                for (MiningHaul.Stack stack : haul.stacks()) {
                    totals.merge(stack.item(), stack.count(), Integer::sum);
                }
            }
            int chests = Math.max(1, planned);
            StringBuilder line = new StringBuilder();
            totals.forEach((item, total) -> line.append(String.format("  %-24s avg %.1f%n",
                    item, total / (double) chests)));
            System.out.printf("[CALIBRATION] %s: %d/%d seeds got a chest, avg %d items, max %d%n%s",
                    size, planned, SEEDS, items / Math.max(1, planned), maxItems, line);
        }
    }

    /**
     * <strong>The Mining Chest must not out-do the boss chest</strong> (Mark, 2026-08-31).
     *
     * <p>Asserted on the one axis the two can be compared on. {@code classic_hoard} rolls 3-5 times
     * over a pool whose diamond entry is 1-3 stones, so a rich boss chest is around six diamonds and
     * also carries enchanted gear, an enchanted book and a golden apple &mdash; none of which this
     * chest has or ever will. Diamonds are therefore the only currency both hold, and the cap on
     * them is what keeps the comparison honest at the top end.</p>
     *
     * <p>Three is the ceiling the shipped bands arithmetically permit (max 2 + max 1). This asserts
     * the arithmetic holds over real layouts, which is a different claim: it is the one that fails
     * if a band is ever added, widened or duplicated.</p>
     */
    @Test
    void noDungeonEverPaysOutMoreThanThreeDiamonds() {
        MiningConfig config = config();
        int worst = 0;
        String worstWhere = "";
        for (DungeonSize size : DungeonSize.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Optional<MiningChestPlanner.MiningChestPlan> plan =
                        MiningChestPlanner.plan(plan(seed, size), config);
                if (plan.isEmpty()) {
                    continue;
                }
                for (MiningHaul.Stack stack : plan.get().haul().stacks()) {
                    if (stack.item().equals("minecraft:diamond") && stack.count() > worst) {
                        worst = stack.count();
                        worstWhere = size + " seed " + seed;
                    }
                }
            }
        }
        assertTrue(worst <= 3, "a Mining Chest paid out " + worst + " diamonds (" + worstWhere
                + "). The boss chest is the prize and this one is a bonus; lower a `max` on the"
                + " diamond bands in mining_config/default.json");
    }

    /**
     * The haul always fits in the chest it is put in.
     *
     * <p>{@code MiningHaul#itemsSnbt} truncates at 27 slots, so an over-full haul is not a crash
     * &mdash; it is items silently vanishing, and the ones that vanish are whatever sorted last.
     * That degradation exists for safety and should never actually fire on the shipped table.</p>
     */
    @Test
    void everyHaulFitsInOneChest() {
        MiningConfig config = config();
        for (DungeonSize size : DungeonSize.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Optional<MiningChestPlanner.MiningChestPlan> plan =
                        MiningChestPlanner.plan(plan(seed, size), config);
                if (plan.isEmpty()) {
                    continue;
                }
                MiningHaul haul = plan.get().haul();
                assertTrue(haul.slotsNeeded() <= MiningHaul.CHEST_SLOTS,
                        size + " seed " + seed + " needs " + haul.slotsNeeded()
                                + " slots and a chest holds " + MiningHaul.CHEST_SLOTS
                                + ", so items would be dropped: " + haul);
            }
        }
    }
}
