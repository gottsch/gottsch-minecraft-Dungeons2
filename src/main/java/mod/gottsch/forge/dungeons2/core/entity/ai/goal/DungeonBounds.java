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
package mod.gottsch.forge.dungeons2.core.entity.ai.goal;

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * "Is this position inside a Dungeons2 dungeon?"
 *
 * <p>The containment test behind {@code SmashBlocksGoal}'s leash. A mob that can take walls apart
 * is acceptable inside a generated dungeon, where the walls are this mod's own and a hole in one is
 * part of the fight. The same mob loose in the overworld is a griefing engine pointed at whatever
 * the player built &mdash; and it does not take a bug to get there, only a player who runs and a
 * Minotaur that follows.</p>
 *
 * <p>The test is the structure's <strong>bounding box</strong>, not its pieces. A dungeon's box
 * encloses the rock between its rooms as well as the rooms, which is what we want: the mob may
 * tunnel through the stone between two corridors, and may not touch anything past the last piece.
 * Using the pieces instead would forbid exactly the wall-breaching this goal exists to do.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public final class DungeonBounds {

    /** The one structure this mod generates; see {@code data/dungeons2/worldgen/structure/dungeon.json}. */
    private static final ResourceLocation DUNGEON = new ResourceLocation(Dungeons.MOD_ID, "dungeon");

    private DungeonBounds() {}

    /**
     * Whether {@code pos} lies inside a generated dungeon's bounding box.
     *
     * <p><strong>Fails closed.</strong> A client level, a missing structure registration, a
     * datapack that removed the structure &mdash; every one of them answers {@code false}, which
     * costs a Minotaur a wall it could have broken. The opposite default would spend the same
     * uncertainty on the player's base, and those two mistakes are not the same size.</p>
     */
    public static boolean contains(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Structure structure = serverLevel.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(DUNGEON);
        if (structure == null) {
            return false;
        }
        return serverLevel.structureManager().getStructureAt(pos, structure).isValid();
    }
}
