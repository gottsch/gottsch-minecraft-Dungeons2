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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A spiked moat dug out under the floor around the room's centre, with the floor left exactly as
 * it was laid: take the prize, lose the floor.
 *
 * <p>The centre cell is not dug. It stands on its own column in the middle of the void, and it is
 * where the secret room's pedestal goes &mdash; the pedestal takes the free interior cell nearest the
 * centre, and every moat cell is claimed. Nothing about the moat is visible and nothing a player or
 * mob does to the floor springs it: the ordinary floor is solid, and it holds up until the prize is
 * taken, when {@code PedestalTrapEvent} turns the ring into crumbling floor and sets it off.</p>
 *
 * <p>{@code radius} is how far the ring reaches from the centre (1 is the 8 cells touching it, 2 a
 * 5x5). It shrinks to keep a walkable ring round it, and to nothing at all if there is no room for a
 * centre and one ring, because a moat around nothing is a pit under a floor that never falls.</p>
 */
public class HiddenMoatPitShapeProvider implements IPitShapeProvider {

    private final int radius;
    private final int depth;
    private final String spikeBlock;
    private final Map<String, String> spikeProperties;
    private final double spikeProbability;

    public HiddenMoatPitShapeProvider(int radius, int depth, String spikeBlock,
                                      Map<String, String> spikeProperties, double spikeProbability) {
        this.radius = radius;
        this.depth = depth;
        this.spikeBlock = spikeBlock;
        this.spikeProperties = spikeProperties;
        this.spikeProbability = spikeProbability;
    }

    @Override
    public PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        // Centred, so the room's centre cell -- where the pedestal stands -- is the moat's centre.
        // An even interior has no centre cell, and then there is no moat.
        if (interiorWidth % 2 == 0 || interiorDepth % 2 == 0) {
            return PitPlan.empty();
        }
        int fit = Math.min(radius, (Math.min(interiorWidth, interiorDepth) - 3) / 2);
        if (fit < 1) {
            return PitPlan.empty();
        }
        int cx = interiorWidth / 2;
        int cz = interiorDepth / 2;

        Set<Coords2D> ring = new HashSet<>();
        for (int x = cx - fit; x <= cx + fit; x++) {
            for (int z = cz - fit; z <= cz + fit; z++) {
                if (x != cx || z != cz) {
                    ring.add(new Coords2D(x, z));
                }
            }
        }
        Map<Coords2D, BlockState> fills = new HashMap<>();
        if (spikeBlock != null && !spikeBlock.isEmpty() && spikeProbability > 0) {
            BlockState spike = BlockStateCodec.withProperties(
                    BlockStateCodec.block(spikeBlock, Blocks.POINTED_DRIPSTONE), spikeProperties);
            ring.stream()
                    .sorted((a, b) -> a.getX() != b.getX()
                            ? Integer.compare(a.getX(), b.getX())
                            : Integer.compare(a.getY(), b.getY()))
                    .forEach(cell -> {
                        if (random.nextDouble() < spikeProbability) {
                            fills.put(cell, spike);
                        }
                    });
        }
        return new PitPlan(PitPlans.sheer(ring, depth), fills, Map.of(), Map.of(), Map.of(),
                Map.of(), Set.of(), ring);
    }

    /** The trap's reach once fitted to a room: 0 when the room is too small for any moat. */
    public int fittedRadius(int interiorWidth, int interiorDepth) {
        if (interiorWidth % 2 == 0 || interiorDepth % 2 == 0) {
            return 0;
        }
        return Math.max(0, Math.min(radius, (Math.min(interiorWidth, interiorDepth) - 3) / 2));
    }
}
