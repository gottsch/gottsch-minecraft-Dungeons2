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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.BasicCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.IDungeonCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.BasicFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.BasicWallGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.IDungeonWallGenerator;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Default room orchestrator: delegates walls, floor, and ceiling to the three
 * sub-builders. Phase 2 dropped the {@code IRoomElementDecorator} chain that
 * the original implementation called &mdash; the decorators still wrote
 * blocks via {@code ServerLevel} and need their own Phase 8 refactor before
 * they can sit on top of this pipeline.
 *
 * <p>Sub-builder selection is currently hard-coded; the original code shipped
 * with motif-aware lookups stubbed (returning a {@code Basic*} for every
 * motif). Real per-motif specialization happens in a later phase.</p>
 *
 * @author Mark Gottschling on Dec 7, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicRoomGenerator implements IRoomGenerator {

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        IDungeonWallGenerator wallGen = selectWallGenerator(motif);
        IDungeonFloorGenerator floorGen = selectFloorGenerator(motif);
        IDungeonCeilingGenerator ceilingGen = selectCeilingGenerator(motif);

        // Order matters when the renderer iterates the list in sequence
        // and a later placement overwrites an earlier one (e.g., interior
        // air from the wall step gets the final say over anything the
        // floor step happens to put in the same cell). Keeping walls last
        // here would lose the interior air; we do walls FIRST so the floor
        // step has the final word at Y=floorY and ceiling at Y=floorY+height-1.
        wallGen.build(room, floorY, motif, random, out);
        floorGen.build(room, floorY, motif, random, out);
        ceilingGen.build(room, floorY, motif, random, out);
    }

    public IDungeonWallGenerator selectWallGenerator(IDungeonMotif motif) {
        return new BasicWallGenerator();
    }

    public IDungeonFloorGenerator selectFloorGenerator(IDungeonMotif motif) {
        return new BasicFloorGenerator();
    }

    public IDungeonCeilingGenerator selectCeilingGenerator(IDungeonMotif motif) {
        return new BasicCeilingGenerator();
    }
}
