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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.PotConfig;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Places a room scheme's loot pots: picks cells, picks variants, and hands back
 * {@link EntityPlacement}s. The one generator in this package that emits entities rather than
 * blocks.
 *
 * <h2>Where a pot may stand</h2>
 * <p>Candidates are the <strong>inner ring</strong> of the room's interior &mdash; the interior
 * cells that touch a wall. Three reasons, in order of how much they matter:</p>
 * <ol>
 *   <li><strong>It has to be a floor cell.</strong> {@code PotEntity} has gravity and a
 *       fall-break distance: a pot with nothing under it falls and shatters before a player ever
 *       sees it. Interior cells are exactly the ones the floor generator fills, so this is the
 *       safe set. (Same failure the corridor's {@code gravel} alternate floor had &mdash; a
 *       falling block in a place nothing checked.)</li>
 *   <li><strong>It should not be in a doorway.</strong> Doorways sit on the perimeter ring, so no
 *       interior cell is ever <em>in</em> one &mdash; but the cell immediately inside a door is
 *       where a player walks through, so those are excluded too. A pot there gets shoved or
 *       smashed on the way in.</li>
 *   <li><strong>It looks right.</strong> Pots belong against a wall; a pot alone in the middle of
 *       an open floor reads as dropped, not placed.</li>
 * </ol>
 *
 * <h2>Determinism</h2>
 * <p>Everything here is a pure function of the room and the {@link RandomSource} it is handed,
 * which the caller seeds from chunk-independent piece state. That matters more for entities than
 * for blocks: a piece renders once per overlapping chunk, and the consumer relies on every one of
 * those runs producing an <em>identical</em> plan so that clipping to the chunk box spawns each pot
 * exactly once. A plan that varied per chunk would drop pots on seams or duplicate them.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public final class RoomPropGenerator {

    private RoomPropGenerator() {}

    /**
     * Emits this room's pots. A count is rolled from the config's inclusive range, then that many
     * distinct cells are drawn from the eligible set; a room with fewer eligible cells than the
     * rolled count simply gets fewer pots rather than stacking two in one cell. Both of those are
     * {@link CellDraw}'s job &mdash; see there for why it hands cells out one at a time.
     */
    public static void placePots(RoomData room, int floorY, PotConfig config, RandomSource random,
                                 List<EntityPlacement> out) {
        placePots(room, floorY, config, Set.of(), random, out);
    }

    /**
     * As above, with {@code occupied} naming cells already taken by something the props must not
     * stand in &mdash; today, the room's projecting wall trim at floor level (see
     * {@code IDungeonWallGenerator#occupiedFloorCells}).
     */
    public static void placePots(RoomData room, int floorY, PotConfig config, Set<Coords2D> occupied,
                                 RandomSource random, List<EntityPlacement> out) {
        List<PotConfig.PotVariant> variants = config.variants();
        if (variants.isEmpty()) {
            return;
        }
        int totalVariantWeight = variants.stream().mapToInt(PotConfig.PotVariant::weight).sum();
        if (totalVariantWeight <= 0) {
            return;
        }

        List<Coords2D> candidates = eligibleCells(room, occupied);
        if (candidates.isEmpty()) {
            return;
        }

        CellDraw draw = CellDraw.of(candidates, config.minCount(), config.clampedMaxCount(), random);
        while (draw.hasNext()) {
            Coords2D cell = draw.next();

            EntityPlacement pot = new EntityPlacement(
                    cell.getX(), floorY + 1, cell.getY(),
                    pickVariant(variants, totalVariantWeight, random),
                    random.nextFloat() * 360.0F,
                    config.lootTable(),
                    lootSeed(random));
            out.add(pot);
        }
    }

    /** Ungated form: no cells claimed by anything else. */
    static List<Coords2D> eligibleCells(RoomData room) {
        return eligibleCells(room, Set.of());
    }

    /**
     * Interior cells that touch a wall, minus the cells immediately inside a doorway and minus
     * {@code occupied}. Returned in floor-local coords, the same space as
     * {@code RoomData#getOriginX}/{@code getDoorways}.
     *
     * <p>{@code occupied} is what makes pots and projecting wall trim able to share a room. A
     * pilaster stands in an inner-ring cell at exactly pot height, and unlike the floor-level
     * projecting course &mdash; an authoring slip avoidable by projecting the top instead &mdash;
     * that is true of every strip on the wall, by construction. Excluding the cells is the only
     * resolution that does not make the two mutually exclusive in a scheme.</p>
     */
    static List<Coords2D> eligibleCells(RoomData room, Set<Coords2D> occupied) {
        int width = room.getWidth();
        int depth = room.getDepth();
        int originX = room.getOriginX();
        int originZ = room.getOriginZ();

        Set<Coords2D> blocked = RoomInterior.cellsInsideDoorways(room);
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                boolean touchesWall = x == 1 || x == width - 2 || z == 1 || z == depth - 2;
                if (!touchesWall) {
                    continue;
                }
                Coords2D cell = new Coords2D(originX + x, originZ + z);
                if (!blocked.contains(cell) && !occupied.contains(cell)) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }

    // cellsInsideDoorways moved to RoomInterior when free-standing pillars needed the same rule.

    private static String pickVariant(List<PotConfig.PotVariant> variants, int totalWeight,
                                      RandomSource random) {
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (PotConfig.PotVariant variant : variants) {
            cumulative += variant.weight();
            if (roll < cumulative) {
                return variant.entity();
            }
        }
        return variants.get(variants.size() - 1).entity(); // unreachable
    }

    /**
     * A non-zero loot seed. Zero is not a neutral value to {@code PotEntity}: it means "roll the
     * table fresh when the pot is broken" rather than fixing the contents now, so the one draw in
     * 2^64 that comes back zero would quietly behave differently from every other pot.
     */
    private static long lootSeed(RandomSource random) {
        long seed = random.nextLong();
        return seed == 0L ? 1L : seed;
    }
}
