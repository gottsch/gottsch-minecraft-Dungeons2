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
package mod.gottsch.forge.dungeons2.core.event;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.advancement.DungeonsTriggers;
import mod.gottsch.forge.dungeons2.core.world.DungeonFloorIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Once a second, tells {@code dungeons2:reached_floor} which dungeon level a player is standing on,
 * read from the saved floor index (the same one a blank Dungeon Map starts from).
 *
 * @author Mark Gottschling on 10/26/2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReachedFloorEvent {

    private static final int INTERVAL = 20;

    private ReachedFloorEvent() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % INTERVAL != 0 || !(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        DungeonFloorIndex.get(level).floorAt(player.blockPosition())
                .ifPresent(floor -> DungeonsTriggers.REACHED_FLOOR.trigger(player, floor.floorIndex()));
    }
}
