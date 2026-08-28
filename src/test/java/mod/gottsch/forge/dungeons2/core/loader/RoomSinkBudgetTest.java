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

import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfig;
import mod.gottsch.forge.dungeons2.core.loader.JigsawChains.Template;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gates how deep a shipped room template may sink <strong>below</strong> its own walking plane.
 *
 * <h2>What a room's door markers mean vertically</h2>
 * <p>{@code DungeonStructure} seats an assembled room so its lowest {@code dungeons2:door} /
 * {@code dungeons2:connector} marker lands on the floor's walking plane. That is forced: a template
 * has no negative local Y, so the only way to author anything under the walking plane -- a sunken
 * court, a cell block below the main level -- is to lift the markers off local 0 and fill the rows
 * beneath. The marker row therefore <em>is</em> the walking plane, and a template's lowest marker Y
 * is exactly how many rows it sinks.</p>
 *
 * <h2>Why that number needs a ceiling</h2>
 * <p>A floor owns {@code sinkOffset} blocks below its walking plane and no more; past that come the
 * stone buffer of {@code gapBetweenFloors} and then the ceiling of the floor beneath. A room that
 * sinks further still generates -- the runtime seats it and warns rather than rejecting it, because
 * refusing would leave the doors unreachable, which is worse -- but it is quietly eating a budget
 * nothing else knows it lost. This fails the build instead, at the point the template is authored.
 *
 * <p><strong>The bound is read, not copied.</strong> Comparing against a hand-written 5 would pass
 * every room the day it was written and say nothing the day {@code sinkOffset} moves -- the same
 * fault {@link TransitionSpanTest} describes for its own hand-copied pitch. Lower {@code sinkOffset}
 * and the rooms that no longer fit under it fail here.</p>
 */
class RoomSinkBudgetTest {

    private static final String ROOM_ROOT = "/data/dungeons2/structures/rooms";
    private static final String POOL_ROOT = "/data/dungeons2/worldgen/template_pool/rooms";

    @Test
    void noShippedRoomSinksPastTheFloorsSinkOffset() {
        int budget = DungeonGenerationConfig.DEFAULT.sinkOffset();
        Map<String, Template> shipped =
                JigsawChains.reachable(JigsawChains.templates(ROOM_ROOT), POOL_ROOT);
        assertFalse(shipped.isEmpty(), "no room template pool named any element");

        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, Template> entry : shipped.entrySet()) {
            List<Integer> planes = entry.getValue().doorYs();
            if (planes.isEmpty()) {
                // A room with no door or connector marker at all is a different fault, and
                // PoolWiringTest is where it is reported. Nothing to measure here.
                continue;
            }
            int sink = planes.get(0);
            if (sink > budget) {
                offenders.add(entry.getKey() + " sinks " + sink + " below its door markers");
            }
        }

        if (!offenders.isEmpty()) {
            fail(offenders.size() + " shipped room(s) sink past the floor's sinkOffset of " + budget
                    + ". A room's lowest door marker sits ON the walking plane, so every row under"
                    + " it is spent from the floor's sink budget -- lower the markers, or raise"
                    + " sinkOffset in generation_config (which is bought from room headroom):\n  "
                    + String.join("\n  ", offenders));
        }
    }
}
