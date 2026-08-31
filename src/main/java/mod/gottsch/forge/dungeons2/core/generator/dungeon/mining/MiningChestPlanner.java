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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.mining;

import mod.gottsch.forge.dungeons2.core.config.MiningConfig;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides what one dungeon's Mining Chest holds and which room it stands in &mdash; backlog #7.
 *
 * <h2>Why this runs at emit time and not in the room</h2>
 * <p>The haul is a property of the <em>whole dungeon</em>: it is what the whole excavation destroyed.
 * A room piece knows its own {@code RoomData}, floor and motif and nothing else, so it cannot
 * compute this and could not be trusted to if it could &mdash; every room would tally the same
 * dungeon and every room would place a chest. Exactly one plan is made here, where the
 * {@link DungeonLayout} is in hand, and it names the one room that gets it.</p>
 *
 * <h2>Deterministic from the layout's own seed</h2>
 * <p>{@link DungeonLayout#getSeed()}, salted. Not a fresh random and not the chunk random: the plan
 * has to be the same on every generation of the same dungeon, and the piece that carries it is
 * serialized to the save and read back on load.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
public final class MiningChestPlanner {

    /**
     * Salt for the layout seed. Any constant does; this one keeps the mining rolls from marching in
     * step with anything else that seeds off the same layout, which is the failure that makes two
     * unrelated features correlate for reasons nobody can find later.
     */
    private static final long SEED_SALT = 0x4D494E494E47L; // "MINING"

    private MiningChestPlanner() {}

    /**
     * Where the Mining Chest goes and what is in it.
     *
     * @param floorIndex the floor of the room named below, for logging and for the piece to check
     * @param roomId     {@link RoomData#getId()} of the room that places the chest
     */
    public record MiningChestPlan(int floorIndex, int roomId, MiningHaul haul) {}

    /**
     * Plans this dungeon's one Mining Chest, or empty when there is nothing to pay back or nowhere
     * to put it.
     *
     * <p>Empty is a normal outcome, not a failure: a config with no ore bands (the fallback when a
     * datapack removed the file), a payout fraction of 0, a dungeon shallow and small enough that
     * every band rounded to nothing, or a layout whose every room is an authored template. In each
     * case the honest result is no chest &mdash; an empty one costs the player a walk to find out it
     * was empty, the same call {@code ChestConfig} makes for an unresolvable loot table.</p>
     */
    public static Optional<MiningChestPlan> plan(DungeonLayout layout, MiningConfig config) {
        if (layout == null || config == null || config.ores().isEmpty()
                || config.payoutFraction() <= 0.0D) {
            return Optional.empty();
        }
        RandomSource random = RandomSource.create(layout.getSeed() ^ SEED_SALT);

        MiningHaul haul = haulFor(layout, config, random);
        if (haul.isEmpty()) {
            return Optional.empty();
        }
        // The haul is rolled BEFORE the location is drawn, and both come off the same random. Order
        // matters only in that it must not change: swap these two and every existing seed's chest
        // moves and its contents change, for no reason a player could ever be told.
        return pickRoom(layout, config, random)
                .map(room -> new MiningChestPlan(room.floorIndex(), room.roomId(), haul));
    }

    /**
     * The estimated haul: every band applied to every excavation it overlaps, scaled by the payout
     * fraction, then rounded.
     *
     * <h2>Rounding is a roll, not a floor</h2>
     * <p>Most bands produce a fractional expectation &mdash; 0.4 of a diamond is the normal case, not
     * an edge one. Truncating would mean every ore rarer than about one per thousand never appears
     * at all, however deep or large the dungeon, and the ones that did appear would come in
     * suspiciously round numbers. So the fraction is the <em>probability of one more</em>: 0.4
     * diamonds is a 40% chance of a diamond. Over many dungeons the average is exactly the estimate,
     * which is the property the whole feature is built on.</p>
     */
    private static MiningHaul haulFor(DungeonLayout layout, MiningConfig config, RandomSource random) {
        List<ExcavationLedger.Excavation> excavations = ExcavationLedger.of(layout);
        if (excavations.isEmpty()) {
            return new MiningHaul(List.of());
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Double> rates = new LinkedHashMap<>();
        for (MiningConfig.OreBand band : config.ores()) {
            double blocks = 0.0D;
            for (ExcavationLedger.Excavation excavation : excavations) {
                double fraction = band.overlapFraction(excavation.baseY(), excavation.height());
                if (fraction > 0.0D) {
                    blocks += excavation.volume() * fraction;
                }
            }
            double expected = blocks / 1000.0D * band.perThousand() * config.payoutFraction();
            int rolled = Math.min(band.max(), roll(expected, random));
            if (rolled > 0) {
                counts.merge(band.item(), rolled, Integer::sum);
            }
            // Summed across the bands that name it, even the ones that rolled zero: rarity is a
            // property of the ore, not of what this particular dungeon happened to roll, and a
            // diamond that rolled 0 here must still sort ahead of coal in the next dungeon.
            rates.merge(band.item(), band.perThousand(), Double::sum);
        }

        // Rarest first -- see MiningHaul#itemsSnbt. A chest holds 27 slots and a large deep dungeon
        // can excavate enough coal to fill them; sorting by the authored rate rather than by the
        // rolled count means the diamonds are in slot 0 whether there is one of them or none.
        List<MiningHaul.Stack> stacks = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Integer> e) ->
                        rates.getOrDefault(e.getKey(), Double.MAX_VALUE)).thenComparing(Map.Entry::getKey))
                .forEach(e -> stacks.add(new MiningHaul.Stack(e.getKey(), e.getValue())));
        return new MiningHaul(stacks);
    }

    /** {@code floor(expected)}, plus one more with probability equal to the remainder. */
    private static int roll(double expected, RandomSource random) {
        if (expected <= 0.0D) {
            return 0;
        }
        int whole = (int) Math.floor(expected);
        return random.nextDouble() < expected - whole ? whole + 1 : whole;
    }

    /** A room the emitter will actually build, and the floor it is on. */
    private record RoomSlot(int floorIndex, int roomId) {}

    /**
     * Draws the room, weighted toward the bottom of the dungeon.
     *
     * <h2>Depth first, then a room on it</h2>
     * <p>The floor is drawn with weight {@code (floorIndex + 1) ^ depthBias} and the room uniformly
     * from that floor. Weighting the rooms directly instead would let a floor with many rooms
     * outvote a deeper floor with few &mdash; and floor size is a planner accident, not a statement
     * about depth. Mark's requirement is about <em>levels</em>, so levels are what is weighted.</p>
     *
     * <p>Only rooms {@code DungeonPieceEmitter} builds procedurally are eligible: START and END
     * belong to the entrance and transition templates, and a room that drew a Phase 8 prefab is
     * assembled from authored .nbt rather than generated, so neither can be told to add a chest.</p>
     */
    private static Optional<RoomSlot> pickRoom(DungeonLayout layout, MiningConfig config,
                                               RandomSource random) {
        List<List<RoomSlot>> byFloor = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0.0D;

        for (FloorLayout floor : layout.getFloors()) {
            List<RoomSlot> eligible = new ArrayList<>();
            for (RoomData room : floor.getRooms()) {
                if (room.getRole().isProcedurallyBuilt() && room.getTemplateId() == null) {
                    eligible.add(new RoomSlot(floor.getFloorIndex(), room.getId()));
                }
            }
            if (eligible.isEmpty()) {
                continue;
            }
            double weight = Math.pow(floor.getFloorIndex() + 1.0D, config.depthBias());
            byFloor.add(eligible);
            weights.add(weight);
            totalWeight += weight;
        }
        if (byFloor.isEmpty() || totalWeight <= 0.0D) {
            return Optional.empty();
        }

        double target = random.nextDouble() * totalWeight;
        for (int i = 0; i < byFloor.size(); i++) {
            target -= weights.get(i);
            if (target < 0.0D) {
                List<RoomSlot> rooms = byFloor.get(i);
                return Optional.of(rooms.get(random.nextInt(rooms.size())));
            }
        }
        // Floating-point residue only; the loop above consumes the whole weight in exact arithmetic.
        List<RoomSlot> rooms = byFloor.get(byFloor.size() - 1);
        return Optional.of(rooms.get(random.nextInt(rooms.size())));
    }
}
