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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.mining.MiningHaul;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.BasicCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.IDungeonCeilingGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.RoomPitGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.BasicPillarGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.IDungeonPillarGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.PillarPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform.BasicPlatformGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform.IDungeonPlatformGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform.PlatformPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.BasicWallGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.IDungeonWallGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.WallPatternSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private int sinkOffset = 0;
    private MiningHaul miningHaul;

    public BasicRoomGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    /**
     * The floor's budget below its walking plane (#29), which is the hard cap on any pit a scheme
     * digs. Zero &mdash; the shipped value, and the default here &mdash; means no room gets a pit
     * however its scheme is authored.
     */
    public BasicRoomGenerator withSinkOffset(int sinkOffset) {
        this.sinkOffset = Math.max(0, sinkOffset);
        return this;
    }

    /**
     * The Mining Chest's contents (#7), when this is the one room in the dungeon that carries it.
     *
     * <p>Injected rather than rolled, and this is the only slot in the room that works that way:
     * the haul is a property of the WHOLE dungeon's excavation, which only the emitter can see. A
     * room asked to compute it would compute the same answer as every other room, and every room
     * would place a chest. Null -- the default, and the case for every room but one in the whole
     * dungeon -- means no Mining Chest here.</p>
     */
    public BasicRoomGenerator withMiningHaul(MiningHaul miningHaul) {
        this.miningHaul = miningHaul;
        return this;
    }

    @Override
    public void build(RoomData room, int floorY, int floorIndex, IDungeonMotif motif,
                      RandomSource random, RoomPlacements out) {
        RoomScheme scheme = selectScheme(room, floorIndex, random);
        List<BlockPlacement> blocks = out.getBlocks();

        // Room dims are passed to the selectors because a scheme's element slots carry their own
        // size gates -- a slot the room fails is dropped while the rest of the scheme still draws.
        int width = room.getWidth();
        int depth = room.getDepth();
        int height = room.getHeight();

        IDungeonWallGenerator wallGen = selectWallGenerator(motif, scheme, width, depth, height);
        IDungeonFloorGenerator floorGen = selectFloorGenerator(motif, scheme, width, depth, height);
        IDungeonCeilingGenerator ceilingGen = selectCeilingGenerator(motif, scheme, width, depth, height);

        // The renderer iterates this list in sequence and a later placement overwrites an earlier
        // one in the same cell, so order here is a layering order. Hollowing first establishes
        // "the room is empty" as a precondition the rest build on top of, which is what lets a
        // future interior feature (pillar, vault) simply run later and win the cells it needs
        // rather than having to coordinate with the step that emitted the air.
        //
        // Their planes do not overlap -- walls are the perimeter ring, floor Y=floorY, ceiling
        // Y=floorY+height-1 -- but two of them reach INTO the volume: a wall's projecting trim (a
        // cornice) and a ceiling's projecting ribs (a coffer) both land in interior cells that
        // hollow() has just cleared. So running after hollow() is load-bearing for those two.
        //
        // Those two layers can also want the SAME cells, where a rib meets the cornice ring, so
        // ceiling-after-wall is load-bearing as well: the ceiling is meant to win, a rib running
        // into the cornice and interrupting it rather than stopping short of it. Floor is still
        // free to move.
        RoomVolumeGenerator.hollow(room, floorY, blocks);
        wallGen.build(room, floorY, motif, random, blocks);
        floorGen.build(room, floorY, motif, random, blocks);
        ceilingGen.build(room, floorY, motif, random, blocks);

        // #3: the pit comes out of the floor immediately after it is paved, and BEFORE anything
        // stands on it. The order is load-bearing in both directions -- after the floor because it
        // overwrites the slab it just laid (a later placement in the same cell wins), and before
        // pillars, platforms and props because those choose cells to stand on and a cell that is
        // now a hole is not one of them.
        //
        // #58: ORDER ALONE WAS NOT ENOUGH, and for a year this comment claimed more than the code
        // did. Running first only makes the pit's cells AVAILABLE to the steps after it; each of
        // those steps still has to ask. The props did ask, via `taken` below. The columns and daises
        // did not -- they build from a layout, and a layout knows the room's dimensions and nothing
        // about what has been carved out of it -- so a colonnade rolled over a pit put columns in
        // mid-air above the hole. They are handed the set explicitly now.
        Set<Coords2D> pit = scheme.pitFor(width, depth, height)
                .map(entry -> RoomPitGenerator.excavate(room, floorY, entry, sinkOffset,
                        motifConfig.floor(), random, blocks))
                .orElseGet(Set::of);

        // Pillars and platforms draw in the room's VOLUME rather than on one of its surfaces, which
        // is why both run after the four surface steps -- they stand in the interior air the hollow
        // step cleared, so anything that also reaches into it must already have been emitted.
        //
        // That makes a column win against a projecting ceiling rib where the two meet, which is the
        // right way round: a column is structure and a rib is decoration, so the column should read
        // as carrying the ceiling rather than being interrupted a block short of it.
        IDungeonPillarGenerator pillarGen = selectPillarGenerator(motif, scheme, width, depth, height);
        pillarGen.build(room, floorY, motif, random, blocks, pit);

        // Platforms after columns, and for a concrete reason rather than symmetry: both draw in the
        // interior air, and where a dais meets a column the column should be the thing standing on
        // it. Running the dais second lets it place its own cells around a column already there,
        // and its footprint check skips cells another platform took.
        IDungeonPlatformGenerator platformGen =
                selectPlatformGenerator(motif, scheme, width, depth, height);
        platformGen.build(room, floorY, motif, random, blocks, pit);

        // Props last: they stand ON the finished floor, and unlike the four steps above they emit
        // entities, which the piece writes to the world by a different route entirely.
        //
        // The wall's projecting trim gets right of way over them: it is part of the architecture and
        // was emitted before the pots were placed, so the pots move rather than spawning inside it.
        // Asking the generator which cells it took (rather than working it out here) keeps the rules
        // about which cells a projected layer actually reaches in the one place that has them.
        // Both the wall's projecting trim and the columns get right of way: each is architecture,
        // each was emitted before the pots, and a pot inside either is invisible until someone walks
        // into the room. Asking each generator which cells it took (rather than re-deriving them
        // here) keeps those rules in the one place that has them -- the columns' set in particular
        // is what actually got built, doorway drops and all, not what the layout asked for.
        Set<Coords2D> taken = new HashSet<>(wallGen.occupiedFloorCells());
        taken.addAll(pillarGen.occupiedFloorCells());
        taken.addAll(platformGen.occupiedFloorCells());
        // A pit's cells are not floor at all, so nothing may stand ON them. Without this a chest or
        // spawner is placed in mid-air over the hole and a pot drops in and shatters as soon as the
        // chunk ticks -- the same gravity trap the chest/pot ordering above exists for.
        taken.addAll(pit);

        // Spawners before pots, and they claim their cells against them. Not because the two
        // collide -- the spawner block is invisible and has no collision, so a pot would sit in one
        // without complaint -- but because the mobs materialise at that cell and would break the pot
        // on their way out, which reads as a bug rather than an ambush.
        //
        // Emitted into the BLOCK list even though nothing about them is visible: the block entity is
        // how a spawner exists at all, and DungeonPiece writes block-entity placements after the
        // decoration pass precisely so an interior air cell cannot overwrite one.
        // The depth axis: which mob sets this room's spawners draw from is the FLOOR's decision by
        // default, and only the scheme's if the scheme said so. That is what lets one hall scheme be
        // authored once and get harder the deeper it is rolled, instead of needing a near-duplicate
        // scheme per depth band. The band carries the mobs-per-spawn counts too, so a deeper floor
        // can be more crowded as well as nastier. See SpawnerConfig#resolvedAgainst.
        scheme.spawnersFor(width, depth, height).ifPresent(spawners ->
                taken.addAll(RoomSpawnerGenerator.placeSpawners(room, floorY, floorIndex,
                        spawners.resolvedAgainst(motifConfig.bandFor(floorIndex)),
                        taken, random, blocks)));

        // Chests before pots and claiming their cells, for a blunter reason than the spawners':
        // a chest is a SOLID block, so a pot entity spawned in the same cell stands inside it and,
        // having gravity, falls and shatters as soon as the chunk ticks.
        scheme.chestsFor(width, depth, height).ifPresent(chests ->
                taken.addAll(RoomChestGenerator.placeChests(room, floorY,
                        chests.resolvedAgainst(motifConfig.chestBandFor(floorIndex)),
                        taken, random, blocks)));

        // The Mining Chest, before the pots and claiming its cell for the same reason an ordinary
        // chest does: it is a solid block, and a pot entity standing inside one falls and shatters
        // the moment the chunk ticks. After the scheme's own chests rather than before, so that a
        // room whose scheme rolled chests still gets them -- this one is guaranteed by the plan and
        // the scheme's are not, so it is this one that should give way if the floor runs out.
        taken.addAll(RoomMiningChestGenerator.placeChest(room, floorY, miningHaul, taken, random,
                blocks));

        scheme.potsFor(width, depth, height).ifPresent(pots ->
                RoomPropGenerator.placePots(room, floorY, pots, taken, random, out.getEntities()));
    }

    public IDungeonPlatformGenerator selectPlatformGenerator(IDungeonMotif motif, RoomScheme scheme,
                                                            int width, int depth, int height) {
        return new BasicPlatformGenerator().withPlatformLayouts(PlatformPatternSelector.layoutsFor(
                scheme.platformsFor(width, depth, height), width, depth, height));
    }

    public IDungeonPillarGenerator selectPillarGenerator(IDungeonMotif motif, RoomScheme scheme,
                                                        int width, int depth, int height) {
        return new BasicPillarGenerator().withPillarLayouts(PillarPatternSelector.layoutsFor(
                scheme.pillarsFor(width, depth, height), width, depth, height));
    }

    /** The one decorative roll a room gets. See {@link RoomSchemeSelector}. */
    public RoomScheme selectScheme(RoomData room, int floorIndex, RandomSource random) {
        return RoomSchemeSelector.select(motifConfig.schemes(),
                room.getWidth(), room.getDepth(), room.getHeight(), floorIndex, random);
    }

    public IDungeonWallGenerator selectWallGenerator(IDungeonMotif motif, RoomScheme scheme,
                                                    int width, int depth, int height) {
        return new BasicWallGenerator()
                .withMotifConfig(motifConfig)
                .withWallPattern(WallPatternSelector.providerFor(
                        scheme.wallFor(width, depth, height), motifConfig.wall(),
                        width, depth, height));
    }

    public IDungeonFloorGenerator selectFloorGenerator(IDungeonMotif motif, RoomScheme scheme,
                                                      int width, int depth, int height) {
        return FloorPatternSelector.generatorFor(scheme.floorFor(width, depth, height), motifConfig.floor());
    }

    public IDungeonCeilingGenerator selectCeilingGenerator(IDungeonMotif motif, RoomScheme scheme,
                                                          int width, int depth, int height) {
        return new BasicCeilingGenerator()
                .withMotifConfig(motifConfig)
                .withCeilingPattern(CeilingPatternSelector.providerFor(
                        scheme.ceilingFor(width, depth, height), motifConfig.ceiling(),
                        width, depth, height));
    }
}
