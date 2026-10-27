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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pulls the fog in while the camera stands in a {@code #dungeons2:dark_fog/*} biome (#129 step 3).
 * The arithmetic is {@link DungeonFog}'s.
 *
 * <p>The tier tags are datapack data, so a pack opts its own biome in by adding it to
 * {@code dark_fog/shallow}, {@code middle} or {@code deep}. Biome tags reach the client with the
 * registry sync &mdash; vanilla's own swamp water fog reads one here.
 *
 * <p>Left to vanilla: fluids (water, lava and powder snow have their own fog), blindness and darkness
 * (which must not be weakened), and spectators.
 *
 * <p>Forge applies {@code RenderFog}'s distances only when the event is CANCELLED
 * ({@code ForgeHooksClient#onFogRender}); setting them alone does nothing.
 *
 * @author Mark Gottschling on 10/26/2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DungeonFogEvents {

    public static final TagKey<Biome> SHALLOW = tag("dark_fog/shallow");
    public static final TagKey<Biome> MIDDLE = tag("dark_fog/middle");
    public static final TagKey<Biome> DEEP = tag("dark_fog/deep");
    public static final TagKey<Biome> HAZE = tag("dark_fog/haze");

    private static final DungeonFog FOG = new DungeonFog();
    private static long lastNanos;

    private DungeonFogEvents() {}

    private static TagKey<Biome> tag(String path) {
        return TagKey.create(Registries.BIOME, new ResourceLocation(Dungeons.MOD_ID, path));
    }

    /**
     * Fired once per frame before the fog distances, so the state is advanced here and read by
     * {@link #onRenderFog}.
     */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0f : Math.min(1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        boolean applies = applies(camera);
        Holder<Biome> biome = applies ? biomeAt(camera) : null;
        FOG.update(biome == null ? null : tierOf(biome), seconds,
                biome == null ? 0 : biome.value().getFogColor());
        if (!applies || !FOG.active()) {
            return;
        }
        float[] colour = FOG.colour(event.getRed(), event.getGreen(), event.getBlue());
        event.setRed(colour[0]);
        event.setGreen(colour[1]);
        event.setBlue(colour[2]);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || !FOG.active() || !applies(event.getCamera())) {
            return;
        }
        float far = FOG.far(event.getFarPlaneDistance());
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(FOG.near(event.getNearPlaneDistance(), far));
        event.setCanceled(true);
    }

    private static boolean applies(Camera camera) {
        if (camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }
        Entity entity = camera.getEntity();
        if (entity instanceof Player player && player.isSpectator()) {
            return false;
        }
        return !(entity instanceof LivingEntity living)
                || !(living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private static Holder<Biome> biomeAt(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.getBiome(camera.getBlockPosition());
    }

    /** The tier a biome is tagged with (a haze first, then the deepest dark), or null. */
    static DungeonFog.Tier tierOf(Holder<Biome> biome) {
        if (biome.is(HAZE)) {
            return DungeonFog.Tier.HAZE;
        }
        if (biome.is(DEEP)) {
            return DungeonFog.Tier.DEEP;
        }
        if (biome.is(MIDDLE)) {
            return DungeonFog.Tier.MIDDLE;
        }
        return biome.is(SHALLOW) ? DungeonFog.Tier.SHALLOW : null;
    }
}
