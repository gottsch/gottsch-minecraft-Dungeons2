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
package mod.gottsch.forge.dungeons2.core.client;

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Rising smoke and drifting ash in a {@code #dungeons2:dark_fog/haze} biome (Molten Halls), spawned
 * around the player each client tick.
 *
 * <p>Code rather than the biome's {@code particle}, because a biome takes one particle type and
 * spawns it with no motion: ash then falls (vanilla's ash provider ignores velocity) and nothing
 * rises. Smoke here is given an upward drift. Only air in the haze biome itself gets a particle, so
 * none appear in the corridor outside.
 *
 * @author Mark Gottschling on 10/26/2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HazeParticles {

    /** How far round the player particles appear, in blocks. */
    private static final int RADIUS = 10;
    /** Tries per tick: about this many particles a tick in an open hall. */
    private static final int SMOKE_TRIES = 2;
    private static final int ASH_TRIES = 3;
    /** Dark grey flecks. */
    private static final DustParticleOptions ASH = new DustParticleOptions(new Vector3f(0.24f, 0.21f, 0.19f), 0.6f);

    private HazeParticles() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) {
            return;
        }
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || !level.getBiome(player.blockPosition()).is(DungeonFogEvents.HAZE)) {
            return;
        }
        RandomSource random = level.random;
        for (int i = 0; i < SMOKE_TRIES + ASH_TRIES; i++) {
            BlockPos pos = player.blockPosition().offset(
                    random.nextInt(RADIUS * 2 + 1) - RADIUS,
                    random.nextInt(9) - 3,
                    random.nextInt(RADIUS * 2 + 1) - RADIUS);
            if (!level.getBlockState(pos).isAir() || !level.getBiome(pos).is(DungeonFogEvents.HAZE)) {
                continue;
            }
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            if (i < SMOKE_TRIES) {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.02 + random.nextDouble() * 0.03, 0);
            } else {
                level.addParticle(ASH, x, y, z, 0, 0.02, 0);
            }
        }
    }
}
