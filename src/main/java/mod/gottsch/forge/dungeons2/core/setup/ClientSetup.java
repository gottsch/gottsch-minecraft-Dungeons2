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
package mod.gottsch.forge.dungeons2.core.setup;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import mod.gottsch.forge.gmm.core.client.model.RatModel;
import mod.gottsch.forge.gmm.core.client.model.ShriekerModel;
import mod.gottsch.forge.gmm.core.client.model.VioletFungusModel;
import mod.gottsch.forge.gmm.core.client.renderer.entity.RatRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.ShriekerRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.VioletFungusRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side registration for the dungeon's mobs (backlog #40 / #41).
 *
 * <p>This is the mod's first client setup class; there was nothing to render before.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    /**
     * Registers GMM's <em>own</em> layer location, which is what every GMM consumer does.
     *
     * <p><strong>This is safe when another GMM consumer is installed alongside us</strong> (Dungeon
     * Denizens and Village Dungeons both register the same one). Verified rather than assumed:
     * Forge's {@code ForgeHooksClient.registerLayerDefinition} is a plain {@code HashMap.put}, so a
     * second registration of the same {@link net.minecraft.client.model.geom.ModelLayerLocation}
     * silently overwrites an identical value, and {@code loadLayerDefinitions} only copies that map
     * into the immutable builder afterwards &mdash; the duplicate never reaches it.</p>
     *
     * <p>One registration serves both rats: they share {@link RatModel}, and the giant is a render
     * scale rather than a different model.</p>
     */
    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RatModel.LAYER_LOCATION, RatModel::createBodyLayer);
        // The fungi do NOT share a layer, despite sharing a look today: VioletFungusModel
        // delegates to the shrieker's rig for now but owns its own LAYER_LOCATION, so it gets its
        // own registration and keeps working when GMM gives it the tentacle geometry.
        event.registerLayerDefinition(ShriekerModel.LAYER_LOCATION, ShriekerModel::createBodyLayer);
        event.registerLayerDefinition(VioletFungusModel.LAYER_LOCATION,
                VioletFungusModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DungeonsEntities.RAT_ENTITY.get(), RatRenderer::new);
        // GMM's renderer takes a scale (and a texture) precisely so a variant needs no new model.
        // Keep this in step with GIANT_RAT_ENTITY's sized(...) -- they are independent numbers.
        event.registerEntityRenderer(DungeonsEntities.GIANT_RAT_ENTITY.get(),
                context -> new RatRenderer<>(context, 2.0F));
        // Unlike the rats these take no scale -- their EntityType box is the size they render at.
        event.registerEntityRenderer(DungeonsEntities.SHRIEKER_ENTITY.get(), ShriekerRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.VIOLET_FUNGUS_ENTITY.get(),
                VioletFungusRenderer::new);
    }

    private ClientSetup() {}
}
