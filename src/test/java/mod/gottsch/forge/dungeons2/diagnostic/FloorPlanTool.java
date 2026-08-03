/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Plans a dungeon headlessly and writes a self-contained, interactive 2D floor-plan viewer to a
 * single HTML file. No world, no game, no server &mdash; just the planner and the piece renderers.
 *
 * <p>Run it through the Gradle task, which puts the test runtime classpath (and therefore Minecraft)
 * in front of it:</p>
 *
 * <pre>
 *   ./gradlew floorplan
 *   ./gradlew floorplan -Pseed=12345 -Psize=MEDIUM -Pfloors=3 -Popen=true
 * </pre>
 *
 * <h2>Options</h2>
 * <table>
 *   <tr><td>{@code --seed}</td><td>dungeon seed (default 0)</td></tr>
 *   <tr><td>{@code --size}</td><td>SMALL / MEDIUM / LARGE (default MEDIUM)</td></tr>
 *   <tr><td>{@code --motif}</td><td>motif value, read from the shipped datapack JSON (default classic)</td></tr>
 *   <tr><td>{@code --floors}</td><td>floor count override; omit to let the size tier roll it</td></tr>
 *   <tr><td>{@code --corridorWidth}</td><td>dilation width 1-3 (default 3, matching the shipped generation config)</td></tr>
 *   <tr><td>{@code --corridorHeight}</td><td>corridor wall height in blocks; default is the motif's own {@code corridor.height}</td></tr>
 *   <tr><td>{@code --x} / {@code --z}</td><td>world XZ the planner is anchored at (default 0,0)</td></tr>
 *   <tr><td>{@code --surfaceY}</td><td>surface Y the stack hangs from (default 72)</td></tr>
 *   <tr><td>{@code --order}</td><td>{@code EMIT} (production: rooms, corridors, doors) or {@code CORRIDORS_FIRST}</td></tr>
 *   <tr><td>{@code --out}</td><td>output file (default {@code build/floorplan/floorplan-&lt;seed&gt;.html})</td></tr>
 *   <tr><td>{@code --open}</td><td>{@code true} to open the file in the default browser when done</td></tr>
 *   <tr><td>{@code --describe}</td><td>{@code true} to also dump the planner's own layout description</td></tr>
 * </table>
 *
 * <h2>Reproducing a dungeon you found in game</h2>
 * <p>The anchor is part of every piece's seed (see {@code DungeonPiece#deterministicRandom}), so
 * matching a specific in-game dungeon means passing its anchor as {@code --x} / {@code --z} as well
 * as its seed. With the defaults the layout is still a real, representative dungeon &mdash; just not
 * that one.</p>
 */
public final class FloorPlanTool {

    private static final String TEMPLATE = "/diagnostic/floorplan.html";
    private static final String DATA_TOKEN = "\"__FLOORPLAN_DATA__\"";

    private FloorPlanTool() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);

        long seed = Long.parseLong(opts.getOrDefault("seed", "0"));
        DungeonSize size = DungeonSize.valueOf(opts.getOrDefault("size", "MEDIUM").toUpperCase());
        String motif = opts.getOrDefault("motif", "classic");
        int worldX = Integer.parseInt(opts.getOrDefault("x", "0"));
        int worldZ = Integer.parseInt(opts.getOrDefault("z", "0"));
        int surfaceY = Integer.parseInt(opts.getOrDefault("surfaceY", "72"));
        int corridorWidth = Integer.parseInt(opts.getOrDefault("corridorWidth", "3"));
        int minRoomGap = Integer.parseInt(opts.getOrDefault("minRoomGap", "0"));

        // The generators resolve block states through the registry, so Minecraft has to be
        // bootstrapped exactly as the generator tests do it.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        // Loaded before planning, not just for rendering: corridor height is resolved from the
        // motif and injected into the planner, the same way DungeonStructure does it, so the plan
        // this tool draws is the plan production would build.
        MotifConfig motifConfig = MotifConfigs.load(motif);
        int corridorHeight = Integer.parseInt(opts.getOrDefault("corridorHeight",
                String.valueOf(motifConfig.corridor().height())));

        DungeonStackPlanner planner = new DungeonStackPlanner(
                seed, new Coords(worldX, 0, worldZ), surfaceY, motif, new TemplateCatalog())
                .withSize(size)
                .withCorridorWidth(corridorWidth)
                .withCorridorHeight(corridorHeight)
                .withMinRoomGap(minRoomGap);
        if (opts.containsKey("floors")) {
            planner.withFloorCount(Integer.parseInt(opts.get("floors")));
        }

        Optional<DungeonLayout> planned = planner.plan();
        if (planned.isEmpty()) {
            System.err.println("Planning failed for seed " + seed + " at (" + worldX + "," + worldZ + ").");
            System.exit(1);
            return;
        }
        DungeonLayout layout = planned.get();

        FloorPlanExporter exporter = new FloorPlanExporter(layout, motifConfig)
                .withOrder(FloorPlanExporter.PieceOrder.valueOf(
                        opts.getOrDefault("order", "EMIT").toUpperCase()));
        String json = exporter.toJson();
        String html = template().replace(DATA_TOKEN, json);

        Path out = opts.containsKey("out")
                ? Paths.get(opts.get("out"))
                : Paths.get("build", "floorplan", "floorplan-" + seed + ".html");
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, html, StandardCharsets.UTF_8);

        if (Boolean.parseBoolean(opts.getOrDefault("describe", "false"))) {
            System.out.println(layout.describe());
        }
        System.out.println();
        System.out.println(exporter.audit());
        System.out.printf("Floor plan written to %s (%.1f KB)%n",
                out.toAbsolutePath(), Files.size(out) / 1024.0);

        if (Boolean.parseBoolean(opts.getOrDefault("open", "false"))) {
            open(out);
        }
    }

    private static String template() throws IOException {
        try (InputStream in = FloorPlanTool.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) {
                throw new IOException("viewer template " + TEMPLATE + " is not on the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void open(Path file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(file.toUri());
            }
        } catch (Exception e) {
            System.out.println("(could not open a browser: " + e.getMessage() + ")");
        }
    }

    /** Accepts {@code --key=value} and {@code --key value}; a bare {@code --key} means "true". */
    private static Map<String, String> parse(String[] args) {
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq >= 0) {
                opts.put(body.substring(0, eq), body.substring(eq + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                opts.put(body, args[++i]);
            } else {
                opts.put(body, "true");
            }
        }
        return opts;
    }
}
