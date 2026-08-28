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
import mod.gottsch.forge.dungeons2.core.loader.JigsawChains.Walk;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The entrance chain, guarded the way {@link TransitionSpanTest} guards the transitions &mdash; the
 * other end of the same mechanism, and until now the end nothing checked at all.
 *
 * <h2>Why this exists</h2>
 * <p>{@code EntrancePoolWiringTest} verifies the pools are wired; nothing verified that the chain
 * they wire <em>reaches</em>. That is backlog #52's lesson at the other end of the dungeon: an
 * unwritten contract between the {@code .nbt} files and a constant, enforced by nothing. It matters
 * now because the entrance is the one chain whose bottom marker <strong>defines floor 0's walking
 * plane</strong> &mdash; a broken entrance is not a cosmetic fault, it is a dungeon with no way
 * in.</p>
 *
 * <h2>The invariant is not the pitch</h2>
 * <p>A transition must span exactly {@code floorHeight + gapBetweenFloors}, because it joins two
 * planes that are already fixed. An entrance joins the SURFACE to floor 0 and therefore sets where
 * floor 0 is; there is no exact distance to hit. What it must do is drop <strong>far enough</strong>
 * that the tallest room floor 0 can hold still fits underground:</p>
 *
 * <pre>  drop &gt;= ceilingBudget   (floorHeight - sinkOffset)</pre>
 *
 * <p>Floor 0's ceiling sits at {@code plane + ceilingBudget - 1}, and the surface entrance sits at
 * {@code plane + drop}; a shallower drop puts a full-height room's ceiling through the surface.
 * <strong>This is exactly what a floorHeight raise puts at risk</strong>, and it is why the shipped
 * {@code generation_config} carries a warning about it: at the old budget of 10 the shipped ladder's
 * drop of 22 had twelve blocks to spare, and at 15 it has seven.</p>
 *
 * <p>Note the margin comes from the DROP, not from the room-height cap. Procedural rooms stop at 10,
 * but an authored template may be cut to the full budget, so the check uses the budget rather than
 * what the planner happens to roll.</p>
 *
 * <h2>Release-tagged, like the transition gate</h2>
 * <p>These assert that AUTHORING is finished, not that the code is correct, so they run under
 * {@code ./gradlew releaseCheck} and are excluded from {@code test}. Half-cut entrance content is
 * expected while a descent is being authored and must not turn the ordinary build red &mdash; the
 * same call the shipped-template block-out check makes.</p>
 */
@Tag("release")
class EntranceSpanTest {

    private static final String ENTRANCE_ROOT = "/data/dungeons2/structures/entrances";
    private static final String POOL_ROOT = "/data/dungeons2/worldgen/template_pool/entrance";

    /** Asked of the shipped config rather than copied, exactly as the transition gate does. */
    private static int ceilingBudget() {
        return DungeonGenerationConfig.DEFAULT.ceilingBudget();
    }

    private static Map<String, Template> shipped() {
        return JigsawChains.reachable(JigsawChains.templates(ENTRANCE_ROOT), POOL_ROOT);
    }

    /**
     * The surface pieces a chain starts from: exactly what the {@code surface_entrance} pool names.
     *
     * <p><strong>Asked of the pool, not inferred from the markers.</strong> A jigsaw names both
     * sides of every join &mdash; the ladder's top marker targets {@code surface_entrance} just as
     * the surface piece targets {@code ladder_top} &mdash; so "nothing points at it" does not
     * identify an end, and "has an outgoing and no door plane" wrongly makes every ladder a start.
     * {@code DungeonStructure} begins an entrance from
     * {@code entrance/<motif>/surface_entrance}, so this asks the same question the code does.</p>
     */
    private static Map<String, Template> starts(Map<String, Template> shipped) {
        Set<String> named = JigsawChains.namedBy(POOL_ROOT, "surface_entrance.json");
        Map<String, Template> starts = new LinkedHashMap<>();
        shipped.forEach((name, template) -> {
            if (named.contains(name)) {
                starts.put(name, template);
            }
        });
        return starts;
    }

    // ---------- the chain resolves ----------

    /**
     * Every link finds its successor, and no chain loops. The failure this catches is total: a
     * target nothing carries leaves the chain hanging in the air, floor 0 gets no walking plane
     * from it, and there is no error anywhere saying so.
     */
    @Test
    void everyEntranceChainResolves() {
        Map<String, Template> shipped = shipped();
        List<String> offenders = new ArrayList<>();
        int walked = 0;
        for (String start : starts(shipped).keySet()) {
            for (Walk walk : JigsawChains.walk(start, shipped)) {
                walked++;
                if (walk.failed()) {
                    offenders.add(walk.trail() + ": " + walk.failure());
                }
            }
        }
        assertTrue(walked > 0, "no entrance chain was walked, so this asserted nothing");
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " entrance path(s) do not resolve:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    /**
     * Every chain ends in a piece carrying a door plane. That plane IS floor 0's walking plane, so a
     * chain terminating in a piece without one descends into the ground and stops.
     */
    @Test
    void everyEntranceChainEndsOnAFloorPlane() {
        Map<String, Template> shipped = shipped();
        List<String> offenders = new ArrayList<>();
        for (String start : starts(shipped).keySet()) {
            for (Walk walk : JigsawChains.walk(start, shipped)) {
                if (!walk.failed() && walk.end().doorYs().isEmpty()) {
                    offenders.add(walk.trail() + ": ends at " + walk.endName()
                            + ", which carries no door or connector -- floor 0 would have no"
                            + " walking plane");
                }
            }
        }
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " entrance path(s) end nowhere:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    // ---------- the drop is deep enough ----------

    /**
     * <strong>The check a floorHeight raise can break.</strong> Every path must drop at least the
     * ceiling budget, or a full-height room on floor 0 pushes its ceiling through the surface.
     *
     * <p>Both shipped ladders are walked, not just whichever is found first: the pool picks between
     * {@code entrance_ladder_1} and {@code entrance_ladder_2} at random, so a raise that outgrew the
     * short one would otherwise show up in only half of worlds.</p>
     */
    @Test
    void everyEntranceDropsFarEnoughForATallRoomOnFloorZero() {
        int budget = ceilingBudget();
        Map<String, Template> shipped = shipped();
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Template> start : starts(shipped).entrySet()) {
            int surfaceY = start.getValue().outgoing().y();
            for (Walk walk : JigsawChains.walk(start.getKey(), shipped)) {
                if (walk.failed() || walk.end().doorYs().isEmpty()) {
                    continue; // reported by the two tests above
                }
                checked++;
                int planeY = walk.endOriginY() + walk.end().doorYs().get(0);
                int drop = surfaceY - planeY;
                if (drop < budget) {
                    offenders.add(walk.trail() + ": drops " + drop + " to floor 0's plane, but a"
                            + " room there may be " + budget + " high (floorHeight "
                            + DungeonGenerationConfig.DEFAULT.floorHeight() + " - sinkOffset "
                            + DungeonGenerationConfig.DEFAULT.sinkOffset() + ") -- its ceiling"
                            + " would break the surface by " + (budget - drop) + " block(s)");
                }
            }
        }
        assertTrue(checked > 0, "no entrance path reached a floor plane, so this asserted nothing");
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " entrance path(s) do not drop far enough for the current floor"
                    + " budget of " + budget + ". Re-cut the descent, or lower floorHeight:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    /** Everything above passes vacuously if the templates are not found or the markers not read. */
    @Test
    void theSweepFindsTheShippedEntrances() {
        Map<String, Template> shipped = shipped();
        assertTrue(shipped.size() >= 3, "expected the shipped entrance templates, found "
                + shipped.keySet());
        assertTrue(!starts(shipped).isEmpty(), "no surface piece found -- markers are not being read");
    }

    /** The numbers, for the record and for whoever cuts the next entrance. */
    @Test
    void report() {
        int budget = ceilingBudget();
        Map<String, Template> shipped = shipped();
        System.out.println("=== entrance drops, ceiling budget " + budget + " ===");
        for (Map.Entry<String, Template> start : starts(shipped).entrySet()) {
            int surfaceY = start.getValue().outgoing().y();
            for (Walk walk : JigsawChains.walk(start.getKey(), shipped)) {
                String drop = walk.failed() || walk.end().doorYs().isEmpty() ? "unreached"
                        : String.valueOf(surfaceY - (walk.endOriginY() + walk.end().doorYs().get(0)));
                System.out.println("  " + walk.trail() + "  drop=" + drop
                        + (walk.failed() ? "  FAILED: " + walk.failure() : ""));
            }
        }
    }
}
