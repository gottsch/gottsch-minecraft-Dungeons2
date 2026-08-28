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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reads shipped jigsaw templates and <strong>walks the chains they describe</strong>. Shared by
 * {@link TransitionSpanTest} and {@link EntranceSpanTest}, which check the same mechanism at
 * opposite ends of the dungeon.
 *
 * <h2>Why a chain has to be walked and not modelled</h2>
 * <p>The first version of the transition gate assumed a chain <em>repeats one middle segment</em>
 * and asked whether that segment's rise divided the required span. No shipped chain repeats: every
 * link names exactly one successor, and the terminating piece contributes its own door offset
 * rather than another full segment. That model agreed with reality only at the old pitch, by
 * arithmetic coincidence, and called a correct chain broken as soon as the pitch moved. Following
 * the links needs no information the files do not already carry.</p>
 *
 * <h2>Direction is read, not assumed</h2>
 * <p>A transition chain climbs and an entrance chain descends, so the join cannot be a constant
 * {@code +1}. A jigsaw's {@code orientation} is {@code front_top}; the front is what the block
 * faces, and vanilla seats the child <em>adjacent</em> to the source in that direction. So
 * {@code up_*} steps +1, {@code down_*} steps −1, and a horizontal front steps 0 &mdash; which is
 * how one walker serves both ends.</p>
 *
 * <h2>Every branch is walked, not the first</h2>
 * <p>Two entrance ladders both answer to {@code dungeons2:entrance/ladder_top}, and the pool picks
 * between them at random. Checking only the first found would leave the other unguarded, so
 * {@link #walk} returns one result per complete path and the caller must be satisfied by all of
 * them.</p>
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
final class JigsawChains {

    private JigsawChains() {}

    /** A door plane in a monolithic piece, or the prebuilt door of a chain's end. */
    static final String DOOR = "dungeons2:door";
    static final String CONNECTOR = "dungeons2:connector";

    /** One jigsaw block: where it sits, which way it faces, and what it says. */
    record Marker(int y, String orientation, String name, String target, String pool) {

        /** Whether this jigsaw attaches the NEXT piece, i.e. names a real continuation pool. */
        boolean isOutgoing() {
            return !pool.isEmpty() && !"minecraft:empty".equals(pool);
        }

        /** Whether this marker is a floor plane, by either of the two names used for one. */
        boolean isDoorPlane() {
            return DOOR.equals(name) || CONNECTOR.equals(name);
        }

        /**
         * The Y step to the cell the child's marker occupies. Vanilla seats a child adjacent to the
         * source in the direction the source FACES, which is the first half of {@code front_top}.
         */
        int stepY() {
            String front = orientation.toLowerCase(Locale.ROOT).split("_")[0];
            return switch (front) {
                case "up" -> 1;
                case "down" -> -1;
                default -> 0;
            };
        }
    }

    /** One template's jigsaw geometry. */
    record Template(List<Integer> doorYs, List<Marker> markers) {

        boolean isMonolithic() {
            return doorYs.size() >= 2;
        }

        int doorSpan() {
            return doorYs.get(doorYs.size() - 1) - doorYs.get(0);
        }

        Marker outgoing() {
            return markers.stream().filter(Marker::isOutgoing).findFirst().orElse(null);
        }

        /** The Y of this template's highest continuation jigsaw, or null if it has none. */
        Integer continuationY() {
            return markers.stream().filter(Marker::isOutgoing)
                    .map(Marker::y).max(Integer::compareTo).orElse(null);
        }

        /** This template's own jigsaw carrying {@code name} -- how a predecessor attaches it. */
        Marker incoming(String name) {
            return markers.stream()
                    .filter(marker -> !marker.isOutgoing() && name.equals(marker.name()))
                    .findFirst().orElse(null);
        }
    }

    /** One complete path through a chain: where it ended and how it got there. */
    record Walk(String trail, String failure, String endName, Template end, int endOriginY) {
        boolean failed() {
            return failure != null;
        }
    }

    /**
     * Every complete path from {@code startName}, seating each piece as vanilla would.
     *
     * <p>A path that cannot continue &mdash; a target nothing carries, or a loop &mdash; comes back
     * as a failed {@link Walk} rather than an exception, so the caller reports all of them at once
     * instead of one per run.</p>
     */
    static List<Walk> walk(String startName, Map<String, Template> shipped) {
        List<Walk> results = new ArrayList<>();
        follow(startName, shipped.get(startName), 0, startName, new LinkedHashSet<>(), shipped, results);
        return results;
    }

    private static void follow(String name, Template current, int originY, String trail,
                               Set<String> seen, Map<String, Template> shipped, List<Walk> out) {
        if (!seen.add(name)) {
            out.add(new Walk(trail, "the chain loops back to " + name + " and would never terminate",
                    name, current, originY));
            return;
        }
        Marker outgoing = current.outgoing();
        if (outgoing == null) {
            out.add(new Walk(trail, null, name, current, originY));
            return;
        }
        int attachY = originY + outgoing.y() + outgoing.stepY();

        List<String> candidates = new ArrayList<>();
        shipped.forEach((candidateName, template) -> {
            if (template.incoming(outgoing.target()) != null) {
                candidates.add(candidateName);
            }
        });
        if (candidates.isEmpty()) {
            out.add(new Walk(trail, "targets '" + outgoing.target() + "', which no shipped template"
                    + " carries as a jigsaw name -- the chain stops there", name, current, originY));
            return;
        }
        for (String candidateName : candidates) {
            Template candidate = shipped.get(candidateName);
            int childOrigin = attachY - candidate.incoming(outgoing.target()).y();
            follow(candidateName, candidate, childOrigin, trail + " -> " + candidateName,
                    new LinkedHashSet<>(seen), shipped, out);
        }
    }

    // ---------- reading what ships ----------

    /** Every {@code .nbt} under {@code root}, keyed by file name. */
    static Map<String, Template> templates(String root) {
        Map<String, Template> out = new LinkedHashMap<>();
        for (Path file : walkResource(root, ".nbt")) {
            CompoundTag tag = read(file);
            List<Integer> planes = new ArrayList<>();
            List<Marker> markers = new ArrayList<>();
            ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompound(i);
                if (!block.contains("nbt", Tag.TAG_COMPOUND)) {
                    continue;
                }
                CompoundTag jigsaw = block.getCompound("nbt");
                int y = block.getList("pos", Tag.TAG_INT).getInt(1);
                String orientation = palette.getCompound(block.getInt("state"))
                        .getCompound("Properties").getString("orientation");
                Marker marker = new Marker(y, orientation, jigsaw.getString("name"),
                        jigsaw.getString("target"), jigsaw.getString("pool"));
                markers.add(marker);
                if (marker.isDoorPlane() && !planes.contains(y)) {
                    planes.add(y);
                }
            }
            planes.sort(Integer::compareTo);
            out.put(file.getFileName().toString(), new Template(planes, markers));
        }
        return out;
    }

    /**
     * The subset of {@code templates} that a pool under {@code poolRoot} actually names.
     *
     * <p><strong>The gates judge what ships IN PLAY, not what sits on disk.</strong> A template no
     * pool names is retired content, not a release blocker: taking a piece out of its pool is how
     * you retire one that no longer fits, and deleting the {@code .nbt} throws away work that is
     * usually going to be re-cut.</p>
     */
    static Map<String, Template> reachable(Map<String, Template> templates, String poolRoot) {
        Set<String> named = new LinkedHashSet<>();
        for (Path pool : walkResource(poolRoot, ".json")) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(pool)).getAsJsonObject();
                if (!json.has("elements")) {
                    continue;
                }
                for (JsonElement element : json.getAsJsonArray("elements")) {
                    JsonObject inner = element.getAsJsonObject().getAsJsonObject("element");
                    if (inner.has("location")) {
                        String location = inner.get("location").getAsString();
                        named.add(location.substring(location.lastIndexOf('/') + 1) + ".nbt");
                    }
                }
            } catch (IOException unreadable) {
                fail("could not read template pool " + pool + ": " + unreadable);
            }
        }
        Map<String, Template> out = new LinkedHashMap<>();
        templates.forEach((name, template) -> {
            if (named.contains(name)) {
                out.put(name, template);
            }
        });
        return out;
    }

    /**
     * The templates named by every pool file called {@code fileName}, across all motifs.
     *
     * <p>Used to find a chain's START, which cannot be derived from the markers: a jigsaw names both
     * sides of every join, so the surface entrance is "targeted" by the ladder that hangs beneath
     * it just as much as the ladder is targeted by it. The pool is the only thing that says which
     * end the game begins at &mdash; {@code DungeonStructure} starts an entrance from
     * {@code entrance/<motif>/surface_entrance} &mdash; so the test asks the same question the code
     * does instead of inferring an answer.</p>
     */
    static Set<String> namedBy(String poolRoot, String fileName) {
        Set<String> named = new LinkedHashSet<>();
        for (Path pool : walkResource(poolRoot, ".json")) {
            if (!pool.getFileName().toString().equals(fileName)) {
                continue;
            }
            try {
                JsonObject json = JsonParser.parseString(Files.readString(pool)).getAsJsonObject();
                for (JsonElement element : json.getAsJsonArray("elements")) {
                    JsonObject inner = element.getAsJsonObject().getAsJsonObject("element");
                    if (inner.has("location")) {
                        String location = inner.get("location").getAsString();
                        named.add(location.substring(location.lastIndexOf('/') + 1) + ".nbt");
                    }
                }
            } catch (IOException unreadable) {
                fail("could not read template pool " + pool + ": " + unreadable);
            }
        }
        return named;
    }

    private static CompoundTag read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read template " + file, unreadable);
        }
    }

    private static List<Path> walkResource(String root, String suffix) {
        URL url = JigsawChains.class.getResource(root);
        if (url == null) {
            return fail("nothing shipped at " + root);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted().toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + root + ": " + unreadable);
        }
    }
}
