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

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Holds and registers the {@link StructurePieceType}s for the Phase 3 dungeon
 * pieces.
 *
 * <p>The type objects are plain lambdas created at class-load time, so they are
 * usable as a piece's {@code type} argument even before {@link #register()} runs
 * (handy for unit tests, which never run the mod lifecycle). {@link #register()}
 * only inserts them into {@link BuiltInRegistries#STRUCTURE_PIECE} so the vanilla
 * save/load dispatcher can resolve each piece by id.</p>
 *
 * <p>The {@code STRUCTURE_PIECE} registry is frozen after bootstrap, so
 * {@link #register()} must run inside the Forge common-setup work queue (which
 * unfreezes the vanilla registries) &mdash; see {@code CommonSetup}.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public final class StructurePieces {

    private StructurePieces() {}

    public static final StructurePieceType ROOM =
            (context, tag) -> new DungeonRoomPiece(context, tag);
    public static final StructurePieceType CORRIDOR =
            (context, tag) -> new DungeonCorridorPiece(context, tag);
    public static final StructurePieceType DOOR =
            (context, tag) -> new DungeonDoorPiece(context, tag);

    /** Registers all piece types. Call from common setup's enqueued work. */
    public static void register() {
        register("dungeon_room", ROOM);
        register("dungeon_corridor", CORRIDOR);
        register("dungeon_door", DOOR);
        Dungeons.LOGGER.debug("registered dungeon structure piece types");
    }

    private static void register(String name, StructurePieceType type) {
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, new ResourceLocation(Dungeons.MOD_ID, name), type);
    }
}
