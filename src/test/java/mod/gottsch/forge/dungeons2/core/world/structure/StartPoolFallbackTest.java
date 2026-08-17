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
package mod.gottsch.forge.dungeons2.core.world.structure;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Backlog #5: a motif that has not authored a pool <strong>borrows the {@code classic} one</strong>
 * rather than degrading straight past it.
 *
 * <h2>Why this is worth a fallback, and it is not about looks</h2>
 * <p>What "degrade" costs is wildly different per category. A missing <em>room</em> pool is
 * invisible &mdash; the slot is filled by an ordinary procedural room. A missing <em>entrance</em>
 * pool means the planner takes its synthetic layout and the dungeon generates with no way in. A
 * missing <em>transition</em> pool means the planner substitutes a placeholder footprint that
 * nothing renders, so the floors are reserved and doored and the staircase between them is never
 * built. Two of the three degradations produce a structurally broken dungeon, silently.</p>
 *
 * <p>Tests the decision, not the registry: {@code chooseStartPool} takes "does this pool exist" as
 * a predicate, so every branch is reachable without loading a {@code template_pool} registry or
 * inventing a second motif's worth of shipped content.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
class StartPoolFallbackTest {

    /** {@code ResourceLocation}'s constructor validates against the registry-safe charset. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Function<String, ResourceLocation> ROOMS =
            motif -> new ResourceLocation("dungeons2", "rooms/" + motif + "/normal");

    private static ResourceLocation choose(String motif, String... shipped) {
        Set<ResourceLocation> exists = Set.of(shipped).stream()
                .map(ResourceLocation::new).collect(java.util.stream.Collectors.toSet());
        return DungeonStructure.chooseStartPool(motif, ROOMS, exists::contains);
    }

    @Test
    void aMotifWithItsOwnPoolUsesIt() {
        assertEquals(new ResourceLocation("dungeons2:rooms/desert/normal"),
                choose("desert", "dungeons2:rooms/desert/normal", "dungeons2:rooms/classic/normal"),
                "a motif that authored its own pool must never borrow");
    }

    @Test
    void aMotifWithNoPoolBorrowsClassic() {
        assertEquals(new ResourceLocation("dungeons2:rooms/classic/normal"),
                choose("desert", "dungeons2:rooms/classic/normal"),
                "a half-authored motif should borrow rather than lose the piece entirely");
    }

    @Test
    void withNeitherPoolItDegrades() {
        assertNull(choose("desert"),
                "with no pool anywhere there is nothing to borrow -- the caller must still get the"
                        + " old graceful degradation rather than an exception");
    }

    /**
     * The fallback motif is not special-cased for vanity: without this branch, {@code classic}
     * missing its own pool would be reported to the log as "borrowing classic from classic".
     */
    @Test
    void theFallbackMotifDoesNotBorrowFromItself() {
        assertNull(choose("classic"),
                "classic with no pool has no second tier to try");
        assertEquals(new ResourceLocation("dungeons2:rooms/classic/normal"),
                choose("classic", "dungeons2:rooms/classic/normal"),
                "classic still resolves its own pool normally");
    }

    /**
     * The shipped state, pinned so the fallback cannot start silently carrying the real dungeon.
     * If {@code classic} ever stops authoring one of these, that is a content bug and the borrow
     * path would hide it for every other motif too.
     */
    @Test
    void classicAuthorsAllThreeOfItsOwnPools() {
        for (String pool : Set.of("entrance/classic/surface_entrance",
                "transitions/classic/shaft_bottom", "rooms/classic/normal")) {
            String path = "/data/dungeons2/worldgen/template_pool/" + pool + ".json";
            org.junit.jupiter.api.Assertions.assertNotNull(
                    StartPoolFallbackTest.class.getResource(path),
                    "classic is the fallback motif, so it must author " + pool
                            + " -- every other motif's degradation path leads here");
        }
    }
}
