package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.config.FloorPatternConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPatternSelectorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void emptyElementListFallsBackToBasic() {
        FloorPatternConfig config = new FloorPatternConfig(List.of());
        assertInstanceOf(BasicFloorGenerator.class, FloorPatternSelector.select(config, RandomSource.create(1)));
    }

    @Test
    void unrecognizedTypeFallsBackToBasic() {
        FloorPatternConfig config = new FloorPatternConfig(List.of(new FloorPatternEntry("nonsense", 1, 0)));
        assertInstanceOf(BasicFloorGenerator.class, FloorPatternSelector.select(config, RandomSource.create(1)));
    }

    @Test
    void singleBorderEntryAlwaysSelectsBorder() {
        FloorPatternConfig config = new FloorPatternConfig(List.of(new FloorPatternEntry("border", 1, 2)));
        for (long seed = 0; seed < 20; seed++) {
            assertInstanceOf(FloorBorderPatternProvider.class,
                    FloorPatternSelector.select(config, RandomSource.create(seed)));
        }
    }

    @Test
    void weightedPickReturnsBothTypesOverManyRolls() {
        FloorPatternConfig config = new FloorPatternConfig(List.of(
                new FloorPatternEntry("empty", 1, 0),
                new FloorPatternEntry("border", 1, 2)));
        RandomSource random = RandomSource.create(42);
        boolean sawEmpty = false;
        boolean sawBorder = false;
        for (int i = 0; i < 200; i++) {
            IDungeonFloorGenerator gen = FloorPatternSelector.select(config, random);
            sawEmpty |= gen instanceof BasicFloorGenerator;
            sawBorder |= gen instanceof FloorBorderPatternProvider;
        }
        assertTrue(sawEmpty, "should have rolled 'empty' at least once in 200 tries");
        assertTrue(sawBorder, "should have rolled 'border' at least once in 200 tries");
    }
}
