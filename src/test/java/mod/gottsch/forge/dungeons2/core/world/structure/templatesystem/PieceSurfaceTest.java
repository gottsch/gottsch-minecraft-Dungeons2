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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorPatternSelector;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the one invariant {@link PieceSurface} rests on &mdash; <em>the floor, and only the floor,
 * is at piece-relative Y 0</em> &mdash; on <strong>both</strong> halves of the pipeline, because a
 * surface gate that held for procedural rooms and not for prefabs would decay the two sides of a
 * shared wall differently. Since backlog #15 it also pins what {@link PieceSurfaceMap} calls each
 * part of a room, which is where every surface above the floor is decided.
 *
 * <p>The template half is worth having whatever happens to the gate: it is the only thing that
 * would catch an author building a room whose bottom layer is hollow, which places verbatim and is
 * silent in every log.</p>
 */
class PieceSurfaceTest {

    private static final String TEMPLATE_ROOT = "/data/dungeons2/structures";

    /**
     * Categories whose layer 0 is legitimately not a floor slab: a stairwell's is a partly-open
     * landing, an entrance's is partly terrain, a well's is the well. They are out of scope for a
     * floor gate rather than exceptions to it &mdash; see {@link PieceSurface}'s class doc.
     */
    private static final Set<String> NON_ROOM_CATEGORIES =
            Set.of("transitions", "entrances", "decorations");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---------- the enum itself ----------

    /**
     * The four primaries partition the piece and the two unions are exactly what they claim, both
     * asserted over the whole vocabulary rather than a sample &mdash; a value added later that
     * forgot to be either a primary or a union fails here.
     */
    @Test
    void theTwoUnionsAreExactlyTheirPrimaries() {
        for (PieceSurface primary : PRIMARIES) {
            assertTrue(PieceSurface.ANY.matches(primary), "ANY must cover " + primary);
            assertEquals(primary != PieceSurface.FLOOR, PieceSurface.ABOVE_FLOOR.matches(primary),
                    "ABOVE_FLOOR is every primary but FLOOR, and " + primary + " disagreed");

            long claimed = Stream.of(PieceSurface.values())
                    .filter(surface -> surface != PieceSurface.ANY
                            && surface != PieceSurface.ABOVE_FLOOR)
                    .filter(surface -> surface.matches(primary))
                    .count();
            assertEquals(1, claimed,
                    "exactly one non-union value may claim " + primary + ", or the gates stop"
                            + " partitioning the piece and a cell can decay on two schedules");
        }
    }

    /** {@link PieceSurfaceMap#classify} only ever answers with a primary. */
    @Test
    void classifyOnlyEverReturnsAPrimary() {
        PieceSurfaceMap surfaces = PieceSurfaceMap.of(ROOM_ORIGIN, room());
        for (int x = -1; x <= 5; x++) {
            for (int y = -1; y <= 7; y++) {
                for (int z = -1; z <= 5; z++) {
                    PieceSurface classified = surfaces.classify(ROOM_ORIGIN.offset(x, y, z));
                    assertTrue(PRIMARIES.contains(classified),
                            classified + " is a union, not a surface a cell can be");
                }
            }
        }
    }

    // ---------- backlog #15: what the classifier calls each part of a room ----------

    /** The values a cell can actually be; {@code ANY} and {@code ABOVE_FLOOR} are unions of them. */
    private static final Set<PieceSurface> PRIMARIES = Set.of(
            PieceSurface.FLOOR, PieceSurface.WALL, PieceSurface.CEILING, PieceSurface.JOIST);

    private static final BlockPos ROOM_ORIGIN = new BlockPos(48, 30, -112);

    /**
     * A 5x5x6 room shaped the way the procedural generators shape one: the floor insets by one so
     * nothing is paved under the wall ring, the walls span {@code [1..height-2]}, the ceiling
     * covers the interior only, and a beam hangs one course beneath it.
     */
    private static List<StructureTemplate.StructureBlockInfo> room() {
        List<StructureTemplate.StructureBlockInfo> piece = new ArrayList<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    for (int y = 1; y <= 4; y++) {
                        piece.add(cell(x, y, z));
                    }
                } else {
                    piece.add(cell(x, 0, z));
                    piece.add(cell(x, 5, z));
                }
            }
        }
        for (int x = 1; x <= 3; x++) {
            piece.add(cell(x, 4, 2));
        }
        return piece;
    }

    private static StructureTemplate.StructureBlockInfo cell(int x, int y, int z) {
        return new StructureTemplate.StructureBlockInfo(
                ROOM_ORIGIN.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), null);
    }

    @Test
    void aRoomClassifiesIntoFloorWallsCeilingAndBeam() {
        PieceSurfaceMap surfaces = PieceSurfaceMap.of(ROOM_ORIGIN, room());

        assertEquals(PieceSurface.FLOOR, surfaces.classify(ROOM_ORIGIN.offset(2, 0, 2)));
        assertEquals(PieceSurface.WALL, surfaces.classify(ROOM_ORIGIN.offset(0, 1, 0)),
                "the bottom course stands on the floor plane even where the floor insets away");
        assertEquals(PieceSurface.WALL, surfaces.classify(ROOM_ORIGIN.offset(0, 4, 0)),
                "the wall ring's top course is the highest thing in its column and is still a wall");
        assertEquals(PieceSurface.CEILING, surfaces.classify(ROOM_ORIGIN.offset(2, 5, 2)));
        assertEquals(PieceSurface.JOIST, surfaces.classify(ROOM_ORIGIN.offset(1, 4, 2)),
                "a beam hangs with the ceiling still above it");
    }

    /**
     * {@code structure_void} places nothing and leaves the terrain, so the piece has no surface
     * there and must not be given a ceiling over the void column the {@code 11x11_corner_*}
     * templates deliberately carry.
     */
    @Test
    void aVoidColumnIsNotOccupiedAndGrowsNoCeiling() {
        List<StructureTemplate.StructureBlockInfo> piece = new ArrayList<>(room());
        BlockPos voided = ROOM_ORIGIN.offset(1, 5, 1);
        piece.removeIf(info -> info.pos().equals(voided));
        piece.add(new StructureTemplate.StructureBlockInfo(
                voided, Blocks.STRUCTURE_VOID.defaultBlockState(), null));

        PieceSurfaceMap surfaces = PieceSurfaceMap.of(ROOM_ORIGIN, piece);
        assertFalse(surfaces.isOccupied(voided));
        assertEquals(PieceSurface.WALL, surfaces.classify(voided),
                "an unfilled cell above the floor falls back to the ungated answer, not a ceiling");
    }

    @Test
    void relativeYIsMeasuredFromThePieceOrigin() {
        BlockPos origin = new BlockPos(112, 37, -64);
        assertEquals(0, PieceSurface.relativeY(origin, new BlockPos(115, 37, -60)));
        assertEquals(5, PieceSurface.relativeY(origin, new BlockPos(115, 42, -60)));
    }

    // ---------- half one: the procedural piece ----------

    /**
     * The claim, stated as an equality rather than a sample: the placements a full room build puts
     * on {@code floorY} are <em>exactly</em> the ones the floor generator produces on its own. Any
     * wall, ceiling or volume block reaching layer 0 breaks it, and so does a floor block that
     * misses it.
     */
    @Test
    void proceduralRoomPutsOnlyTheFloorOnLayerZero() {
        int floorY = 60;
        RoomData room = new RoomData(1, 10, 10, 7, 7, 5, RoomRole.NORMAL);

        RoomPlacements roomOut = new RoomPlacements();
        new BasicRoomGenerator().build(room, floorY, 0, DungeonMotif.CLASSIC,
                RandomSource.create(99L), roomOut);

        List<BlockPlacement> floorOnly = new ArrayList<>();
        FloorPatternSelector.plain(MotifConfig.DEFAULT.floor())
                .build(room, floorY, DungeonMotif.CLASSIC, RandomSource.create(99L), floorOnly);

        Set<String> layerZero = new LinkedHashSet<>();
        for (BlockPlacement placement : roomOut.getBlocks()) {
            if (placement.getY() == floorY) {
                layerZero.add(key(placement));
            }
        }
        Set<String> expected = new LinkedHashSet<>();
        floorOnly.forEach(placement -> expected.add(key(placement)));

        assertFalse(expected.isEmpty(), "the floor generator produced nothing to compare against");
        assertEquals(expected, layerZero,
                "layer 0 of a procedural room must be the floor and nothing else");
    }

    /**
     * <strong>The parity guard for #3 (pits).</strong> Layer 0 is the floor by decision, and a
     * template gets that for free because a pit sinks the template's own lowest layer. The
     * procedural half does not: its origin is {@code floorY}, so a pit dug below the floor would
     * put its floor at a NEGATIVE relative Y while layer 0 stayed the room floor &mdash; the
     * opposite of what the prefab beside it does. This fails the day that happens, which is the
     * signal that procedural pits must sink the piece origin with the pit.
     */
    @Test
    void proceduralRoomHasNothingBelowLayerZero() {
        int floorY = 60;
        RoomPlacements out = new RoomPlacements();
        new BasicRoomGenerator().build(new RoomData(1, 10, 10, 7, 7, 5, RoomRole.NORMAL),
                floorY, 0, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        for (BlockPlacement placement : out.getBlocks()) {
            assertTrue(placement.getY() >= floorY,
                    "a placement at Y=" + placement.getY() + " sits below the piece origin,"
                            + " which would make relativeY negative");
        }
    }

    private static String key(BlockPlacement placement) {
        return placement.getX() + "," + placement.getY() + "," + placement.getZ();
    }

    // ---------- half two: the shipped prefabs ----------

    // ---------- half two: the shipped prefabs -- REMOVED 2026-09-07 ----------
    //
    // Two tests lived here, and both asserted something that is not true of an authored room:
    //
    //   everyShippedRoomTemplateHasASolidFloorOnLayerZero -- every cell of layer 0 present and
    //     non-air.
    //   everyShippedRoomTemplateOpensUpOnLayerOne         -- layer 1 must contain air.
    //
    // Mark, 2026-09-07: "layer-0 could have air or structure_void authored into it. And layer 1
    // won't necessarily carry air either in an authored room." Both premises are wrong, and the
    // failures they produced were all correct templates:
    //
    //   - the mud 11x11 corners omit a structure_void cell from layer 0. structure_void is not
    //     saved into the blocks list, so a template that uses one legitimately has fewer layer-0
    //     entries than its footprint. The test read that as a hole.
    //   - 12x29_sunken_hallway_1b is one of THREE pieces that join into a 12x29 hallway; it is
    //     named for the assembled length, and only the columns it actually owns carry blocks.
    //   - the sewers have water on layer 1, not air, because the channel is the room.
    //
    // There is nothing to weaken these into. "Layer 0 is the floor" is a useful default for the
    // PROCEDURAL pieces, which is what the first half of this file still covers through
    // PieceSurface itself; on the prefab side it is an authoring choice per template, and a test
    // that cannot tell a sunken floor from a hole is a test that will be silenced rather than
    // read. Deleted rather than @Disabled so it does not come back as a puzzle.

    // ---------- reading the binary templates ----------

    private static boolean isOutOfScope(Path file) {
        String path = file.toString().replace(File.separatorChar, '/');
        return NON_ROOM_CATEGORIES.stream().anyMatch(category -> path.contains("/" + category + "/"));
    }

    private static List<String> palette(CompoundTag root) {
        List<String> names = new ArrayList<>();
        for (Tag tag : root.getList("palette", Tag.TAG_COMPOUND)) {
            names.add(((CompoundTag) tag).getString("Name"));
        }
        return names;
    }

    private static CompoundTag read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read template " + file, unreadable);
        }
    }

    private static List<Path> templateFiles() {
        URL url = PieceSurfaceTest.class.getResource(TEMPLATE_ROOT);
        if (url == null) {
            return fail("no shipped templates at " + TEMPLATE_ROOT);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + TEMPLATE_ROOT + ": " + unreadable);
        }
    }
}
