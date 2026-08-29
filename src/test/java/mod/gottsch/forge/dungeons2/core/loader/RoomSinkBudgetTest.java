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
import mod.gottsch.forge.dungeons2.core.loader.JigsawChains.Seat;
import mod.gottsch.forge.dungeons2.core.loader.JigsawChains.Template;
import mod.gottsch.forge.dungeons2.core.loader.JigsawChains.Walk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Whether a shipped room ASSEMBLY fits the vertical budget of the floor that has to hold it.
 *
 * <h2>What a room's door markers mean vertically</h2>
 * <p>{@code DungeonStructure#seatRoomOnWalkingPlane} drops an assembled room so its lowest
 * {@code dungeons2:door} / {@code dungeons2:connector} marker lands on the floor's walking plane.
 * That is forced: a template has no negative local Y, so the only way to author anything under the
 * walking plane &mdash; a sunken court, a cell block below the main level &mdash; is to lift the
 * markers off local 0 and fill the rows beneath. The marker row therefore <em>is</em> the walking
 * plane, and everything else is measured from it.</p>
 *
 * <h2>Two budgets, one plane, and both are silent when broken</h2>
 * <ul>
 *   <li><strong>Below</strong>, a floor owns {@code sinkOffset} rows and no more. Past that come the
 *       stone buffer of {@code gapBetweenFloors} and then the ceiling of the floor beneath. The
 *       runtime seats an over-deep room anyway and warns, because refusing would leave its doors
 *       unreachable, which is worse.</li>
 *   <li><strong>Above</strong>, a floor owns {@code ceilingBudget()} rows counting the walking plane
 *       itself. {@code DungeonStackPlanner#pickRoomHeight} clamps a PROCEDURAL room to it; nothing
 *       clamps an authored one, so a template cut too tall pushes its ceiling through
 *       {@code gapBetweenFloors} into the floor above and nothing anywhere says so.</li>
 * </ul>
 *
 * <h2>Why this walks the chain instead of measuring one file</h2>
 * <p>The first version of this gate read one template's lowest marker and called that the sink. It
 * passed {@code 12x29_sunken_hallway_1}, which sinks a full 5 &mdash; because the sink is not in any
 * one file. The middle piece carries its join marker at local Y 5, so vanilla seats that piece five
 * blocks LOWER than the piece it hangs off; the rows below the walking plane belong to a template
 * whose own markers are at 0, and the piece that is actually deep has no door marker at all and was
 * skipped entirely. A per-file reading cannot see that, and the deepest and tallest pieces of a
 * chain are usually the middle ones. So the chain is walked and seated exactly as
 * {@link TransitionSpanTest} walks the transitions, and the budget is measured across every piece
 * at once.</p>
 *
 * <h2>The bounds are read, not copied</h2>
 * <p>Both come off {@code DungeonGenerationConfig.DEFAULT}. Comparing against a hand-written 5 and
 * 15 would pass every room the day it was written and say nothing the day the pitch moves &mdash;
 * the fault {@code TransitionSpanTest} describes for its own hand-copied pitch, and the one that
 * left #29's backlog row claiming {@code sinkOffset} was still 0 two days after it was 5.</p>
 */
class RoomSinkBudgetTest {

    /** Both categories are seated by the same code path, so both answer to the same budget. */
    private static final Map<String, String> CATEGORIES = Map.of(
            "/data/dungeons2/structures/rooms", "/data/dungeons2/worldgen/template_pool/rooms",
            "/data/dungeons2/structures/end_rooms", "/data/dungeons2/worldgen/template_pool/end_rooms");

    /** One assembled room, reduced to the three numbers the budgets are about. */
    private record Fit(String trail, int sink, int above) {
    }

    @Test
    void noShippedRoomAssemblySpillsOutOfItsFloor() {
        int sinkBudget = DungeonGenerationConfig.DEFAULT.sinkOffset();
        int ceilingBudget = DungeonGenerationConfig.DEFAULT.ceilingBudget();

        List<String> tooDeep = new ArrayList<>();
        List<String> tooTall = new ArrayList<>();
        int measured = 0;

        for (Map.Entry<String, String> category : CATEGORIES.entrySet()) {
            Map<String, Template> shipped =
                    JigsawChains.reachable(JigsawChains.templates(category.getKey()), category.getValue());
            if (shipped.isEmpty()) {
                // An empty category is not this gate's business -- PoolWiringTest reports a pool
                // that names nothing, and end_rooms was legitimately empty until #46 was authored.
                continue;
            }
            // Only a template a `normal.json` names is a START. The middle pieces of a chain are
            // named by their own sub-pool and reached THROUGH the start; walking from one would
            // measure a fragment of an assembly that never generates on its own.
            Set<String> starts = JigsawChains.namedBy(category.getValue(), "normal.json");
            for (String start : starts) {
                if (!shipped.containsKey(start)) {
                    continue;   // PoolWiringTest's business, not this gate's.
                }
                for (Walk walk : JigsawChains.walk(start, shipped)) {
                    if (walk.failed()) {
                        continue;   // A broken chain is PoolWiringTest's business too.
                    }
                    Fit fit = measure(walk);
                    if (fit == null) {
                        continue;   // No door marker anywhere on the chain: nothing to measure from.
                    }
                    measured++;
                    if (fit.sink() > sinkBudget) {
                        tooDeep.add(fit.trail() + " sinks " + fit.sink() + " (budget " + sinkBudget + ")");
                    }
                    if (fit.above() > ceilingBudget) {
                        tooTall.add(fit.trail() + " stands " + fit.above() + " tall from the walking"
                                + " plane (budget " + ceilingBudget + ")");
                    }
                }
            }
        }

        assertFalse(measured == 0,
                "no room assembly was measured at all -- every start was unreachable or markerless,"
                        + " so this gate is passing vacuously");

        List<String> problems = new ArrayList<>();
        if (!tooDeep.isEmpty()) {
            problems.add(tooDeep.size() + " room assembly/assemblies sink past the floor's"
                    + " sinkOffset. Every row under the lowest door marker is spent from that"
                    + " budget; past it the room eats the stone buffer between floors:\n    "
                    + String.join("\n    ", tooDeep));
        }
        if (!tooTall.isEmpty()) {
            problems.add(tooTall.size() + " room assembly/assemblies are taller than the floor's"
                    + " ceiling budget. The ceiling pushes through gapBetweenFloors into the floor"
                    + " above, and nothing at runtime reports it:\n    "
                    + String.join("\n    ", tooTall));
        }
        if (!problems.isEmpty()) {
            fail(String.join("\n\n  ", problems)
                    + "\n\n  Fix by re-cutting the template(s), or change floorHeight / sinkOffset"
                    + " in generation_config -- but read that file's _comment first: the pitch is"
                    + " what every transition and entrance chain was cut to span.");
        }
    }

    /**
     * The chain's depth below and height above its walking plane, or null if no piece on it carries
     * a door or connector marker.
     *
     * <p>Every quantity is relative to the START piece's local Y 0, which is the space
     * {@link JigsawChains.Seat#originY} is already in. The walking plane is the lowest marker
     * anywhere on the chain, matching {@code scanRoomGeometry}'s minimum.</p>
     */
    private static Fit measure(Walk walk) {
        Integer plane = null;
        int bottom = Integer.MAX_VALUE;
        int top = Integer.MIN_VALUE;
        for (Seat seat : walk.seats()) {
            bottom = Math.min(bottom, seat.originY());
            top = Math.max(top, seat.originY() + seat.template().height() - 1);
            for (int doorY : seat.template().doorYs()) {
                int at = seat.originY() + doorY;
                plane = plane == null ? at : Math.min(plane, at);
            }
        }
        if (plane == null) {
            return null;
        }
        return new Fit(walk.trail(), plane - bottom, top - plane + 1);
    }

}
