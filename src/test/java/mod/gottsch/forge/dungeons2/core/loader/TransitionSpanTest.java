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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Backlog #52: a transition has to <em>reach</em>, and until now nothing checked that it did.
 *
 * <h2>The span is the traverse; the volume is not</h2>
 * <p>What must equal the floor-to-floor pitch is the distance between a transition's two
 * {@code dungeons2:door} markers &mdash; those are the points the maze attaches the two floors'
 * corridors to. The piece's bounding box is a larger and unrelated number: {@code stairs_1} is a
 * 21-block-tall building whose doors are 12 apart, {@code ladder1} is 18 tall with the same 12-block
 * traverse. {@code DungeonStructure} used to compare the assembly's <em>volume</em> against a
 * hand-copied 12, which passed {@code stairs_1} on the strength of the 21 without ever looking at
 * where its doors were.</p>
 *
 * <h2>Why this test rather than only the runtime check</h2>
 * <p>The runtime check fires during worldgen, once, into a log, on whatever seed happened to
 * generate. This one fails the build the moment the planner's pitch and the shipped templates
 * disagree &mdash; which is exactly what happens the moment {@code floorHeight} is raised for
 * backlog #29. Without it the symptom in game is a stairwell that ends in stone: the upper door
 * marker is handed to the maze as if it sat on the upper walking plane, and nothing downstream
 * re-checks it. No error, no log, no test.</p>
 *
 * <p>So: <strong>if you are here because this test went red after changing the floor height, it is
 * working.</strong> The templates named in the failure need re-cutting; see backlog #52 for which
 * of them can be stretched and which are monolithic.</p>
 */
class TransitionSpanTest {

    private static final String TRANSITION_ROOT = "/data/dungeons2/structures/transitions";

    private static final String DOOR = "dungeons2:door";
    private static final String CONNECTOR = "dungeons2:connector";

    /**
     * Blocks a chained segment gains per join, on top of its own up-jigsaw's local Y.
     *
     * <p>Vanilla's {@code JigsawPlacement} seats a child so its target marker is <em>adjacent</em>
     * to the source marker rather than coincident with it, which for a vertical connection is one
     * block. This is the one number here that is not read out of a file, and it is
     * <strong>self-checking on the shipped state</strong>: {@code stairs_2}'s segments rise 5 by
     * marker, and 5 does not divide the shipped pitch of 12 while 5+1 does. If the constant were
     * wrong, {@link #aChainedTransitionCanLandExactlyOnThePitch} would be red today.</p>
     */
    private static final int JIGSAW_JOIN_OFFSET = 1;

    /**
     * What worldgen actually plans at, asked rather than copied &mdash; the shipped
     * {@code generation_config}'s pitch, which {@code RoomHeightBandTest} pins against the file on
     * disk, and which the planner's own default delegates to.
     */
    private static int pitch() {
        return DungeonGenerationConfig.DEFAULT.pitch();
    }

    /**
     * A monolithic transition &mdash; two door planes in one template &mdash; must span the pitch by
     * itself. It has no way to stretch: {@code stairs_1}'s upper marker cannot move without
     * re-cutting the stairs beneath it.
     */
    @Test
    void everyMonolithicTransitionSpansExactlyThePitch() {
        int pitch = pitch();
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Template> entry : templates().entrySet()) {
            Template template = entry.getValue();
            if (!template.isMonolithic()) {
                continue;
            }
            checked++;
            if (template.doorSpan() != pitch) {
                offenders.add(entry.getKey() + ": doors at Y=" + template.doorYs()
                        + " span " + template.doorSpan() + ", pitch is " + pitch);
            }
        }
        assertTrue(checked >= 2, "expected the shipped monolithic transitions, found " + checked);
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " shipped transition(s) do not span the floor pitch of " + pitch
                    + ". The two floors would not connect, silently -- re-cut the template(s) or "
                    + "change the pitch back:\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * A chained transition stretches by repeating its middle segment, so it does not need to span
     * the pitch in one piece &mdash; but it does need to land <em>exactly</em> on the upper plane,
     * and it can only land on multiples of its segment rise. A pitch its segments cannot sum to is
     * as broken as a template that is too short, and considerably less obvious.
     */
    @Test
    void aChainedTransitionCanLandExactlyOnThePitch() {
        int pitch = pitch();
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Template> entry : templates().entrySet()) {
            Template template = entry.getValue();
            if (template.isMonolithic() || template.continuationY() == null) {
                continue;
            }
            checked++;
            int rise = template.continuationY() + JIGSAW_JOIN_OFFSET;
            if (pitch % rise != 0) {
                offenders.add(entry.getKey() + ": rises " + rise + " per segment ("
                        + template.continuationY() + " by marker + " + JIGSAW_JOIN_OFFSET
                        + " for the join), which does not divide the pitch of " + pitch
                        + " -- the chain would stop " + (pitch % rise) + " block(s) off the plane");
            }
        }
        assertTrue(checked >= 1, "expected at least one chained transition, found " + checked);
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " chained transition(s) cannot land on the floor pitch of "
                    + pitch + ":\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * There is no third category. A transition template either carries both door planes itself or
     * hands off to a continuation pool; one that does neither is a stairwell that goes nowhere, and
     * it would assemble and place perfectly happily.
     */
    @Test
    void everyTransitionEitherSpansOrContinues() {
        List<String> orphans = new ArrayList<>();
        for (Map.Entry<String, Template> entry : templates().entrySet()) {
            Template template = entry.getValue();
            if (!template.isMonolithic() && template.continuationY() == null
                    && template.doorYs().isEmpty()) {
                orphans.add(entry.getKey() + ": no second door plane and no continuation jigsaw");
            }
        }
        if (!orphans.isEmpty()) {
            fail(orphans.size() + " transition template(s) neither span nor continue:\n  "
                    + String.join("\n  ", orphans));
        }
    }

    /** Everything above passes vacuously if the templates are not being found or not read. */
    @Test
    void theSweepFindsTheShippedTransitions() {
        Map<String, Template> found = templates();
        assertTrue(found.size() >= 5, "expected the shipped transition templates, found "
                + found.keySet());
        assertTrue(found.values().stream().anyMatch(Template::isMonolithic),
                "no template carried two door planes -- the markers are not being read");
        assertTrue(found.values().stream().anyMatch(t -> t.continuationY() != null),
                "no template carried a continuation jigsaw -- the markers are not being read");
    }

    /** The numbers, for the record and for whoever re-cuts these. */
    @Test
    void report() {
        int pitch = pitch();
        System.out.println("=== #52 transition spans, planner pitch " + pitch + " ===");
        templates().forEach((name, t) -> System.out.println("  " + name
                + "  doorY=" + t.doorYs()
                + (t.isMonolithic() ? "  span=" + t.doorSpan() : "")
                + (t.continuationY() != null ? "  continues at Y=" + t.continuationY() : "")));
    }

    // ---------- reading the binary templates ----------

    /**
     * One template's vertical marker geometry. {@code doorYs} holds the distinct Y planes carrying a
     * {@code dungeons2:door} <em>or</em> {@code dungeons2:connector} marker: the chain's ends use
     * connectors (its doors are prebuilt), the monolithic ones use doors, and both are a floor
     * plane as far as reaching is concerned.
     */
    private record Template(List<Integer> doorYs, Integer continuationY) {
        boolean isMonolithic() {
            return doorYs.size() >= 2;
        }

        int doorSpan() {
            return doorYs.get(doorYs.size() - 1) - doorYs.get(0);
        }
    }

    private static Map<String, Template> templates() {
        Map<String, Template> out = new LinkedHashMap<>();
        for (Path file : transitionFiles()) {
            CompoundTag root = read(file);
            List<Integer> planes = new ArrayList<>();
            Integer continuation = null;
            ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompound(i);
                if (!block.contains("nbt", Tag.TAG_COMPOUND)) {
                    continue;
                }
                String name = block.getCompound("nbt").getString("name");
                int y = block.getList("pos", Tag.TAG_INT).getInt(1);
                if (DOOR.equals(name) || CONNECTOR.equals(name)) {
                    if (!planes.contains(y)) {
                        planes.add(y);
                    }
                } else if (!block.getCompound("nbt").getString("pool").isEmpty()
                        && !"minecraft:empty".equals(block.getCompound("nbt").getString("pool"))) {
                    // A jigsaw naming a real continuation pool is what makes this a chain link.
                    continuation = continuation == null ? y : Math.max(continuation, y);
                }
            }
            planes.sort(Integer::compareTo);
            out.put(file.getFileName().toString(), new Template(planes, continuation));
        }
        return out;
    }

    private static CompoundTag read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read template " + file, unreadable);
        }
    }

    private static List<Path> transitionFiles() {
        URL url = TransitionSpanTest.class.getResource(TRANSITION_ROOT);
        if (url == null) {
            return fail("no shipped transitions at " + TRANSITION_ROOT);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + TRANSITION_ROOT + ": " + unreadable);
        }
    }
}
