package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.config.FloorPatternConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPatternSelectorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A "border" entry with all three required blocks filled in with valid vanilla ids. */
    private static FloorPatternEntry borderEntry(int weight, int inset) {
        return new FloorPatternEntry(
                "border", weight, inset,
                Optional.of("minecraft:andesite"), Optional.of("minecraft:polished_andesite"),
                Optional.of("minecraft:polished_andesite"), Optional.empty(), Optional.empty(),
                RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY, List.of());
    }

    /** A "checkerboard" entry with both required blocks filled in with valid vanilla ids. */
    private static FloorPatternEntry checkerboardEntry(int weight) {
        return new FloorPatternEntry(
                "checkerboard", weight, 0,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("minecraft:granite"), Optional.of("minecraft:diorite"),
                RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY, List.of());
    }

    /** A "speckle" entry with both required blocks filled in with valid vanilla ids. */
    private static FloorPatternEntry speckleEntry(int weight) {
        return new FloorPatternEntry(
                "speckle", weight, 0,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("minecraft:granite"), Optional.of("minecraft:diorite"),
                RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY, List.of());
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
    void borderEntryWithoutBlocksFallsBackToBasic() {
        // No Java-side default for any of the three block slots -- floor_pattern_config must
        // supply them, or the entry degrades to plain rather than guessing a block.
        FloorPatternConfig config = new FloorPatternConfig(List.of(new FloorPatternEntry("border", 1, 2)));
        assertInstanceOf(BasicFloorGenerator.class, FloorPatternSelector.select(config, RandomSource.create(1)));
    }

    @Test
    void singleBorderEntryAlwaysSelectsBorder() {
        FloorPatternConfig config = new FloorPatternConfig(List.of(borderEntry(1, 2)));
        for (long seed = 0; seed < 20; seed++) {
            assertInstanceOf(FloorBorderPatternProvider.class,
                    FloorPatternSelector.select(config, RandomSource.create(seed)));
        }
    }

    @Test
    void singleCheckerboardEntryAlwaysSelectsCheckerboard() {
        FloorPatternConfig config = new FloorPatternConfig(List.of(checkerboardEntry(1)));
        for (long seed = 0; seed < 20; seed++) {
            assertInstanceOf(CheckerboardFloorPatternProvider.class,
                    FloorPatternSelector.select(config, RandomSource.create(seed)));
        }
    }

    @Test
    void singleSpeckleEntryAlwaysSelectsSpeckle() {
        FloorPatternConfig config = new FloorPatternConfig(List.of(speckleEntry(1)));
        for (long seed = 0; seed < 20; seed++) {
            assertInstanceOf(RandomSpeckleFloorPatternProvider.class,
                    FloorPatternSelector.select(config, RandomSource.create(seed)));
        }
    }

    @Test
    void compositeEntryWithNoGeneratorsFallsBackToBasic() {
        FloorPatternConfig config = new FloorPatternConfig(
                List.of(new FloorPatternEntry("composite", 1, 0)));
        assertInstanceOf(BasicFloorGenerator.class, FloorPatternSelector.select(config, RandomSource.create(1)));
    }

    @Test
    void compositeEntryWiresCheckerboardBaseWithBorderOverlay() {
        FloorPatternEntry checkerboardLayer = checkerboardEntry(1);
        FloorPatternEntry borderLayer = borderEntry(1, 2);
        FloorPatternEntry composite = new FloorPatternEntry(
                "composite", 1, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                List.of(checkerboardLayer, borderLayer));

        IDungeonFloorGenerator generator =
                FloorPatternSelector.select(new FloorPatternConfig(List.of(composite)), RandomSource.create(1));
        assertInstanceOf(CompositeFloorPatternProvider.class, generator);
    }

    @Test
    void compositeEntrySkipsNonOverlayCapableSubsequentLayers() {
        // A second "checkerboard" (not overlay-capable) after the base is silently dropped, same
        // graceful degradation an unrecognized top-level type gets -- it should not throw or
        // wrap the base a second time.
        FloorPatternEntry base = checkerboardEntry(1);
        FloorPatternEntry notOverlayable = checkerboardEntry(1);
        FloorPatternEntry composite = new FloorPatternEntry(
                "composite", 1, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                List.of(base, notOverlayable));

        IDungeonFloorGenerator generator =
                FloorPatternSelector.select(new FloorPatternConfig(List.of(composite)), RandomSource.create(1));
        assertInstanceOf(CompositeFloorPatternProvider.class, generator);
    }

    @Test
    void weightedPickReturnsBothTypesOverManyRolls() {
        FloorPatternConfig config = new FloorPatternConfig(List.of(
                new FloorPatternEntry("empty", 1, 0),
                borderEntry(1, 2)));
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
