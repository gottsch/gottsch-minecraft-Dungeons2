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
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A mini-boss tears through cobwebs instead of being caught in them.
 *
 * <p>A web holds whatever walks into it to a crawl, and the weathering hangs them in corners and
 * across corridors. For an ordinary monster that is atmosphere; a boss chasing a player down a
 * webbed hallway stopped dead in it, which reads as the dungeon fighting on the player's side. So
 * each tick, before the boss moves, any web touching it - or just about to - is torn down. Before
 * the move, not after: vanilla slows a mob from inside {@code move}, so a web still there when it
 * moves has already caught it for that tick.</p>
 *
 * <p>Not gated on {@code mobGriefing}: a web is the dungeon's dressing, not a player's build, and a
 * boss held in place by one is a fight that does not happen.</p>
 *
 * @author Mark Gottschling on 10/25/2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BossWebTearEvent {

    /** How far past its own box a boss reaches for a web: the next step it is about to take. */
    private static final double REACH = 0.3D;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !mob.isAlive()
                || !MiniBossAnchorEvent.isMiniBoss(mob)) {
            return;
        }
        Level level = mob.level();
        var box = mob.getBoundingBox().inflate(REACH, 0.0D, REACH);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getBlockState(pos).getBlock() instanceof WebBlock) {
                level.destroyBlock(pos, false, mob);
            }
        }
    }
}
