package mod.gottsch.forge.dungeons2.core.generator.dungeon.counter;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss&rarr;counter-item chest plan (#97): gated on the boss, rolled at {@link
 * CounterItemPlanner#CHANCE}, deterministic, and only ever in an ordinary procedural room.
 *
 * @author Mark Gottschling on Sep 24, 2026
 */
class CounterItemPlannerTest {

    private static final int SEEDS = 200;
    private static final String MOTIF = "classic";
    private static final String COLOSSUS = "dungeons2:stone_colossus";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DungeonLayout plan(long seed, String boss) {
        DungeonLayout layout = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, MOTIF,
                new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withCorridorWidth(3)
                .withCorridorStyles(DungeonStructure.corridorStyleWeights(
                        MotifConfigs.load(MOTIF).corridor()))
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
        // No authored boss room seats without a template catalog, so the boss is set by hand.
        layout.setBoss(boss);
        return layout;
    }

    @Test
    void noBossOrAnUncounteredBossPlansNothing() {
        for (long seed = 0; seed < 20; seed++) {
            assertTrue(CounterItemPlanner.plan(plan(seed, null)).isEmpty(), "seed " + seed);
            assertTrue(CounterItemPlanner.plan(plan(seed, "dungeons2:minotaur")).isEmpty(),
                    "seed " + seed);
        }
    }

    @Test
    void aColossusDungeonHidesTheBootsAboutOneTimeInFour() {
        int planned = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonLayout layout = plan(seed, COLOSSUS);
            Optional<CounterItemPlanner.CounterChestPlan> plan = CounterItemPlanner.plan(layout);
            assertEquals(plan, CounterItemPlanner.plan(plan(seed, COLOSSUS)),
                    "seed " + seed + " planned two different counter chests");
            if (plan.isEmpty()) {
                continue;
            }
            planned++;
            assertEquals("dungeons2:chests/counter_stone_colossus", plan.get().lootTable());
            RoomData room = roomOf(layout, plan.get());
            assertEquals(RoomRole.NORMAL, room.getRole(), "seed " + seed);
            assertEquals(null, room.getTemplateId(), "seed " + seed);
        }
        double share = planned / (double) SEEDS;
        assertTrue(share > 0.15D && share < 0.35D,
                String.format("%.0f%% of colossus dungeons planned the boots, expected ~25%%", share * 100));
    }

    /** Both Beholder-kin hide the Mirror Shield, from the one shared table. */
    @Test
    void bothBeholderkinHideTheMirrorShield() {
        for (String boss : new String[]{"dungeons2:beholder", "dungeons2:death_tyrant"}) {
            int planned = 0;
            for (long seed = 0; seed < 60; seed++) {
                Optional<CounterItemPlanner.CounterChestPlan> plan =
                        CounterItemPlanner.plan(plan(seed, boss));
                if (plan.isPresent()) {
                    planned++;
                    assertEquals("dungeons2:chests/counter_beholder", plan.get().lootTable(), boss);
                }
            }
            assertTrue(planned > 0, boss + " never planned a Mirror Shield chest in 60 seeds");
        }
    }

    /** Every table the planner can name must ship, or the chest is silently empty. */
    @Test
    void everyCounterTableShips() {
        for (String table : CounterItemPlanner.COUNTER_TABLES.values()) {
            String[] id = table.split(":");
            Path path = Path.of("src/main/resources/data", id[0], "loot_tables", id[1] + ".json");
            assertTrue(Files.exists(path), "missing loot table " + path);
        }
    }

    private static RoomData roomOf(DungeonLayout layout, CounterItemPlanner.CounterChestPlan plan) {
        for (FloorLayout floor : layout.getFloors()) {
            if (floor.getFloorIndex() != plan.floorIndex()) {
                continue;
            }
            for (RoomData room : floor.getRooms()) {
                if (room.getId() == plan.roomId()) {
                    return room;
                }
            }
        }
        throw new AssertionError("plan names a room that does not exist: " + plan);
    }
}
