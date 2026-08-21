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
package mod.gottsch.forge.dungeons2.core.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Backlog #46's {@code end_rooms} pool ships <strong>empty</strong>, on purpose, and that is a
 * contract rather than an oversight.
 *
 * <h2>Why an empty file exists at all</h2>
 * <p>It is the path and the schema, there to author into: a boss template is a content decision, and
 * an empty pool documents where one goes without making that decision. The whole feature degrades to
 * today's procedural terminal room while it stays empty.</p>
 *
 * <h2>Why the emptiness is load-bearing</h2>
 * <p>{@code DungeonStackPlanner.placeBossRoom} draws an assembly seed <em>per attempt</em>, so an
 * assembler that cannot produce anything still consumes randomness — which on the bottom floor of
 * every dungeon would re-roll every existing world for a feature that is switched off. That is why
 * {@code DungeonStructure} wires the assembler only when the pool resolves <strong>and</strong>
 * {@code size() &gt; 0}.
 *
 * <p>The presence check alone was not enough, and this file is exactly why: a present-but-empty pool
 * resolves perfectly happily, so testing only for presence would have switched the feature on the
 * moment this placeholder shipped. The guard would have been defeated by the file added to document
 * it.</p>
 *
 * <h2>This test is a hand-off note</h2>
 * <p><strong>When a real boss template is authored, this test fails</strong> — deliberately. That is
 * the signal to delete it and add an {@code end_rooms} row to {@link PoolWiringTest}'s
 * {@code CATEGORIES}, which is where a pool with actual elements belongs.</p>
 */
class EndRoomPoolPlaceholderTest {

    private static final String POOL =
            "/data/dungeons2/worldgen/template_pool/end_rooms/classic/normal.json";

    private static JsonObject pool() {
        try (InputStream in = EndRoomPoolPlaceholderTest.class.getResourceAsStream(POOL)) {
            if (in == null) {
                return fail("the end_rooms pool is missing from " + POOL + " -- #46 has no path to "
                        + "author a boss room into");
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception unreadable) {
            return fail("could not read " + POOL + ": " + unreadable);
        }
    }

    /** It exists, it parses, and it names itself what {@code bossRoomStartPool} asks for. */
    @Test
    void theEndRoomPoolExistsAndNamesItselfCorrectly() {
        JsonObject json = pool();
        assertEquals("dungeons2:end_rooms/classic/normal", json.get("name").getAsString(),
                "the pool's own name must match DungeonStructure#bossRoomStartPool, or it resolves "
                        + "to nothing and the boss room silently never appears");
        assertTrue(json.has("elements"), "a template pool with no elements key does not load at all");
    }

    /**
     * The one that matters. See the class note: emptiness is what keeps the feature inert, and the
     * failure message is the instruction for whoever makes it non-empty.
     */
    @Test
    void theEndRoomPoolIsStillEmpty() {
        int elements = pool().getAsJsonArray("elements").size();
        if (elements != 0) {
            fail("the end_rooms pool now has " + elements + " element(s), so #46's boss room is "
                    + "LIVE. That is the intended end state -- now finish the hand-off: delete this "
                    + "test and add an end_rooms row to PoolWiringTest's CATEGORIES, so the pool is "
                    + "checked the way every other populated category is. Also re-measure the SMALL "
                    + "fallback rate (TerminalRoomFitProbe) against the real template sizes.");
        }
    }
}
