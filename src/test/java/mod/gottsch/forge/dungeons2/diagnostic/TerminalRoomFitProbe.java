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
package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * How big an authored boss room can be before the bottom floor stops fitting it &mdash; backlog
 * #46's step 1, which the entry says must be measured before anything is built.
 *
 * <h2>It is a geometric fit failure, and that was checked rather than assumed</h2>
 * <p>The obvious reading of a number like "15x15 places on 50% of SMALL seeds" is that
 * {@code placeAvoidingReserved} ran out of its 20 random attempts. It did not &mdash; that method
 * falls back to an <em>exhaustive</em> even-aligned scan, so it finds a slot whenever one exists.
 * The second obvious reading is that this probe is really measuring knock-on: a bigger terminal room
 * consumes different randomness, so a failed plan might be failing somewhere else entirely. That was
 * measured separately &mdash; running the same exhaustive scan against the bottom floor's footprint
 * and its START room, with no planning at all, produces <em>identical</em> percentages. The number
 * and the label agree.</p>
 *
 * <p>What actually binds is SMALL's bottom floor (25x25 to 35x35) against the START room it must
 * avoid, which on the bottom floor is the upstairs transition's own footprint. A 15x15 is more than
 * half the width of a 25x25 floor, so it cannot dodge anything near the middle.</p>
 *
 * <h2>Why this is the question that decides the design</h2>
 * <p>The terminal slot is reserved at a synthetic <strong>7x7</strong> today, and a boss room wants
 * to be bigger. But the bottom floor's grid is sized by the dungeon's size tier, not by what the
 * terminal room wants, so a large authored template may simply not fit &mdash; and the failure is
 * not "no boss room". {@code plan()} returns <em>empty</em> when the terminal slot cannot be placed,
 * because every floor is required to have an end. <strong>A boss template one size too big deletes
 * the whole dungeon.</strong></p>
 *
 * <p>The entry offers two mitigations &mdash; author several sizes and retry smallest-first, or
 * floor the bottom floor's grid at the largest authored footprint. The second re-rolls existing
 * seeds, so it needs to be worth it. That is what the numbers below decide.</p>
 *
 * <h2>It reports, it does not gate</h2>
 * <p>The only assertions are that the sample is real and that the shipped 7x7 never fails &mdash;
 * that last one is a genuine invariant, since it is what the mod builds today. The rest is
 * calibration.</p>
 */
class TerminalRoomFitProbe {

    private static final int DUNGEONS = 300;

    /** Odd, because every room the maze places is; 7 is today's, 21 is past the largest it builds. */
    private static final int[] SIZES = {7, 9, 11, 13, 15, 17, 19, 21};

    @Test
    void measure() {
        // [tier][size] -> plans that succeeded
        Map<DungeonSize, Map<Integer, Integer>> ok = new LinkedHashMap<>();
        Map<DungeonSize, Integer> attempts = new LinkedHashMap<>();

        for (DungeonSize size : DungeonSize.values()) {
            Map<Integer, Integer> bySize = new LinkedHashMap<>();
            ok.put(size, bySize);
            int tried = 0;
            for (int i = 0; i < DUNGEONS; i++) {
                // Spread, not sequential -- see reference_first_draw_seed_correlation.
                long seed = 0xD2_4600_0001L + i * 7919L;
                tried++;
                for (int side : SIZES) {
                    if (plan(seed, size, side).isPresent()) {
                        bySize.merge(side, 1, Integer::sum);
                    }
                }
            }
            attempts.put(size, tried);
        }

        System.out.println("=== #46 terminal-room fit: % of seeds that still PLAN AT ALL ===");
        System.out.println("(a failure here is not a missing boss room -- it is no dungeon)");
        StringBuilder header = new StringBuilder(String.format("%-8s", "tier"));
        for (int side : SIZES) {
            header.append(String.format("%8s", side + "x" + side));
        }
        System.out.println(header);
        for (DungeonSize size : DungeonSize.values()) {
            StringBuilder row = new StringBuilder(String.format("%-8s", size));
            for (int side : SIZES) {
                double pct = 100.0 * ok.get(size).getOrDefault(side, 0) / attempts.get(size);
                row.append(String.format("%7.1f%%", pct));
            }
            System.out.println(row);
        }

        // The invariant: 7x7 is what the mod builds today, so it must never be the thing that
        // fails. If this ever goes red, the terminal slot has stopped being placeable and every
        // dungeon on those seeds has quietly vanished.
        for (DungeonSize size : DungeonSize.values()) {
            assertEquals(attempts.get(size), ok.get(size).getOrDefault(7, 0),
                    "the shipped 7x7 terminal room failed to place on some " + size + " seeds");
        }
        assertTrue(attempts.values().stream().allMatch(n -> n >= DUNGEONS),
                "expected a meaningful sample per tier");
    }

    private static Optional<DungeonLayout> plan(long seed, DungeonSize size, int side) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, "classic",
                new TemplateCatalog())
                .withSize(size)
                .withTerminalRoomSize(side, side)
                .plan();
    }

    // ---------- the shipped template, measured through the real placement path ----------

    /**
     * The number the table above cannot give: how often the <strong>shipped</strong> boss room is
     * actually adopted &mdash; #46's hand-off step, run when {@code end_rooms} stopped being empty.
     *
     * <h2>The table above asks a harsher question than the mod now asks</h2>
     * <p>{@code withTerminalRoomSize} <em>forces</em> the slot to a size, so a size that does not
     * fit takes the whole plan down with it &mdash; that is the pre-#46 mechanism, and it is why
     * the table is headed "% of seeds that still PLAN AT ALL". The assembler path added by #46 does
     * not work that way: {@code placeBossRoom} returns null when no draw fits, and the caller
     * reserves the ordinary synthetic terminal room instead. So the shipped 19x19 cannot delete a
     * dungeon, and the real question changed from "does it fit" to "how often do we get one".</p>
     *
     * <p>Both numbers are reported here, because the second is the check that the first claim is
     * true: adoption may be well under 100%, but planning must be exactly 100%.</p>
     *
     * <h2>The size is read off the shipped .nbt</h2>
     * <p>Not a hand-written 19. The whole point of re-measuring at hand-off is that the number now
     * tracks a real file, and a re-cut template that changes the fallback rate should change this
     * output without anyone remembering to edit a constant.</p>
     */
    @Test
    void measureTheShippedBossRoom() {
        List<int[]> shipped = shippedEndRoomFootprints();
        assertFalse(shipped.isEmpty(),
                "no end_rooms template ships, so there is nothing to measure -- if the pool was"
                        + " emptied again, this probe should go back to being a placeholder test");

        System.out.println("=== #46 shipped boss room: adoption vs planning ===");
        for (int[] wd : shipped) {
            System.out.printf("template footprint %dx%d%n", wd[0], wd[1]);
            for (DungeonSize size : DungeonSize.values()) {
                int planned = 0;
                int adopted = 0;
                for (int i = 0; i < DUNGEONS; i++) {
                    long seed = 0xD2_4600_0002L + i * 7919L;
                    Optional<DungeonLayout> layout = planWithBoss(seed, size, wd[0], wd[1]);
                    if (layout.isEmpty()) {
                        continue;
                    }
                    planned++;
                    if (hasBossRoom(layout.get())) {
                        adopted++;
                    }
                }
                System.out.printf("  %-8s planned %5.1f%%   boss room adopted %5.1f%%%n",
                        size, 100.0 * planned / DUNGEONS, 100.0 * adopted / DUNGEONS);

                // The invariant the assembler path exists to provide. Adoption is calibration and
                // is allowed to be low; losing the DUNGEON is the failure #46 was careful to avoid,
                // and it is the one thing here that gates.
                assertEquals(DUNGEONS, planned,
                        "a boss room that does not fit must degrade to the synthetic terminal room,"
                                + " not take the dungeon with it -- " + size + " lost "
                                + (DUNGEONS - planned) + " plan(s) at " + wd[0] + "x" + wd[1]);
            }
        }
    }

    private static Optional<DungeonLayout> planWithBoss(long seed, DungeonSize size,
                                                        int width, int depth) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, "classic",
                new TemplateCatalog())
                .withSize(size)
                // Stands in for vanilla jigsaw at the shipped footprint, honouring the protocol:
                // the same shape BossRoomPlacementTest uses, with one authored door on its edge.
                .withBossRoomAssembler((worldX, worldY, worldZ, floorIndex, assemblySeed, commit) ->
                        Optional.of(new DungeonStackPlanner.AssembledRoom(
                                new Rectangle2D(worldX, worldZ, width, depth),
                                List.of(new Coords2D(worldX, worldZ + depth / 2)),
                                List.of())))
                .plan();
    }

    private static boolean hasBossRoom(DungeonLayout layout) {
        FloorLayout bottom = layout.getFloors().get(layout.getFloors().size() - 1);
        for (RoomData room : bottom.getRooms()) {
            if (room.getRole() == RoomRole.BOSS) {
                return true;
            }
        }
        return false;
    }

    /** The XZ footprint of every {@code .nbt} under {@code structures/end_rooms}. */
    private static List<int[]> shippedEndRoomFootprints() {
        List<int[]> out = new ArrayList<>();
        for (Path file : walkResource("/data/dungeons2/structures/end_rooms")) {
            try (InputStream in = Files.newInputStream(file)) {
                CompoundTag tag = NbtIo.readCompressed(in);
                var size = tag.getList("size", Tag.TAG_INT);
                out.add(new int[] {size.getInt(0), size.getInt(2)});
            } catch (Exception unreadable) {
                fail("could not read " + file + ": " + unreadable);
            }
        }
        return out;
    }

    private static List<Path> walkResource(String root) {
        URL url = TerminalRoomFitProbe.class.getResource(root);
        if (url == null) {
            return List.of();
        }
        try {
            URI uri = url.toURI();
            Path base;
            if ("jar".equals(uri.getScheme())) {
                base = FileSystems.newFileSystem(uri, Map.of()).getPath(root);
            } else {
                base = Paths.get(uri);
            }
            try (Stream<Path> walk = Files.walk(base)) {
                return walk.filter(path -> path.toString().endsWith(".nbt")).sorted().toList();
            }
        } catch (Exception unreadable) {
            return fail("could not walk " + root + ": " + unreadable);
        }
    }
}
