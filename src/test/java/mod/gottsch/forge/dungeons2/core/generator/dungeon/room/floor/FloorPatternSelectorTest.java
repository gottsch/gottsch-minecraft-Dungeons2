package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.floor.BorderFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.CheckerboardFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.CompositeFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.CrossFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.PlainFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.SpeckleFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.SpokesFloorPattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The pattern-to-generator mapping, and the three-tier precedence in
 * {@link FloorPatternSelector#generatorFor}.
 *
 * <p>Each pattern now builds its own generator, so most of what this used to assert about the
 * selector is really an assertion about the pattern &mdash; kept here anyway, because what matters
 * to a room is still "this authored entry draws that provider".</p>
 */
class FloorPatternSelectorTest {

    /** Plain stone_bricks base blocks -- what an unmarked cell renders as. */
    private static final FloorConfig CONFIG =
            new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BorderFloorPattern border() {
        return new BorderFloorPattern(2, "minecraft:andesite",
                "minecraft:polished_andesite", "minecraft:polished_andesite");
    }

    private static CheckerboardFloorPattern checkerboard() {
        return new CheckerboardFloorPattern("minecraft:stone_bricks", "minecraft:polished_andesite");
    }

    private static SpeckleFloorPattern speckle() {
        return new SpeckleFloorPattern("minecraft:cobblestone", "minecraft:packed_mud", 0.12);
    }

    private static IDungeonFloorGenerator draw(FloorPattern pattern) {
        return FloorPatternSelector.toGenerator(new FloorPatternEntry(pattern), CONFIG);
    }

    // ---------- each pattern builds its own provider ----------

    @Test
    void eachPatternMapsToItsProvider() {
        assertInstanceOf(BasicFloorGenerator.class, draw(PlainFloorPattern.INSTANCE));
        assertInstanceOf(FloorBorderPatternProvider.class, draw(border()));
        assertInstanceOf(CheckerboardFloorPatternProvider.class, draw(checkerboard()));
        assertInstanceOf(RandomSpeckleFloorPatternProvider.class, draw(speckle()));
        assertInstanceOf(CrossFloorPatternProvider.class,
                draw(new CrossFloorPattern(1, "minecraft:polished_andesite")));
        assertInstanceOf(RadialSpokesFloorPatternProvider.class,
                draw(new SpokesFloorPattern(4, "minecraft:polished_andesite")));
    }

    /**
     * An unresolvable BLOCK id still degrades the entry to plain floor. Only the unknown
     * <em>type</em> policy changed with the registry (that is now a load error and is asserted in
     * {@code FloorPatternRegistryTest}); block-id policy was left alone deliberately.
     */
    @Test
    void anUnresolvableBlockIdStillDegradesToPlain() {
        assertInstanceOf(BasicFloorGenerator.class,
                draw(new CheckerboardFloorPattern("minecraft:not_a_real_block", "minecraft:stone_bricks")));
        assertInstanceOf(BasicFloorGenerator.class,
                draw(new CrossFloorPattern(1, "minecraft:not_a_real_block")));
        assertInstanceOf(BasicFloorGenerator.class,
                draw(new BorderFloorPattern(2, "minecraft:not_a_real_block",
                        "minecraft:stone_bricks", "minecraft:stone_bricks")));
    }

    // ---------- composite ----------

    @Test
    void compositeWiresACheckerboardBaseWithABorderOverlay() {
        assertInstanceOf(CompositeFloorPatternProvider.class,
                draw(new CompositeFloorPattern(List.of(checkerboard(), border()))));
    }

    @Test
    void compositeWithNoGeneratorsDegradesToPlain() {
        assertInstanceOf(BasicFloorGenerator.class, draw(new CompositeFloorPattern(List.of())));
    }

    /**
     * A layer whose provider is not overlay-capable is silently skipped rather than wrapping the
     * base twice. Still silent, and still deliberately so: whether a pattern can overlay is a
     * property of its provider, which no codec can see at decode time.
     */
    @Test
    void compositeSkipsNonOverlayCapableLayers() {
        assertInstanceOf(CompositeFloorPatternProvider.class,
                draw(new CompositeFloorPattern(List.of(checkerboard(), checkerboard()))));
    }

    @Test
    void crossAndSpokesAreUsableAsCompositeOverlays() {
        assertInstanceOf(CompositeFloorPatternProvider.class,
                draw(new CompositeFloorPattern(List.of(checkerboard(),
                        new CrossFloorPattern(1, "minecraft:polished_andesite"),
                        new SpokesFloorPattern(4, "minecraft:polished_andesite")))));
    }

    // ---------- precedence: scheme slot > band pattern > plain ----------

    /** {@link #CONFIG}, plus a band-level default of speckled floor. */
    private static FloorConfig configPaving(FloorPattern pattern) {
        return new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks",
                Optional.of(new FloorPatternEntry(pattern)));
    }

    @Test
    void aBandsOwnPatternIsUsedWhenTheSchemeNamesNoFloor() {
        assertInstanceOf(RandomSpeckleFloorPatternProvider.class,
                FloorPatternSelector.generatorFor(Optional.empty(), configPaving(speckle())),
                "a scheme with no floor slot must fall through to the band's own paving,"
                        + " not straight to plain floor");
    }

    @Test
    void aSchemesOwnFloorBeatsTheBandsPattern() {
        assertInstanceOf(CheckerboardFloorPatternProvider.class,
                FloorPatternSelector.generatorFor(
                        Optional.of(new FloorPatternEntry(checkerboard())), configPaving(speckle())),
                "a room that asked for a mosaic asked for it at every depth -- the band is the"
                        + " default underneath, never an override on top");
    }

    @Test
    void withNoSchemeFloorAndNoBandPatternTheFloorIsStillPlain() {
        assertInstanceOf(BasicFloorGenerator.class,
                FloorPatternSelector.generatorFor(Optional.empty(), CONFIG));
    }
}
