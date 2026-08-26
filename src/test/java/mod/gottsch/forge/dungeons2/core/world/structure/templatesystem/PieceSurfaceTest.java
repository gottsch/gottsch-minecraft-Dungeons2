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
 * <strong>PROTOTYPE.</strong> Pins the one invariant {@link PieceSurface} rests on &mdash;
 * <em>the floor, and only the floor, is at piece-relative Y 0</em> &mdash; on <strong>both</strong>
 * halves of the pipeline, because a surface gate that held for procedural rooms and not for
 * prefabs would decay the two sides of a shared wall differently.
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

    @Test
    void floorMatchesLayerZeroOnlyAndAboveFloorIsItsComplement() {
        assertTrue(PieceSurface.FLOOR.matches(0));
        assertFalse(PieceSurface.FLOOR.matches(1));
        assertFalse(PieceSurface.ABOVE_FLOOR.matches(0));
        assertTrue(PieceSurface.ABOVE_FLOOR.matches(1));
        for (int relativeY = 0; relativeY < 24; relativeY++) {
            assertTrue(PieceSurface.ANY.matches(relativeY));
            assertEquals(PieceSurface.FLOOR.matches(relativeY),
                    !PieceSurface.ABOVE_FLOOR.matches(relativeY),
                    "FLOOR and ABOVE_FLOOR must partition every layer at relativeY=" + relativeY);
        }
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

    /**
     * Every shipped room / hallway template must have a <em>complete</em>, non-air layer 0 &mdash;
     * one block for every cell of its footprint. That is what makes relative Y 0 mean "floor" on
     * the prefab side.
     */
    @Test
    void everyShippedRoomTemplateHasASolidFloorOnLayerZero() {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Path file : templateFiles()) {
            if (isOutOfScope(file)) {
                continue;
            }
            checked++;
            CompoundTag root = read(file);
            int sizeX = root.getList("size", Tag.TAG_INT).getInt(0);
            int sizeZ = root.getList("size", Tag.TAG_INT).getInt(2);
            List<String> palette = palette(root);

            int cells = 0;
            int openings = 0;
            for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) tag;
                if (block.getList("pos", Tag.TAG_INT).getInt(1) != PieceSurface.FLOOR_RELATIVE_Y) {
                    continue;
                }
                cells++;
                // structure_void is NOT an opening as far as the gate is concerned: it places
                // nothing, so no block ever reaches a processor at that cell and there is nothing
                // to mis-gate.
                //
                // Three shipped templates have one -- a full-height void column at x=1,z=9 in each
                // of the 11x11 corners. That is DELIBERATE (Mark, 2026-08-26): structure_void means
                // "leave whatever is here", so the column is filled with vanilla-generated terrain,
                // not opened to air. Do not "fix" it.
                //
                // `air` IS an opening: it places, and a floor cell authored as air means layer 0 is
                // not reliably the floor.
                if (palette.get(block.getInt("state")).equals("minecraft:air")) {
                    openings++;
                }
            }
            int footprint = sizeX * sizeZ;
            if (cells != footprint || openings > 0) {
                offenders.add(file.getFileName() + "  layer0=" + cells + "/" + footprint
                        + ", air-or-void=" + openings);
            }
        }
        assertTrue(checked >= 20, "expected the shipped room set, read " + checked + " template(s)");
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " room template(s) do not have a solid floor on layer 0, which"
                    + " is what PieceSurface.FLOOR keys off. A hollow layer 0 places verbatim and"
                    + " is silent in every log:\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * The other direction, and the one that actually distinguishes "layer 0 is the floor" from
     * "layer 0 is solid": layer 1 must be where the walls start, i.e. it must contain air. A
     * template whose layer 1 were also solid would mean the floor is two blocks thick and the gate
     * is catching only half of it.
     */
    @Test
    void everyShippedRoomTemplateOpensUpOnLayerOne() {
        List<String> offenders = new ArrayList<>();
        for (Path file : templateFiles()) {
            if (isOutOfScope(file)) {
                continue;
            }
            CompoundTag root = read(file);
            List<String> palette = palette(root);
            boolean sawAir = false;
            for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) tag;
                if (block.getList("pos", Tag.TAG_INT).getInt(1) != PieceSurface.FLOOR_RELATIVE_Y + 1) {
                    continue;
                }
                if (palette.get(block.getInt("state")).equals("minecraft:air")) {
                    sawAir = true;
                    break;
                }
            }
            if (!sawAir) {
                offenders.add(file.getFileName().toString());
            }
        }
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " room template(s) have no air on layer 1, so layer 0 is not"
                    + " the only floor layer:\n  " + String.join("\n  ", offenders));
        }
    }

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
