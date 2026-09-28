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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mod.gottsch.forge.dungeons2.core.config.TombConfig;
import mod.gottsch.forge.dungeons2.core.config.TombContents;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.event.EchelonSpawnEvent;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Places a room scheme's tombs (#104): sealed {@code dungeonblocks} sarcophagi, each holding a loot
 * table and a guardian for its first opening to choose between.
 *
 * <h2>Where a tomb stands</h2>
 * <p>Like a bed: its HEAD in a wall-adjacent cell, backing onto the wall, and its FOOT one cell
 * further into the room. The head cells are the pots' and chests' candidates
 * ({@code RoomPropGenerator#eligibleCells}), so a head is never in a doorway's approach; the foot
 * is checked against the same rules, because a foot across a doorway blocks it just as well. The
 * tomb's {@code facing} points from foot to head &mdash; into the wall &mdash; which is
 * {@code dungeonblocks}' convention for the pair.</p>
 *
 * <p><strong>No two tombs share an edge.</strong> Each keeps the cells around it clear of other
 * tombs, so every lid can be reached and a guardian that finds no headroom on its tomb has somewhere
 * beside it to stand. Whether a cell can take a tomb therefore depends on the tombs already placed,
 * so a rejected cell is replaced from the rest of the draw ({@link CellDraw#nextUndrawn}) rather than
 * costing the room a tomb.</p>
 *
 * <h2>Both halves are block-entity placements</h2>
 * <p>Both halves carry a block entity, and both are emitted with data so they skip the processor
 * pass and are written last, where {@code hollow()}'s air cannot land on top of them &mdash; the
 * write order {@code DungeonPiece#placeAll} documents. Every half states {@code part},
 * {@code facing} and {@code open=false} outright: a block's default state is not something to
 * trust with a boolean, and a tomb generated open would be a tomb whose first opening never
 * happens.</p>
 *
 * <p>The contents ride on the HEAD, which is the half DungeonBlocks reads first. Both halves carry
 * the tomb's origin in Forge's persistent data, the same compound a vanilla cage carries, so
 * {@code EchelonSpawnEvent} can scale the guardian by this floor's depth &mdash; a mob raised from a
 * tomb has no spawner to say where it came from.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
public final class RoomTombGenerator {

    /**
     * {@code dungeonblocks}' block-entity type, for {@code DungeonPiece#applyBlockEntity}'s tag &mdash;
     * and what {@code DungeonPiece#probeTomb} recognises a tomb half by.
     */
    public static final String SARCOPHAGUS_ENTITY = "dungeonblocks:sarcophagus";
    public static final String FACING = "facing";
    public static final String PART = "part";
    static final String OPEN = "open";
    public static final String HEAD = "head";
    static final String FOOT = "foot";
    /** Forge's persistent-data key on any block entity; see {@link EchelonSpawnEvent}. */
    static final String FORGE_DATA = "ForgeData";

    private RoomTombGenerator() {}

    /**
     * Emits this room's tombs, returning the cells they took &mdash; both halves of each.
     *
     * @param config   the slot, ALREADY resolved against the floor's tomb band
     * @param motif    the motif's value for the echelon stamp; null leaves the tombs unstamped
     * @param occupied cells already claimed; tombs avoid them and hand back their own
     */
    public static Set<Coords2D> placeTombs(RoomData room, int floorY, int floorIndex, String motif,
                                           TombConfig config, Set<Coords2D> occupied,
                                           RandomSource random, List<BlockPlacement> out) {
        List<TombConfig.TombVariant> variants = config.variants();
        int totalWeight = variants.stream().mapToInt(TombConfig.TombVariant::weight).sum();
        // No tomb at all rather than an inert one -- see TombContents#releasesAnything. Checked
        // before anything draws, so a slot that can place nothing leaves the room's stream alone.
        if (variants.isEmpty() || totalWeight <= 0 || !config.contents().releasesAnything()) {
            return Set.of();
        }
        List<Coords2D> heads = headCells(room, occupied);
        if (heads.isEmpty()) {
            return Set.of();
        }

        String origin = originSnbt(motif, floorIndex);
        // floorY + 1: resting on the floor surface, the row the chests and pots use.
        int y = floorY + 1;
        CellDraw draw = CellDraw.of(heads, config.minCount(), config.clampedMaxCount(), random);
        Set<Coords2D> used = new LinkedHashSet<>();
        Set<Coords2D> keepClear = new HashSet<>();
        int placed = 0;
        while (placed < draw.count() && draw.hasUndrawn()) {
            Coords2D head = draw.nextUndrawn();
            Coords2D foot = footOf(room, head);
            if (keepClear.contains(head) || keepClear.contains(foot)) {
                continue;
            }
            String facing = towardWall(room, head);
            String block = pickVariant(variants, totalWeight, random);
            TombContents.Drawn contents = config.contents().draw(random);

            // No probe here: this plans in floor-local coordinates and re-plans on every chunk
            // pass, so a line from here names no findable position and repeats. DungeonPiece
            // logs [D2-TOMB] as it writes the head -- in world coordinates, once.
            out.add(half(head, y, block, facing, HEAD, headData(contents, origin)));
            out.add(half(foot, y, block, facing, FOOT, footData(origin)));
            used.add(head);
            used.add(foot);
            keepClear.addAll(withNeighbours(head));
            keepClear.addAll(withNeighbours(foot));
            placed++;
        }
        return used;
    }

    /**
     * The wall-adjacent cells a tomb's head may take: the pots' candidates, minus any whose foot
     * would land outside the interior, in a claimed cell, or in a doorway's approach.
     */
    static List<Coords2D> headCells(RoomData room, Set<Coords2D> occupied) {
        Set<Coords2D> doorways = RoomInterior.cellsInsideDoorways(room);
        List<Coords2D> heads = new ArrayList<>();
        for (Coords2D head : RoomPropGenerator.eligibleCells(room, occupied)) {
            Coords2D foot = footOf(room, head);
            if (isInterior(room, foot) && !occupied.contains(foot) && !doorways.contains(foot)) {
                heads.add(head);
            }
        }
        return heads;
    }

    /** One step from the head into the room: away from the wall it backs onto. */
    static Coords2D footOf(RoomData room, Coords2D head) {
        return switch (RoomChestGenerator.facingAwayFromWall(room, head)) {
            case "south" -> new Coords2D(head.getX(), head.getY() + 1);
            case "north" -> new Coords2D(head.getX(), head.getY() - 1);
            case "east" -> new Coords2D(head.getX() + 1, head.getY());
            default -> new Coords2D(head.getX() - 1, head.getY());
        };
    }

    /** The tomb's {@code facing}, foot to head: toward the wall the head backs onto. */
    static String towardWall(RoomData room, Coords2D head) {
        return switch (RoomChestGenerator.facingAwayFromWall(room, head)) {
            case "south" -> "north";
            case "north" -> "south";
            case "east" -> "west";
            default -> "east";
        };
    }

    private static boolean isInterior(RoomData room, Coords2D cell) {
        return cell.getX() >= room.getOriginX() + 1
                && cell.getX() <= room.getOriginX() + room.getWidth() - 2
                && cell.getY() >= room.getOriginZ() + 1
                && cell.getY() <= room.getOriginZ() + room.getDepth() - 2;
    }

    private static List<Coords2D> withNeighbours(Coords2D cell) {
        return List.of(cell,
                new Coords2D(cell.getX() + 1, cell.getY()), new Coords2D(cell.getX() - 1, cell.getY()),
                new Coords2D(cell.getX(), cell.getY() + 1), new Coords2D(cell.getX(), cell.getY() - 1));
    }

    private static BlockPlacement half(Coords2D cell, int y, String block, String facing,
                                       String part, BlockEntityData data) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(FACING, facing);
        properties.put(PART, part);
        properties.put(OPEN, "false");
        BlockPlacement placement = new BlockPlacement(cell.getX(), y, cell.getY(), block, properties);
        placement.setBlockEntityNbt(data);
        return placement;
    }

    /** The head's data: the contents, stringified, plus the origin. */
    static BlockEntityData headData(TombContents.Drawn contents, String origin) {
        BlockEntityData data = new BlockEntityData(SARCOPHAGUS_ENTITY);
        contents.fields().forEach((key, value) -> data.with(key, String.valueOf(value)));
        if (origin != null) {
            data.withNbt(FORGE_DATA, origin);
        }
        return data;
    }

    /**
     * The foot's data: the origin alone. Data even when there is no origin, because data is what
     * routes a placement round the processor pass to be written last with its partner.
     */
    static BlockEntityData footData(String origin) {
        BlockEntityData data = new BlockEntityData(SARCOPHAGUS_ENTITY);
        if (origin != null) {
            data.withNbt(FORGE_DATA, origin);
        }
        return data;
    }

    /** The {@code ForgeData} compound as SNBT, or null when there is no depth to stamp. */
    static String originSnbt(String motif, int floorIndex) {
        if (motif == null || motif.isBlank() || floorIndex < 0) {
            return null;
        }
        CompoundTag forgeData = new CompoundTag();
        forgeData.put(EchelonSpawnEvent.ORIGIN, EchelonSpawnEvent.originTag(motif, floorIndex));
        return forgeData.toString();
    }

    /** Weighted draw over the declared variants. Mirrors {@code RoomChestGenerator#pickVariant}. */
    private static String pickVariant(List<TombConfig.TombVariant> variants, int totalWeight,
                                      RandomSource random) {
        int roll = random.nextInt(totalWeight);
        for (TombConfig.TombVariant variant : variants) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant.block();
            }
        }
        return variants.get(variants.size() - 1).block();
    }
}
