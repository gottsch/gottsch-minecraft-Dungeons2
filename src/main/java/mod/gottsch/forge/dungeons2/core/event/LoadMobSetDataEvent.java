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
import mod.gottsch.forge.gottschcore.mobset.MobSetDataHandler;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers GottschCore's {@code mob_sets} datapack reader.
 *
 * <p><strong>GottschCore does not register this itself, deliberately</strong> &mdash; its own
 * javadoc says "a consuming mod must register it with the {@code AddReloadListenerEvent}". Same
 * library-registers-nothing model as the Monster Manual, and as the mob-set spawner's block entity
 * type ({@code DungeonsBlockEntities}). Without this, {@code MobSetDataRegistry} stays empty and
 * every spawner resolves to nothing at all, with no error.</p>
 *
 * <p>Files live at {@code data/<any namespace>/mob_sets/*.json}, so a datapack can add or replace
 * Dungeons2's sets without touching the mod.</p>
 *
 * <p>The {@code @Mod.EventBusSubscriber} annotation is what force-loads this class &mdash; the one
 * kind of class Forge loads without a reference, which is exactly the mechanism the registry
 * holders lacked.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LoadMobSetDataEvent {

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new MobSetDataHandler());
    }

    /**
     * Reports whether the shipped mob sets actually made it into {@code MobSetDataRegistry}.
     *
     * <p><strong>Why this needs saying out loud.</strong> An empty registry is not a quiet
     * degradation: {@code ProximityMobSetSpawnerBlockEntity.execute} calls {@code selfDestruct()}
     * <em>outside</em> the {@code ifPresent}, so a spawner whose set does not resolve spawns
     * nothing and then <strong>deletes itself and its block entity</strong>. The observable result
     * is an empty room and a cell that reports "not a Block Entity" &mdash; indistinguishable from
     * the spawner never having been placed. GottschCore carries a TODO acknowledging the case.</p>
     *
     * <p>Fired on {@code ServerStartedEvent} because datapack reload has completed by then;
     * {@link #onAddReloadListener} is far too early to ask.</p>
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ResourceLocation shipped = new ResourceLocation(Dungeons.MOD_ID, "classic_vermin");
        boolean present = MobSetDataRegistry.get(shipped).isPresent();
        Dungeons.LOGGER.info("[D2-MOBSET] {} present={}", shipped, present);
        if (!present) {
            Dungeons.LOGGER.error("[D2-MOBSET] {} did NOT load. Every spawner using it will spawn"
                    + " nothing and then self-destruct, leaving no trace to diagnose.", shipped);
        }
    }
}
