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

import mod.gottsch.forge.dungeons2.core.config.FloorPatternConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.BasicCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.IDungeonCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorPatternSelector;
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
 * <p>The floor sub-builder is the one exception: {@link #selectFloorGenerator} rolls a weighted
 * pick from a {@link FloorPatternConfig} (default: always plain, see
 * {@link FloorPatternConfig#DEFAULT}) via {@link #withFloorPatternConfig}, resolved by the caller
 * from the datapack registry &mdash; same "resolve once where {@code RegistryAccess} is
 * available, inject the resolved value" shape as {@code DungeonStackPlanner#withCorridorWidth}.
 * The roll uses this room's own {@code random}, so it stays deterministic across the repeated
 * {@code postProcess} calls a piece gets per overlapping chunk.</p>
 *
 * @author Mark Gottschling on Dec 7, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicRoomGenerator implements IRoomGenerator {

    private FloorPatternConfig floorPatternConfig = FloorPatternConfig.DEFAULT;

    public BasicRoomGenerator withFloorPatternConfig(FloorPatternConfig floorPatternConfig) {
        this.floorPatternConfig = floorPatternConfig;
        return this;
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        IDungeonWallGenerator wallGen = selectWallGenerator(motif);
        IDungeonFloorGenerator floorGen = selectFloorGenerator(motif, random);
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

    public IDungeonFloorGenerator selectFloorGenerator(IDungeonMotif motif, RandomSource random) {
        return FloorPatternSelector.select(floorPatternConfig, random);
    }

    public IDungeonCeilingGenerator selectCeilingGenerator(IDungeonMotif motif) {
        return new BasicCeilingGenerator();
    }
}
