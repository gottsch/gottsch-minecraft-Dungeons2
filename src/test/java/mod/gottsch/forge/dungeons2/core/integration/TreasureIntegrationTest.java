package mod.gottsch.forge.dungeons2.core.integration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Treasure2 is an OPTIONAL dependency, and this pins the one property that makes that true.
 *
 * <p>The JVM loads a class on first active use and resolves its symbolic references then. If the
 * class holding {@code isLoaded()} also named a Treasure2 type, calling the guard would load
 * Treasure2 &mdash; and on a machine without it that is a {@code NoClassDefFoundError} on exactly the
 * path that exists to avoid one. The split into {@code TreasureIntegration} (no Treasure2 types) and
 * its nested {@code Delegate} (all of them) is what prevents it.</p>
 *
 * <p>This reads the compiled class files rather than calling anything, because the failure is a
 * <em>linkage</em> property: it cannot be observed from a test that has Treasure2 on its classpath,
 * and a test that merely called {@code isLoaded()} would pass whether or not the split existed.
 * The constant pool holds every referenced type name as UTF-8, so the type names are simply
 * searched for in the bytes.</p>
 */
class TreasureIntegrationTest {

    private static byte[] bytesOf(String className) throws Exception {
        try (InputStream in = TreasureIntegrationTest.class.getClassLoader()
                .getResourceAsStream(className.replace('.', '/') + ".class")) {
            assertNotNull(in, "could not read " + className + "; the test cannot verify anything");
            return in.readAllBytes();
        }
    }

    /**
     * The internal name of Treasure2's package, not the bare mod id.
     *
     * <p>The distinction is the whole subtlety: {@code TreasureIntegration} holds
     * {@code TREASURE2 = "treasure2"} as a string literal, which also lands in the constant pool and
     * is completely harmless -- a string is not a symbolic reference to a class and loads nothing.
     * The first version of this test searched for the bare id and failed on its own mod-id
     * constant. What matters is whether a Treasure2 <em>type</em> is named.</p>
     */
    private static final String TREASURE2_TYPES = "mod/gottsch/forge/treasure2";

    private static boolean mentionsTreasure2(byte[] classFile) {
        return new String(classFile, StandardCharsets.ISO_8859_1).contains(TREASURE2_TYPES);
    }

    @Test
    void theGuardClassNamesNoTreasure2Type() throws Exception {
        assertFalse(mentionsTreasure2(bytesOf(TreasureIntegration.class.getName())),
                "TreasureIntegration must not reference any treasure2 type, or calling isLoaded()"
                        + " loads Treasure2 and crashes on machines that do not have it");
    }

    /**
     * The other half: if the delegate stopped naming Treasure2, the split would still "pass" the
     * assertion above while doing nothing at all -- so this pins that the types really did move
     * there rather than disappearing.
     */
    @Test
    void theDelegateIsWhereTheTreasure2TypesLive() throws Exception {
        assertTrue(mentionsTreasure2(bytesOf(TreasureIntegration.class.getName() + "$Delegate")),
                "the delegate should hold the treasure2 calls; if it does not, the integration is"
                        + " no longer wired to anything");
    }
}
