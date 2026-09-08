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
package mod.gottsch.forge.dungeons2.core.world;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.world.structure.PoolElementIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finding the boss room a position is standing in.
 *
 * <p>The room is identified from the <strong>structure</strong> rather than from anything written
 * into the world, so it works before the boss has spawned, after the chunk has been unloaded and
 * reloaded, and in a world generated before this class existed. Nothing has to be recorded at
 * generation time for a boss room to be recognised.</p>
 *
 * <p>A piece counts as a boss room when its pool element's template lives under
 * {@link #END_ROOMS_PATH}. That is the same fact the end-room pools are built from, read through
 * {@link PoolElementIds} &mdash; the element's own codec &mdash; rather than by reflection, for the
 * reason #44 settled: reflection into vanilla's pool elements breaks silently on a Forge update
 * while the codec is a supported surface.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public final class BossRooms {

    /** The structure this mod generates. */
    private static final ResourceLocation DUNGEON = new ResourceLocation(Dungeons.MOD_ID, "dungeon");

    /**
     * The path fragment that marks a template as a terminal/boss room.
     *
     * <p>Deliberately the folder rather than a list of template names: a boss room added to the
     * pool tomorrow is protected without touching this file, and a room moved out of the folder
     * stops being protected, which is the same statement the pools already make.</p>
     */
    private static final String END_ROOMS_PATH = "end_rooms/";

    private BossRooms() {}

    /**
     * The boss room containing {@code pos}, or empty.
     *
     * <p>Costs one structure lookup plus a walk of that dungeon's pieces, so callers should ask
     * only when they already have reason to (a block being broken, a boss dying) rather than per
     * tick.</p>
     */
    public static Optional<BoundingBox> at(ServerLevel level, BlockPos pos) {
        Structure structure = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(DUNGEON);
        if (structure == null) {
            return Optional.empty();
        }
        StructureStart start = level.structureManager().getStructureAt(pos, structure);
        if (!start.isValid()) {
            return Optional.empty();
        }
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            if (!piece.getBoundingBox().isInside(pos)) {
                continue;
            }
            boolean isBossRoom = PoolElementIds.locationOf(pool.getElement())
                    .filter(location -> location.contains(END_ROOMS_PATH))
                    .isPresent();
            if (isBossRoom) {
                return Optional.of(piece.getBoundingBox());
            }
        }
        return Optional.empty();
    }

    /**
     * Every boss room in the dungeon at {@code pos}, whether cleared or not.
     *
     * <p>For the callers that have to judge <em>many</em> positions at once &mdash; an explosion's
     * blast list, a piston's push list. Doing {@link #at} per position would repeat the structure
     * lookup hundreds of times for one detonation; this pays for it once and leaves the callers a
     * handful of cheap {@link BoundingBox#isInside} tests.</p>
     *
     * <p><strong>Anchored at one position by design.</strong> A blast or a piston that reaches a
     * boss room from outside the dungeon's own bounding box is not covered, and that is an accepted
     * limit rather than an oversight: the box encloses the rock between the rooms as well as the
     * rooms, a boss room sits well inside it, and nothing can damage a boss room wall from further
     * away than a blast radius. To exploit the gap a player would have to detonate outside the
     * dungeon entirely and still reach its deepest room.</p>
     */
    public static List<BoundingBox> allAt(ServerLevel level, BlockPos pos) {
        Structure structure = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(DUNGEON);
        if (structure == null) {
            return List.of();
        }
        StructureStart start = level.structureManager().getStructureAt(pos, structure);
        if (!start.isValid()) {
            return List.of();
        }
        List<BoundingBox> rooms = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece pool
                    && PoolElementIds.locationOf(pool.getElement())
                            .filter(location -> location.contains(END_ROOMS_PATH))
                            .isPresent()) {
                rooms.add(piece.getBoundingBox());
            }
        }
        return rooms;
    }

    /**
     * The stable identity of a boss room: its box's minimum corner, packed.
     *
     * <p>The minimum corner rather than the centre because it is exact integer arithmetic on a box
     * that never moves, and because two rooms cannot share one.</p>
     */
    public static long keyOf(BoundingBox box) {
        return BlockPos.asLong(box.minX(), box.minY(), box.minZ());
    }
}
