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
import mod.gottsch.forge.gmm.core.client.model.AlligatorGarModel;
import mod.gottsch.forge.gmm.core.client.model.BlackPuddingModel;
import mod.gottsch.forge.gmm.core.client.model.BloaterArmModel;
import mod.gottsch.forge.gmm.core.client.model.BloaterZombieModel;
import mod.gottsch.forge.gmm.core.client.model.BodakModel;
import mod.gottsch.forge.gmm.core.client.model.BoneShardModel;
import mod.gottsch.forge.gmm.core.client.model.ElectricSkeletonModel;
import mod.gottsch.forge.gmm.core.client.model.GhoulModel;
import mod.gottsch.forge.gmm.core.client.model.IronSkeletonModel;
import mod.gottsch.forge.gmm.core.client.model.MagmaSkeletonModel;
import mod.gottsch.forge.gmm.core.client.model.MargoyleModel;
import mod.gottsch.forge.gmm.core.client.model.OrcModel;
import mod.gottsch.forge.gmm.core.client.model.OrcShamanModel;
import mod.gottsch.forge.gmm.core.client.model.SkeletonChampionModel;
import mod.gottsch.forge.gmm.core.client.model.SkeletonWarriorModel;
import mod.gottsch.forge.gmm.core.client.model.TaintedSkeletonModel;
import mod.gottsch.forge.gmm.core.client.model.WingedSkeletonModel;
import mod.gottsch.forge.gmm.core.client.renderer.entity.AcidSkeletonRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.AlligatorGarRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.AnimatedArmorRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.BlackPuddingRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.BloaterArmRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.BloaterRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.BloodyBonesRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.BodakRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.BoneShardRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.BurningSkeletonRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.ElectricSkeletonRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.GelatinousCubeRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.GhoulRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.GraveZombieRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.GrayOozeRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.IronSkeletonRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.MagmaSkeletonRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.MargoyleRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.OchreJellyRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.OrcRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.OrcShamanRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.SkeletonChampionRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.SkeletonWarriorRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.SpikeGrowthSpellRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.TaintedSkeletonRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.WightRenderer;
import mod.gottsch.forge.gmm.core.client.renderer.entity.WingedSkeletonRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
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
        // The GMM roster (2026-08-31). Only the mobs with their OWN rig appear here: the acid,
        // burning and bloody-bones skeletons reuse SkeletonWarriorModel's layer, the grave zombie
        // and wight reuse vanilla's ModelLayers.ZOMBIE, the animated armor reuses the zombie rig
        // plus vanilla's armour layers, and three of the four oozes reuse vanilla's SLIME. A layer
        // registered twice would be harmless (see above); a renderer whose layer is registered
        // NOWHERE crashes on first sight of the mob, which is the failure worth being careful about.
        event.registerLayerDefinition(SkeletonWarriorModel.LAYER_LOCATION, SkeletonWarriorModel::createBodyLayer);
        event.registerLayerDefinition(WingedSkeletonModel.LAYER_LOCATION, WingedSkeletonModel::createBodyLayer);
        event.registerLayerDefinition(IronSkeletonModel.LAYER_LOCATION, IronSkeletonModel::createBodyLayer);
        event.registerLayerDefinition(MagmaSkeletonModel.LAYER_LOCATION, MagmaSkeletonModel::createBodyLayer);
        event.registerLayerDefinition(TaintedSkeletonModel.LAYER_LOCATION, TaintedSkeletonModel::createBodyLayer);
        event.registerLayerDefinition(ElectricSkeletonModel.LAYER_LOCATION, ElectricSkeletonModel::createBodyLayer);
        event.registerLayerDefinition(SkeletonChampionModel.LAYER_LOCATION, SkeletonChampionModel::createBodyLayer);
        event.registerLayerDefinition(BloaterZombieModel.LAYER_LOCATION, BloaterZombieModel::createBodyLayer);
        event.registerLayerDefinition(BodakModel.LAYER_LOCATION, BodakModel::createBodyLayer);
        event.registerLayerDefinition(GhoulModel.LAYER_LOCATION, GhoulModel::createBodyLayer);
        event.registerLayerDefinition(BlackPuddingModel.LAYER_LOCATION, BlackPuddingModel::createBodyLayer);
        event.registerLayerDefinition(MargoyleModel.LAYER_LOCATION, MargoyleModel::createBodyLayer);
        event.registerLayerDefinition(OrcModel.LAYER_LOCATION, OrcModel::createBodyLayer);
        event.registerLayerDefinition(OrcShamanModel.LAYER_LOCATION, OrcShamanModel::createBodyLayer);
        event.registerLayerDefinition(AlligatorGarModel.LAYER_LOCATION, AlligatorGarModel::createBodyLayer);

        // The projectile rigs. BoneShardModel is a set of variants -- a shard is a randomly
        // chosen bone shape -- so every one of its layers has to be registered, not just the first.
        for (int v = 0; v < BoneShardModel.LAYERS.length; v++) {
            final int variant = v;
            event.registerLayerDefinition(BoneShardModel.LAYERS[variant],
                    () -> BoneShardModel.createBodyLayer(variant));
        }
        event.registerLayerDefinition(BloaterArmModel.LAYER_LOCATION, BloaterArmModel::createBodyLayer);
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
        // The GMM roster.
        event.registerEntityRenderer(DungeonsEntities.SKELETON_WARRIOR_ENTITY.get(), SkeletonWarriorRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.WINGED_SKELETON_ENTITY.get(), WingedSkeletonRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.IRON_SKELETON_ENTITY.get(), IronSkeletonRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.MAGMA_SKELETON_ENTITY.get(), MagmaSkeletonRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.TAINTED_SKELETON_ENTITY.get(), TaintedSkeletonRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.ACID_SKELETON_ENTITY.get(), AcidSkeletonRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.ELECTRIC_SKELETON_ENTITY.get(), ElectricSkeletonRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.BURNING_SKELETON_ENTITY.get(), BurningSkeletonRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.BLOODY_BONES_ENTITY.get(), BloodyBonesRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.SKELETON_CHAMPION_ENTITY.get(), SkeletonChampionRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.BLOATER_ENTITY.get(), BloaterRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.GRAVE_ZOMBIE_ENTITY.get(), GraveZombieRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.WIGHT_ENTITY.get(), WightRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.BODAK_ENTITY.get(), BodakRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.GHOUL_ENTITY.get(), GhoulRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.GELATINOUS_CUBE_ENTITY.get(), GelatinousCubeRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.OCHRE_JELLY_ENTITY.get(), OchreJellyRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.GRAY_OOZE_ENTITY.get(), GrayOozeRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.BLACK_PUDDING_ENTITY.get(), BlackPuddingRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.ANIMATED_ARMOR_ENTITY.get(), AnimatedArmorRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.MARGOYLE_ENTITY.get(), MargoyleRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.ORC_ENTITY.get(), OrcRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.ORC_SHAMAN_ENTITY.get(), OrcShamanRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.ALLIGATOR_GAR_ENTITY.get(), AlligatorGarRenderer::new);

        // The projectiles. The rock and the withering gaze are rendered as a spinning ITEM --
        // GMM's own choice, and the reason each has an `itemSupplier` hook; the second argument
        // is the render scale of that item.
        event.registerEntityRenderer(DungeonsEntities.ROCK_ENTITY.get(),
                provider -> new ThrownItemRenderer<>(provider, 0.5F, true));
        event.registerEntityRenderer(DungeonsEntities.WITHERING_GAZE_SPELL_ENTITY.get(),
                provider -> new ThrownItemRenderer<>(provider, 0.6F, true));
        event.registerEntityRenderer(DungeonsEntities.BONE_SHARD_ENTITY.get(), BoneShardRenderer::new);
        event.registerEntityRenderer(DungeonsEntities.BLOATER_ARM_ENTITY.get(), BloaterArmRenderer::new);
        // Spike Growth draws real BlockStates rather than a mesh, so it has its own renderer.
        event.registerEntityRenderer(DungeonsEntities.SPIKE_GROWTH_SPELL_ENTITY.get(),
                SpikeGrowthSpellRenderer::new);
    }

    private ClientSetup() {}
}
