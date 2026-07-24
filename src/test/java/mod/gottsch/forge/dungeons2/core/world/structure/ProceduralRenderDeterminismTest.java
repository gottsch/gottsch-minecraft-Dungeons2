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

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the chunk-seam fix: procedural pieces render from a piece-stable seed
 * ({@code DungeonPiece#deterministicRandom}), not the chunk-seeded {@code RandomSource}
 * that {@code postProcess} is handed. Because {@link DungeonRoomPiece#renderPlacements()}
 * takes <em>no</em> external RNG, a piece's output is a pure function of its stable
 * state &mdash; so two chunks that both touch the piece produce byte-identical blocks in
 * the overlap.
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
class ProceduralRenderDeterminismTest {

    private static final String MOTIF = "classic";
    private static final int FLOOR_Y = 40;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RoomData room(int id) {
        RoomData r = new RoomData(id, 4, 4, 7, 7, 6, RoomRole.NORMAL);
        return r;
    }

    private static String dump(List<BlockPlacement> placements) {
        StringBuilder sb = new StringBuilder();
        for (BlockPlacement p : placements) {
            sb.append(p).append('\n');
        }
        return sb.toString();
    }

    @Test
    void sameStateRendersIdentically() {
        DungeonRoomPiece a = new DungeonRoomPiece(room(7), MOTIF, FLOOR_Y, 128, 256);
        DungeonRoomPiece b = new DungeonRoomPiece(room(7), MOTIF, FLOOR_Y, 128, 256);

        String first = dump(a.renderPlacements());
        // Re-render the same instance, and an independent instance with identical state.
        assertEquals(first, dump(a.renderPlacements()), "re-render of the same piece must match");
        assertEquals(first, dump(b.renderPlacements()), "identical state must render identically");
        assertFalse(first.isBlank(), "expected a non-empty render");
    }

    @Test
    void renderTakesNoExternalRandom() {
        // The render is reproducible across many calls with nothing but stable state
        // passed in — i.e. it cannot depend on a chunk-seeded RandomSource.
        DungeonRoomPiece piece = new DungeonRoomPiece(room(3), MOTIF, FLOOR_Y, -512, 64);
        String baseline = dump(piece.renderPlacements());
        for (int i = 0; i < 8; i++) {
            assertEquals(baseline, dump(piece.renderPlacements()),
                    "render must be invariant across repeated calls (call " + i + ")");
        }
    }
}
