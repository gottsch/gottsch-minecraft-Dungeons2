package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomSpeckleFloorPatternProviderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void zeroProbabilityNeverPlacesTheAccent() {
        RandomSpeckleFloorPatternProvider provider =
                new RandomSpeckleFloorPatternProvider(0.0, Blocks.GRANITE, Blocks.DIORITE);
        List<BlockPlacement> out = new ArrayList<>();
        provider.build(10, 10, 0, 0, 0, RandomSource.create(1), out);
        for (BlockPlacement p : out) {
            assertEquals("minecraft:granite", p.getBlockId());
        }
        assertEquals(100, out.size());
    }

    @Test
    void oneProbabilityAlwaysPlacesTheAccent() {
        RandomSpeckleFloorPatternProvider provider =
                new RandomSpeckleFloorPatternProvider(1.0, Blocks.GRANITE, Blocks.DIORITE);
        List<BlockPlacement> out = new ArrayList<>();
        provider.build(5, 5, 0, 0, 0, RandomSource.create(1), out);
        for (BlockPlacement p : out) {
            assertEquals("minecraft:diorite", p.getBlockId());
        }
    }

    @Test
    void neitherSlotHasAJavaSideDefault() {
        // No motif-scoped fallback block for either slot -- floor_pattern_config is the single
        // source of truth, so a missing block is a construction-time error, not a silent guess.
        assertThrows(NullPointerException.class,
                () -> new RandomSpeckleFloorPatternProvider(0.05, null, Blocks.DIORITE));
        assertThrows(NullPointerException.class,
                () -> new RandomSpeckleFloorPatternProvider(0.05, Blocks.GRANITE, null));
    }

    @Test
    void midProbabilityProducesBothBlocksOverAWideFloor() {
        RandomSpeckleFloorPatternProvider provider =
                new RandomSpeckleFloorPatternProvider(0.2, Blocks.GRANITE, Blocks.DIORITE);
        List<BlockPlacement> out = new ArrayList<>();
        provider.build(20, 20, 0, 0, 0, RandomSource.create(7), out);
        boolean sawBase = out.stream().anyMatch(p -> p.getBlockId().equals("minecraft:granite"));
        boolean sawAccent = out.stream().anyMatch(p -> p.getBlockId().equals("minecraft:diorite"));
        assertTrue(sawBase, "should have placed the base block at least once over a 400-cell floor");
        assertTrue(sawAccent, "should have placed the accent block at least once over a 400-cell floor");
    }
}
