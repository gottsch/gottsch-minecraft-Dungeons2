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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 deliverable checks for the {@link DungeonPieceEmitter} bridge:
 *
 * <ul>
 *     <li><strong>Determinism</strong> &mdash; the same seed plans + emits a
 *         byte-identical piece list (same count, same classes, same bounding
 *         boxes).</li>
 *     <li><strong>Boundedness</strong> &mdash; no emitted piece strays beyond the
 *         planner's overall dungeon AABB (within a small wall-margin tolerance),
 *         which catches gross coordinate-mapping errors in the emitter.</li>
 * </ul>
 *
 * <p>The emitter is exercised with a {@code null} {@link net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager}
 * so only the procedural room / corridor / door pieces are produced &mdash; the
 * template-backed entrance / transition pieces need a worldgen-loaded manager and
 * are covered once Phase 5 makes the structure live.</p>
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
class DungeonStructureEmitterTest {

    private static final long SEED = 0xD2_4A_2026L;
    private static final int ANCHOR_X = 128;
    private static final int ANCHOR_Z = 256;
    private static final int SURFACE_Y = 64;
    private static final String MOTIF = "classic";
    /** XZ slack to absorb the 1-cell wall margin room/corridor pieces add. */
    private static final int XZ_MARGIN = 2;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DungeonLayout plan() {
        ICoords anchor = new Coords(ANCHOR_X, 0, ANCHOR_Z);
        return new DungeonStackPlanner(SEED, anchor, SURFACE_Y, MOTIF, new TemplateCatalog())
                .withSize(DungeonSize.SMALL)
                .withFloorCount(2)
                .plan()
                .orElseThrow(() -> new AssertionError("planner returned empty for fixed seed"));
    }

    @Test
    void sameSeedEmitsIdenticalPieceList() {
        List<StructurePiece> a = DungeonPieceEmitter.emit(plan(), ANCHOR_X, ANCHOR_Z);
        List<StructurePiece> b = DungeonPieceEmitter.emit(plan(), ANCHOR_X, ANCHOR_Z);

        assertFalse(a.isEmpty(), "emitter produced no pieces");
        assertEquals(a.size(), b.size(), "piece count differs across identical plans");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).getClass(), b.get(i).getClass(),
                    "piece type differs at index " + i);
            assertEquals(box(a.get(i)), box(b.get(i)),
                    "bounding box differs at index " + i);
        }
    }

    /**
     * {@code emit} has to stay equal to its two halves concatenated. {@code DungeonStructure} uses
     * the halves so it can slot the prefab rooms between them; the floor-plan exporter and these
     * tests use the whole. If the two drift, the thing that generates and the thing that draws the
     * plan stop being the same dungeon -- and nothing else would say so.
     */
    @Test
    void emitIsExactlyTerrainThenDoors() {
        DungeonLayout layout = plan();
        List<StructurePiece> whole = DungeonPieceEmitter.emit(layout, ANCHOR_X, ANCHOR_Z);
        List<StructurePiece> terrain = DungeonPieceEmitter.emitTerrain(layout, ANCHOR_X, ANCHOR_Z);
        List<StructurePiece> doors = DungeonPieceEmitter.emitDoors(layout, ANCHOR_X, ANCHOR_Z);

        assertFalse(doors.isEmpty(), "plan has no doors, so this proves nothing");
        assertEquals(whole.size(), terrain.size() + doors.size(), "halves do not cover the whole");
        for (int i = 0; i < terrain.size(); i++) {
            assertEquals(whole.get(i).getClass(), terrain.get(i).getClass(),
                    "terrain half diverges at index " + i);
            assertEquals(box(whole.get(i)), box(terrain.get(i)),
                    "terrain half diverges at index " + i);
        }
        for (int i = 0; i < doors.size(); i++) {
            assertEquals(box(whole.get(terrain.size() + i)), box(doors.get(i)),
                    "door half diverges at index " + i);
        }
    }

    /**
     * No door piece may be emitted before a terrain piece. Doors run last because both the room and
     * the corridor leave the door rows as air and the door piece is what builds the opening; it is
     * also what lets the prefab rooms render late without sealing their own doorways.
     */
    @Test
    void doorsComeAfterEveryTerrainPiece() {
        List<StructurePiece> pieces = DungeonPieceEmitter.emit(plan(), ANCHOR_X, ANCHOR_Z);
        boolean seenDoor = false;
        for (StructurePiece piece : pieces) {
            if (piece instanceof DungeonDoorPiece) {
                seenDoor = true;
            } else {
                assertFalse(seenDoor, "a non-door piece was emitted after a door piece: "
                        + piece.getClass().getSimpleName() + " -- doors must be last, or a prefab "
                        + "room rendered between them would seal its own doorways");
            }
        }
        assertTrue(seenDoor, "plan has no doors, so this proves nothing");
    }

    @Test
    void everyPieceStaysWithinDungeonBounds() {
        DungeonLayout layout = plan();
        List<StructurePiece> pieces = DungeonPieceEmitter.emit(layout, ANCHOR_X, ANCHOR_Z);

        int minX = layout.getBboxMin().getX() - XZ_MARGIN;
        int maxX = layout.getBboxMax().getX() + XZ_MARGIN;
        int minZ = layout.getBboxMin().getZ() - XZ_MARGIN;
        int maxZ = layout.getBboxMax().getZ() + XZ_MARGIN;
        int minY = layout.getBboxMin().getY();
        int maxY = layout.getBboxMax().getY();

        for (StructurePiece piece : pieces) {
            BoundingBox bb = piece.getBoundingBox();
            assertTrue(bb.minX() >= minX && bb.maxX() <= maxX,
                    "piece X out of bounds: " + bb);
            assertTrue(bb.minZ() >= minZ && bb.maxZ() <= maxZ,
                    "piece Z out of bounds: " + bb);
            assertTrue(bb.minY() >= minY && bb.maxY() <= maxY,
                    "piece Y out of bounds: " + bb);
        }
    }

    /** Component-wise box snapshot (BoundingBox#equals is value-based, but this is explicit). */
    private static String box(StructurePiece piece) {
        BoundingBox bb = piece.getBoundingBox();
        return bb.minX() + "," + bb.minY() + "," + bb.minZ() + " -> "
                + bb.maxX() + "," + bb.maxY() + "," + bb.maxZ();
    }
}
