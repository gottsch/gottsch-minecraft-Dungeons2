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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The mod load order this mod and its dependencies declare has <strong>no cycle</strong>.
 *
 * <h2>The crash this exists for</h2>
 * <p>2026-08-13, on adding {@code gmm}. Forge's {@code ModSorter} died before the game window
 * opened with:</p>
 * <pre>
 * Mod Sorting failed.
 * Detected Cycles: [[...ModFileInfo@6bd16207, ...ModFileInfo@298d9a05, ...ModFileInfo@58399d82]]
 * Exception in thread "main" java.lang.NullPointerException:
 *     Cannot invoke "java.util.List.stream()" because "sortedList" is null
 * </pre>
 * <p><strong>The message names nothing.</strong> Three anonymous object identities, no mod ids, no
 * file names, and then an NPE from the sorter handing a null list onward. Working out that it meant
 * {@code dungeons2 -> gottschcore -> gmm -> dungeons2} took reading three separate mods' TOMLs by
 * hand.</p>
 *
 * <h2>The rule</h2>
 * <p>{@code ordering} says where the <em>declaring</em> mod sits relative to the dependency, so
 * {@code AFTER} means "I load after it". Dungeons2 had {@code BEFORE} for gottschcore, which was
 * harmless alone; gmm's own TOML declares gottschcore {@code AFTER}, so adding gmm as {@code AFTER}
 * closed a loop. <strong>Either convention works as long as it is applied consistently &mdash;
 * mixing them is what cycles.</strong> Dungeon Denizens uses AFTER throughout; Village Dungeons uses
 * BEFORE throughout; this mod now uses AFTER throughout.
 *
 * <h2>How it can check other mods</h2>
 * <p>Every mod jar carries its own {@code META-INF/mods.toml}, and the dependency jars are on the
 * test classpath &mdash; so {@code ClassLoader.getResources} enumerates all of them, ours included,
 * and the real graph can be built. That is the whole point: a cycle is never visible in one file.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
class ModLoadOrderTest {

    // The trailing "#mandatory" / "#optional" comments Forge's generated template carries are part
    // of every one of these files, ours included -- an anchored pattern without them matches nothing
    // and every check here passes vacuously.
    private static final Pattern MODS_SECTION = Pattern.compile("^\\s*\\[\\[mods]]\\s*(#.*)?$");
    private static final Pattern DEPENDENCY_SECTION =
            Pattern.compile("^\\s*\\[\\[dependencies\\.([A-Za-z0-9_${}]+)]]\\s*(#.*)?$");
    private static final Pattern KEY_VALUE =
            Pattern.compile("^\\s*(modId|ordering)\\s*=\\s*\"([^\"]*)\"");

    /** "a must load before b". */
    private record Edge(String before, String after, String source) {
        @Override
        public String toString() {
            return before + " -> " + after + "  (declared by " + source + ")";
        }
    }

    @Test
    void theDeclaredLoadOrderHasNoCycle() {
        List<Edge> edges = edges();
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Edge edge : edges) {
            graph.computeIfAbsent(edge.before(), key -> new ArrayList<>()).add(edge.after());
        }

        List<String> cycle = findCycle(graph);
        if (cycle != null) {
            fail("the declared mod load order contains a cycle, which is a hard crash in Forge's"
                    + " ModSorter before the window opens:\n  " + String.join(" -> ", cycle)
                    + "\n\nall declared edges (\"X must load before Y\"):\n  "
                    + edges.stream().map(Edge::toString).reduce((a, b) -> a + "\n  " + b).orElse(""));
        }
    }

    /**
     * This mod's own orderings agree with each other.
     *
     * <p>Cheap and blunt, and it catches the mistake one file earlier than the cycle check does: a
     * mod that says BEFORE about one library and AFTER about another is one dependency-jar edge away
     * from a loop, whether or not it happens to have closed one today.</p>
     */
    @Test
    void thisModIsConsistentAboutItsOwnOrdering() {
        Set<String> orderings = new LinkedHashSet<>();
        for (Map.Entry<String, String> dependency : ourDependencies().entrySet()) {
            if (!"NONE".equals(dependency.getValue())) {
                orderings.add(dependency.getValue());
            }
        }
        assertTrue(orderings.size() <= 1,
                "dungeons2's mods.toml mixes " + orderings + " across its dependencies "
                        + ourDependencies() + ". Pick one and use it throughout -- mixing is what"
                        + " produced the 2026-08-13 ModSorter cycle. See this test's notes.");
    }

    /** Both checks above pass trivially if the dependency jars are not being read. */
    @Test
    void theDependencyTomlsAreBeingRead() {
        Set<String> declaring = new LinkedHashSet<>();
        for (Edge edge : edges()) {
            declaring.add(edge.source());
        }
        assertTrue(declaring.contains("dungeons2"), "expected our own mods.toml, found " + declaring);
        assertTrue(declaring.size() > 1,
                "expected at least one dependency's mods.toml on the test classpath, found only "
                        + declaring + " -- a cycle is never visible in one file, so this test would"
                        + " be checking nothing");
        assertTrue(!ourDependencies().isEmpty(), "expected dungeons2 to declare dependencies");
    }

    // ---------- building the graph ----------

    private static List<Edge> edges() {
        List<Edge> edges = new ArrayList<>();
        for (String toml : allModsTomls()) {
            String declaring = declaringMod(toml);
            if (declaring == null) {
                continue;
            }
            parseDependencies(toml).forEach((dependency, ordering) -> {
                // ordering describes where the DECLARING mod sits relative to the dependency.
                if ("BEFORE".equals(ordering)) {
                    edges.add(new Edge(declaring, dependency, declaring));
                } else if ("AFTER".equals(ordering)) {
                    edges.add(new Edge(dependency, declaring, declaring));
                }
            });
        }
        return edges;
    }

    private static Map<String, String> ourDependencies() {
        for (String toml : allModsTomls()) {
            if ("dungeons2".equals(declaringMod(toml))) {
                Map<String, String> dependencies = parseDependencies(toml);
                // forge/minecraft are always NONE and are not interesting here.
                dependencies.remove("forge");
                dependencies.remove("minecraft");
                return dependencies;
            }
        }
        return fail("no mods.toml declaring dungeons2 on the classpath");
    }

    /** The first {@code modId} under {@code [[mods]]}. */
    private static String declaringMod(String toml) {
        boolean inMods = false;
        for (String line : toml.split("\\R")) {
            if (MODS_SECTION.matcher(line).matches()) {
                inMods = true;
                continue;
            }
            if (line.trim().startsWith("[[")) {
                inMods = false;
            }
            Matcher keyValue = KEY_VALUE.matcher(line);
            if (inMods && keyValue.find() && "modId".equals(keyValue.group(1))) {
                return keyValue.group(2);
            }
        }
        return null;
    }

    /** dependency modId -> ordering, for every {@code [[dependencies.*]]} block. */
    private static Map<String, String> parseDependencies(String toml) {
        Map<String, String> dependencies = new LinkedHashMap<>();
        String pendingId = null;
        boolean inDependency = false;
        for (String line : toml.split("\\R")) {
            if (DEPENDENCY_SECTION.matcher(line).matches()) {
                inDependency = true;
                pendingId = null;
                continue;
            }
            if (line.trim().startsWith("[[")) {
                inDependency = false;
                pendingId = null;
                continue;
            }
            if (!inDependency) {
                continue;
            }
            Matcher keyValue = KEY_VALUE.matcher(line);
            if (!keyValue.find()) {
                continue;
            }
            if ("modId".equals(keyValue.group(1))) {
                pendingId = keyValue.group(2);
                dependencies.putIfAbsent(pendingId, "NONE");
            } else if (pendingId != null) {
                dependencies.put(pendingId, keyValue.group(2));
            }
        }
        return dependencies;
    }

    private static List<String> allModsTomls() {
        List<String> tomls = new ArrayList<>();
        try {
            Enumeration<URL> resources =
                    ModLoadOrderTest.class.getClassLoader().getResources("META-INF/mods.toml");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream()) {
                    tomls.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not enumerate mods.toml on the classpath", unreadable);
        }
        return tomls;
    }

    /** Plain DFS; returns the cycle as a readable path, or null. */
    private static List<String> findCycle(Map<String, List<String>> graph) {
        Set<String> done = new LinkedHashSet<>();
        for (String node : new LinkedHashSet<>(graph.keySet())) {
            List<String> path = new ArrayList<>();
            List<String> cycle = visit(node, graph, new LinkedHashSet<>(), done, path);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private static List<String> visit(String node, Map<String, List<String>> graph,
                                      Set<String> onPath, Set<String> done, List<String> path) {
        if (done.contains(node)) {
            return null;
        }
        if (!onPath.add(node)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(node), path.size()));
            cycle.add(node);
            return cycle;
        }
        path.add(node);
        for (String next : graph.getOrDefault(node, List.of())) {
            List<String> cycle = visit(next, graph, onPath, done, path);
            if (cycle != null) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        onPath.remove(node);
        done.add(node);
        return null;
    }
}
