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
package mod.gottsch.neo.dungeons2.core.world.structure;

import mod.gottsch.neo.dungeons2.Dungeons;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Holds and registers the {@link StructurePieceType}s for the Phase 3 dungeon
 * pieces.
 *
 * <p>The type objects are plain lambdas created at class-load time, so they are
 * usable as a piece's {@code type} argument even before {@link #register(IEventBus)} runs
 * (handy for unit tests, which never run the mod lifecycle). Registration only
 * inserts them into {@link BuiltInRegistries#STRUCTURE_PIECE} so the vanilla
 * save/load dispatcher can resolve each piece by id.</p>
 *
 * <p>On Forge this was a {@code Registry.register} inside common setup's
 * {@code enqueueWork}, which unfroze the vanilla registries. NeoForge does not unfreeze
 * them there (the mod failed to load with "Registry is already frozen"), so the
 * existing type objects go through a {@link DeferredRegister} instead.</p>
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

    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Dungeons.MOD_ID);

    static {
        PIECES.register("dungeon_room", () -> ROOM);
        PIECES.register("dungeon_corridor", () -> CORRIDOR);
        PIECES.register("dungeon_door", () -> DOOR);
    }

    /** Registers all piece types. Call from {@code Registration.init}. */
    public static void register(IEventBus eventBus) {
        PIECES.register(eventBus);
    }
}
