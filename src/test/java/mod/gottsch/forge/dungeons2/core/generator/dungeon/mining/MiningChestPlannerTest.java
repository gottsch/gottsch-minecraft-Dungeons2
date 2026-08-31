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
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Mining Chest plan itself &mdash; backlog #7: that it is deterministic, that it lands deep, and
 * that the two ways of getting nothing both work.
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
class MiningChestPlannerTest {

    private static final int SEEDS = 40;
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
     * <strong>The same layout always plans the same chest.</strong>
     *
     * <p>Load-bearing twice over. The plan is made at emit time and <em>serialized onto the piece</em>
     * &mdash; but a structure's pieces are re-emitted whenever the structure start is recomputed, and
     * a plan that drifted would move the chest or change its contents between one generation and the
     * next of the same seed. And the location half is drawn from the layout's own seed, so this is
     * also what pins {@code MiningChestPlanner} to reading {@code DungeonLayout#getSeed} rather than
     * anything ambient.</p>
     */
    @Test
    void theSameLayoutAlwaysPlansTheSameChest() {
        MiningConfig config = config();
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonLayout layout = plan(seed, DungeonSize.MEDIUM);
            Optional<MiningChestPlanner.MiningChestPlan> first =
                    MiningChestPlanner.plan(layout, config);
            Optional<MiningChestPlanner.MiningChestPlan> second =
                    MiningChestPlanner.plan(layout, config);
            assertEquals(first, second, "seed " + seed + " planned two different Mining Chests");

            // And again from a freshly planned layout of the same seed, which is what actually
            // happens in game: the structure start is rebuilt, not cached across sessions.
            Optional<MiningChestPlanner.MiningChestPlan> replanned =
                    MiningChestPlanner.plan(plan(seed, DungeonSize.MEDIUM), config);
            assertEquals(first, replanned,
                    "seed " + seed + " planned a different chest on a re-planned layout");
        }
    }

    /**
     * <strong>The chest lands near the bottom.</strong> Mark, 2026-08-31: "in a 4 level dungeon, the
     * player shouldn't get a big Mining Chest on level 1."
     *
     * <p>The haul is the <em>whole</em> dungeon's excavation wherever the chest ends up, so one found
     * on floor 0 would hand over the deep floors' diamonds without the player having gone down for
     * them. Asserted as a distribution rather than a rule &mdash; a guaranteed floor is a solved
     * dungeon, so shallow placements are permitted and merely rare.</p>
     *
     * <p>The bar is the bottom half of the floors. At the shipped {@code depthBias} of 3 the
     * arithmetic says a four-floor dungeon puts 91% of its weight there; 75% leaves room for the
     * two-floor dungeons, where the bottom half is one floor of two and the best achievable is 89%.</p>
     */
    @Test
    void theChestIsWeightedTowardTheBottomOfTheDungeon() {
        MiningConfig config = config();
        int deep = 0;
        int total = 0;
        int onFloorZeroOfATallDungeon = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonLayout layout = plan(seed, DungeonSize.LARGE);
            int floors = layout.getFloors().size();
            Optional<MiningChestPlanner.MiningChestPlan> plan =
                    MiningChestPlanner.plan(layout, config);
            if (plan.isEmpty() || floors < 2) {
                continue;
            }
            total++;
            if (plan.get().floorIndex() >= floors / 2) {
                deep++;
            }
            if (floors >= 4 && plan.get().floorIndex() == 0) {
                onFloorZeroOfATallDungeon++;
            }
        }
        assertTrue(total > 0, "no multi-floor LARGE dungeon planned a chest, so this proved nothing");
        double share = deep / (double) total;
        assertTrue(share >= 0.75D, String.format(
                "only %.0f%% of Mining Chests (%d of %d) landed in the bottom half of the dungeon;"
                        + " raise depthBias in mining_config", share * 100, deep, total));
        assertTrue(onFloorZeroOfATallDungeon <= 1, onFloorZeroOfATallDungeon
                + " chests landed on floor 0 of a dungeon four or more floors deep, which is the"
                + " exact case Mark called out");
    }

    /** Exactly one plan per dungeon, and it names a room that actually exists on that floor. */
    @Test
    void thePlanNamesARealRoomOnItsOwnFloor() {
        MiningConfig config = config();
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonLayout layout = plan(seed, DungeonSize.MEDIUM);
            Optional<MiningChestPlanner.MiningChestPlan> plan =
                    MiningChestPlanner.plan(layout, config);
            if (plan.isEmpty()) {
                continue;
            }
            MiningChestPlanner.MiningChestPlan chest = plan.get();
            boolean found = layout.getFloors().stream()
                    .filter(floor -> floor.getFloorIndex() == chest.floorIndex())
                    .flatMap(floor -> floor.getRooms().stream())
                    .anyMatch(room -> room.getId() == chest.roomId()
                            && room.getRole().isProcedurallyBuilt() && room.getTemplateId() == null);
            assertTrue(found, "seed " + seed + " planned a chest for floor " + chest.floorIndex()
                    + " room " + chest.roomId() + ", which is not a procedurally built room on that"
                    + " floor -- nothing would ever place it");
        }
    }

    /**
     * The two ways of paying back nothing, both of which are normal rather than failures: a datapack
     * that removed the file (an empty ore table), and one that turned the feature off.
     *
     * <p>Asserted because "no chest" and "an empty chest" are very different outcomes and only one of
     * them is acceptable &mdash; an empty chest costs the player a walk to find out it was empty.</p>
     */
    @Test
    void anEmptyTableOrAZeroFractionPlansNoChestAtAll() {
        DungeonLayout layout = plan(1L, DungeonSize.LARGE);
        assertTrue(MiningChestPlanner.plan(layout, MiningConfig.DEFAULT).isEmpty(),
                "the empty fallback table planned a chest, which could only be an empty one");

        MiningConfig off = new MiningConfig(0.0D, MiningConfig.DEFAULT_DEPTH_BIAS,
                config().ores());
        assertTrue(MiningChestPlanner.plan(layout, off).isEmpty(),
                "payoutFraction 0 still planned a chest");
    }

    /**
     * A shallow dungeon pays back no diamond, and a deep one may.
     *
     * <p>The property the whole depth-keyed table exists for, and the one that would silently vanish
     * if {@code OreBand#overlapFraction} were ever simplified to test a floor's own Y: SMALL never
     * reaches Y 16, so a diamond in a SMALL dungeon's chest is ore that dungeon never destroyed.</p>
     */
    @Test
    void aShallowDungeonNeverPaysBackDiamond() {
        MiningConfig config = config();
        for (long seed = 0; seed < SEEDS; seed++) {
            Optional<MiningChestPlanner.MiningChestPlan> plan =
                    MiningChestPlanner.plan(plan(seed, DungeonSize.SMALL), config);
            if (plan.isEmpty()) {
                continue;
            }
            List<MiningHaul.Stack> stacks = plan.get().haul().stacks();
            assertTrue(stacks.stream().noneMatch(s -> s.item().equals("minecraft:diamond")),
                    "a SMALL dungeon (seed " + seed + ") paid back diamond, which it never dug"
                            + " through: " + plan.get().haul());
        }
    }
}
