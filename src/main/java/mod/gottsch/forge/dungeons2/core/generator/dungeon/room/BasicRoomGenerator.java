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

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
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
 * <p>All three sub-builders draw their blocks from a {@link MotifConfig} (default: all plain
 * stone_bricks, see {@link MotifConfig#DEFAULT}) injected via {@link #withMotifConfig}, resolved by
 * the caller from the datapack registry &mdash; same "resolve once where {@code RegistryAccess} is
 * available, inject the resolved value" shape as {@code DungeonStackPlanner#withCorridorWidth}.</p>
 *
 * <p>Decoration comes from a single {@link RoomScheme} rolled once per room by
 * {@link RoomSchemeSelector}, which each sub-builder then reads its own slot from. One roll rather
 * than one per element is what keeps a room's floor, walls and ceiling parts of the same authored
 * style instead of three independent draws. The roll uses this room's own {@code random}, so it
 * stays deterministic across the repeated {@code postProcess} calls a piece gets per overlapping
 * chunk &mdash; which is also why it must happen exactly once, here, and be passed down rather than
 * re-rolled by any sub-builder.</p>
 *
 * @author Mark Gottschling on Dec 7, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicRoomGenerator implements IRoomGenerator {

    private MotifConfig motifConfig = MotifConfig.DEFAULT;

    public BasicRoomGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, RoomPlacements out) {
        RoomScheme scheme = selectScheme(room, random);
        List<BlockPlacement> blocks = out.getBlocks();

        IDungeonWallGenerator wallGen = selectWallGenerator(motif, scheme);
        IDungeonFloorGenerator floorGen = selectFloorGenerator(motif, scheme);
        IDungeonCeilingGenerator ceilingGen = selectCeilingGenerator(motif, scheme);

        // The renderer iterates this list in sequence and a later placement overwrites an earlier
        // one in the same cell, so order here is a layering order. Hollowing first establishes
        // "the room is empty" as a precondition the rest build on top of, which is what lets a
        // future interior feature (pillar, vault) simply run later and win the cells it needs
        // rather than having to coordinate with the step that emitted the air.
        //
        // The four steps below this one do not actually overlap -- volume is strictly interior,
        // walls are strictly the perimeter ring, floor is Y=floorY and ceiling Y=floorY+height-1 --
        // so their relative order is currently free. Do not rely on that when adding a step.
        RoomVolumeGenerator.hollow(room, floorY, blocks);
        wallGen.build(room, floorY, motif, random, blocks);
        floorGen.build(room, floorY, motif, random, blocks);
        ceilingGen.build(room, floorY, motif, random, blocks);

        // Props last: they stand ON the finished floor, and unlike the four steps above they emit
        // entities, which the piece writes to the world by a different route entirely.
        scheme.pots().ifPresent(pots ->
                RoomPropGenerator.placePots(room, floorY, pots, random, out.getEntities()));
    }

    /** The one decorative roll a room gets. See {@link RoomSchemeSelector}. */
    public RoomScheme selectScheme(RoomData room, RandomSource random) {
        return RoomSchemeSelector.select(motifConfig.schemes(),
                room.getWidth(), room.getDepth(), room.getHeight(), random);
    }

    /** Takes no slot from the scheme yet &mdash; wall treatments have no providers behind them. */
    public IDungeonWallGenerator selectWallGenerator(IDungeonMotif motif, RoomScheme scheme) {
        return new BasicWallGenerator().withMotifConfig(motifConfig);
    }

    public IDungeonFloorGenerator selectFloorGenerator(IDungeonMotif motif, RoomScheme scheme) {
        return FloorPatternSelector.generatorFor(scheme.floor(), motifConfig.floor());
    }

    /** Takes no slot from the scheme yet &mdash; see {@link #selectWallGenerator}. */
    public IDungeonCeilingGenerator selectCeilingGenerator(IDungeonMotif motif, RoomScheme scheme) {
        return new BasicCeilingGenerator().withMotifConfig(motifConfig);
    }
}
