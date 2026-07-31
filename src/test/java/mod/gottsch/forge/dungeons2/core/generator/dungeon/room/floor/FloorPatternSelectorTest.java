package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FloorPatternSelectorTest {

    /** Plain stone_bricks base blocks -- what an unmarked cell renders as. */
    private static final FloorConfig CONFIG =
            new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks");

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
                RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                List.of());
    }

    /** A "checkerboard" entry with both required blocks filled in with valid vanilla ids. */
    private static FloorPatternEntry checkerboardEntry(int weight) {
        return new FloorPatternEntry(
                "checkerboard", weight, 0,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("minecraft:granite"), Optional.of("minecraft:diorite"),
                RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                List.of());
    }

    /** A "speckle" entry with both required blocks filled in with valid vanilla ids. */
    private static FloorPatternEntry speckleEntry(int weight) {
        return new FloorPatternEntry(
                "speckle", weight, 0,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("minecraft:granite"), Optional.of("minecraft:diorite"),
                RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                List.of());
    }

    /** A scheme with no floor slot renders the undecorated floor, not a hardcoded fallback. */
    @Test
    void absentFloorSlotFallsBackToBasic() {
        assertInstanceOf(BasicFloorGenerator.class,
                FloorPatternSelector.generatorFor(Optional.empty(), CONFIG));
    }

    @Test
    void unrecognizedTypeFallsBackToBasic() {
        FloorPatternEntry entry = new FloorPatternEntry("nonsense", 1, 0);
        assertInstanceOf(BasicFloorGenerator.class, FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    @Test
    void borderEntryWithoutBlocksFallsBackToBasic() {
        // No Java-side default for any of the three block slots -- the motif config must
        // supply them, or the entry degrades to plain rather than guessing a block.
        FloorPatternEntry entry = new FloorPatternEntry("border", 1, 2);
        assertInstanceOf(BasicFloorGenerator.class, FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    @Test
    void borderEntryMapsToBorderProvider() {
        FloorPatternEntry entry = borderEntry(1, 2);
        assertInstanceOf(FloorBorderPatternProvider.class,
                FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    @Test
    void checkerboardEntryMapsToCheckerboardProvider() {
        FloorPatternEntry entry = checkerboardEntry(1);
        assertInstanceOf(CheckerboardFloorPatternProvider.class,
                FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    @Test
    void speckleEntryMapsToSpeckleProvider() {
        FloorPatternEntry entry = speckleEntry(1);
        assertInstanceOf(RandomSpeckleFloorPatternProvider.class,
                FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    /** An entry with one accent block, filled in with a valid vanilla id. */
    private static FloorPatternEntry oneBlockEntry(String type, int weight) {
        return new FloorPatternEntry(
                type, weight, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("minecraft:chiseled_stone_bricks"), Optional.empty(),
                RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                List.of());
    }

    @Test
    void crossEntryMapsToCrossProvider() {
        FloorPatternEntry entry = oneBlockEntry("cross", 1);
        assertInstanceOf(CrossFloorPatternProvider.class,
                FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    @Test
    void spokesEntryMapsToSpokesProvider() {
        FloorPatternEntry entry = oneBlockEntry("spokes", 1);
        assertInstanceOf(RadialSpokesFloorPatternProvider.class,
                FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    @Test
    void crossAndSpokesWithoutABlockFallBackToBasic() {
        for (String type : new String[]{"cross", "spokes"}) {
            FloorPatternEntry entry = new FloorPatternEntry(type, 1, 0);
            assertInstanceOf(BasicFloorGenerator.class,
                    FloorPatternSelector.toGenerator(entry, CONFIG),
                    type + " with no primaryBlock should degrade to plain");
        }
    }

    /** Both new patterns are overlay-capable, so they survive a composite's overlay slot. */
    @Test
    void crossAndSpokesAreUsableAsCompositeOverlays() {
        for (String type : new String[]{"cross", "spokes"}) {
            FloorPatternEntry composite = new FloorPatternEntry(
                    "composite", 1, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                    CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                    List.of(checkerboardEntry(1), oneBlockEntry(type, 1)));
            assertInstanceOf(CompositeFloorPatternProvider.class,
                    FloorPatternSelector.toGenerator(composite, CONFIG));
        }
    }

    @Test
    void compositeEntryWithNoGeneratorsFallsBackToBasic() {
        FloorPatternEntry entry = new FloorPatternEntry("composite", 1, 0);
        assertInstanceOf(BasicFloorGenerator.class, FloorPatternSelector.toGenerator(entry, CONFIG));
    }

    @Test
    void compositeEntryWiresCheckerboardBaseWithBorderOverlay() {
        FloorPatternEntry checkerboardLayer = checkerboardEntry(1);
        FloorPatternEntry borderLayer = borderEntry(1, 2);
        FloorPatternEntry composite = new FloorPatternEntry(
                "composite", 1, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                List.of(checkerboardLayer, borderLayer));

        IDungeonFloorGenerator generator =
                FloorPatternSelector.toGenerator(composite, CONFIG);
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
                CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                List.of(base, notOverlayable));

        IDungeonFloorGenerator generator =
                FloorPatternSelector.toGenerator(composite, CONFIG);
        assertInstanceOf(CompositeFloorPatternProvider.class, generator);
    }

}
