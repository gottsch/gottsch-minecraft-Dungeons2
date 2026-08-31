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
package mod.gottsch.forge.dungeons2.core.setup;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.api.DungeonsApi;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import mod.gottsch.forge.dungeons2.core.item.DungeonsItems;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.world.structure.StructurePieces;
import mod.gottsch.forge.gmm.core.entity.monster.AlligatorGar;
import mod.gottsch.forge.gmm.core.entity.monster.BlackPudding;
import mod.gottsch.forge.gmm.core.entity.monster.GelatinousCube;
import mod.gottsch.forge.gmm.core.entity.monster.GrayOoze;
import mod.gottsch.forge.gmm.core.entity.monster.OchreJelly;
import mod.gottsch.forge.gmm.core.entity.monster.Orc;
import mod.gottsch.forge.gmm.core.entity.monster.OrcShaman;
import mod.gottsch.forge.gmm.core.entity.monster.construct.AnimatedArmor;
import mod.gottsch.forge.gmm.core.entity.monster.gargoyle.Margoyle;
import mod.gottsch.forge.gmm.core.entity.monster.ghoul.Ghoul;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.AcidSkeleton;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.BloodyBones;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.BurningSkeleton;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.ElectricSkeleton;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.IronSkeleton;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.MagmaSkeleton;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.SkeletonChampion;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.SkeletonWarrior;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.TaintedSkeleton;
import mod.gottsch.forge.gmm.core.entity.monster.skeleton.WingedSkeleton;
import mod.gottsch.forge.gmm.core.entity.monster.zombie.Bloater;
import mod.gottsch.forge.gmm.core.entity.monster.zombie.Bodak;
import mod.gottsch.forge.gmm.core.entity.monster.zombie.GraveZombie;
import mod.gottsch.forge.gmm.core.entity.monster.zombie.Wight;
import mod.gottsch.forge.gmm.core.entity.projectile.BloaterArm;
import mod.gottsch.forge.gmm.core.entity.projectile.BoneShard;
import mod.gottsch.forge.gmm.core.entity.projectile.Rock;
import mod.gottsch.forge.gmm.core.entity.projectile.SpikeGrowthSpell;
import mod.gottsch.forge.gmm.core.entity.projectile.WitheringGazeSpell;
import mod.gottsch.forge.gmm.core.entity.monster.Rat;
import mod.gottsch.forge.gmm.core.entity.monster.plant.Shrieker;
import mod.gottsch.forge.gmm.core.entity.monster.plant.VioletFungus;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Arrays;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonSetup {
	/**
	 * 
	 * @param event
	 */
	public static void common(final FMLCommonSetupEvent event) {
		wireRangedAttacks();
		// add mod specific logging
		Config.instance.addRollingFileAppender(Dungeons.MOD_ID);

		// Backlog #10 diagnostic. Registration is the one thing about the mob-set spawner that
		// cannot be observed from inside the game: the block is invisible, so "registered and
		// working" and "block entity type missing" look identical, and a DeferredRegister holder
		// that never class-loads registers NOTHING with no error at all (see DungeonsBlockEntities,
		// and #40/#41 where the same trap ate the rats). This runs after registration is complete,
		// so it is the authoritative answer.
		//
		//   grep "D2-REGISTRY" run/logs/dungeons2.log
		logRegistryPresence();
//		Dungeons2Networking.register();

		// Register structure piece types. The STRUCTURE_PIECE registry is frozen
		// after bootstrap, so this must run on the synchronized work queue (Forge
		// unfreezes the vanilla registries there).
		event.enqueueWork(StructurePieces::register);

		Dungeons.LOGGER.info("common setup complete");
		Dungeons.LOGGER.debug("initializing dimensional generated registries");

		// register all motifs (doesn't have to be restricted to the enum's values)
		Arrays.stream(DungeonMotif.values()).sequential().forEach(DungeonsApi::registerMotif);


		// Block palettes are datapack-driven: one MotifConfig per motif, loaded by
		// Forge's datapack-registry machinery (see MotifConfigRegistries). Nothing to
		// register here -- the string->enum PatternRegistry indirection this used to
		// need went away with the block_provider system it served.
	}

	/**
	 * Attributes for the dungeon's mobs (backlog #40 / #41).
	 *
	 * <p>The rat uses GMM's own {@code createAttributes}; the giant rat has its own, because it is
	 * the same class registered at a different size (see {@link DungeonsEntities}).</p>
	 */

	/**
	 * Hands GMM's mobs the projectiles they throw.
	 *
	 * <h2>Without this, six mobs are quietly half-built</h2>
	 * <p>GMM leaves a {@code public static} hook null on each of them and guards every use with a
	 * null check, exactly as it leaves registration to the consumer. The consequence is not a crash
	 * and not a log line: the Orc walks up and punches instead of throwing, the Shaman never casts,
	 * Bloody Bones collapses without flinging anything. <strong>A mob registered and not wired here
	 * looks completely fine until you watch it fight.</strong></p>
	 *
	 * <p>Called from {@code common} rather than from a registry event because it reads
	 * {@code RegistryObject#get}, which is only safe once the registries are frozen.</p>
	 */
	private static void wireRangedAttacks() {
		// The orc lobs a rock. The goal computes the spawn point (the orc's right hand); this
		// creates the projectile and gives it its ballistic arc.
		Orc.projectileLauncher = (shooter, target, x, y, z) -> {
			Rock rock = new Rock(DungeonsEntities.ROCK_ENTITY.get(), shooter.level());
			rock.setPos(x, y, z);
			rock.lobTo(shooter, target.getX(), target.getY(0.5D), target.getZ(), 0.8D);
			shooter.level().addFreshEntity(rock);
		};
		// The in-flight visual. GMM falls back to a FIRE CHARGE when this is unset -- a working
		// rock that looks like a fireball -- so both of the item-rendered projectiles get their real
		// art here. See DungeonsItems.ROCK_ITEM for where each texture comes from.
		Rock.itemSupplier = () -> DungeonsItems.ROCK_ITEM.get();

		// Spike Growth's whole telegraph/chevron geometry lives in the spell's own static cast(),
		// so the launcher delegates and ignores the goal's computed spawn point.
		OrcShaman.spellCaster = (caster, target, x, y, z) ->
				SpikeGrowthSpell.cast(DungeonsEntities.SPIKE_GROWTH_SPELL_ENTITY.get(), caster, target);

		// The bodak's gaze is an ordinary direct-damage bolt, so unlike Spike Growth it is aimed
		// here rather than by the spell.
		WitheringGazeSpell.itemSupplier = () -> DungeonsItems.WITHERING_GAZE_SPELL_ITEM.get();
		Bodak.spellCaster = (caster, target, x, y, z) -> {
			WitheringGazeSpell spell = new WitheringGazeSpell(
					DungeonsEntities.WITHERING_GAZE_SPELL_ENTITY.get(), caster.level());
			spell.init(caster, target.getX() - x, target.getY(0.5D) - y, target.getZ() - z);
			spell.setPos(x, y, z);
			caster.level().addFreshEntity(spell);
		};

		// Bone shrapnel: the tainted skeleton throws it, and Bloody Bones flings its own limbs as
		// the same projectile when it collapses to a skull.
		TaintedSkeleton.shardFactory = (shooter, level) ->
				new BoneShard(DungeonsEntities.BONE_SHARD_ENTITY.get(), shooter, level);
		BloodyBones.shardFactory = (shooter, level) ->
				new BoneShard(DungeonsEntities.BONE_SHARD_ENTITY.get(), shooter, level);
		// The bloater ruptures into real zombie arms rather than bone.
		Bloater.armFactory = (shooter, level) ->
				new BloaterArm(DungeonsEntities.BLOATER_ARM_ENTITY.get(), shooter, level);
	}

	@SubscribeEvent
	public static void onAttributeCreate(EntityAttributeCreationEvent event) {
		event.put(DungeonsEntities.RAT_ENTITY.get(), Rat.createAttributes().build());
		event.put(DungeonsEntities.GIANT_RAT_ENTITY.get(),
				DungeonsEntities.createGiantRatAttributes().build());
		event.put(DungeonsEntities.SHRIEKER_ENTITY.get(), Shrieker.createAttributes().build());
		event.put(DungeonsEntities.VIOLET_FUNGUS_ENTITY.get(), VioletFungus.createAttributes().build());
		// The GMM roster (2026-08-31). Every one of these uses GMM's own createAttributes: unlike
		// the giant rat, none of them is the same class registered at a different size, so there is
		// nothing here for this mod to override.
		event.put(DungeonsEntities.SKELETON_WARRIOR_ENTITY.get(), SkeletonWarrior.createAttributes().build());
		event.put(DungeonsEntities.WINGED_SKELETON_ENTITY.get(), WingedSkeleton.createAttributes().build());
		event.put(DungeonsEntities.IRON_SKELETON_ENTITY.get(), IronSkeleton.createAttributes().build());
		event.put(DungeonsEntities.MAGMA_SKELETON_ENTITY.get(), MagmaSkeleton.createAttributes().build());
		event.put(DungeonsEntities.TAINTED_SKELETON_ENTITY.get(), TaintedSkeleton.createAttributes().build());
		event.put(DungeonsEntities.ACID_SKELETON_ENTITY.get(), AcidSkeleton.createAttributes().build());
		event.put(DungeonsEntities.ELECTRIC_SKELETON_ENTITY.get(), ElectricSkeleton.createAttributes().build());
		event.put(DungeonsEntities.BURNING_SKELETON_ENTITY.get(), BurningSkeleton.createAttributes().build());
		event.put(DungeonsEntities.BLOODY_BONES_ENTITY.get(), BloodyBones.createAttributes().build());
		event.put(DungeonsEntities.SKELETON_CHAMPION_ENTITY.get(), SkeletonChampion.createAttributes().build());
		event.put(DungeonsEntities.BLOATER_ENTITY.get(), Bloater.createAttributes().build());
		event.put(DungeonsEntities.GRAVE_ZOMBIE_ENTITY.get(), GraveZombie.createAttributes().build());
		event.put(DungeonsEntities.WIGHT_ENTITY.get(), Wight.createAttributes().build());
		event.put(DungeonsEntities.BODAK_ENTITY.get(), Bodak.createAttributes().build());
		event.put(DungeonsEntities.GHOUL_ENTITY.get(), Ghoul.createAttributes().build());
		event.put(DungeonsEntities.GELATINOUS_CUBE_ENTITY.get(), GelatinousCube.createAttributes().build());
		event.put(DungeonsEntities.OCHRE_JELLY_ENTITY.get(), OchreJelly.createAttributes().build());
		event.put(DungeonsEntities.GRAY_OOZE_ENTITY.get(), GrayOoze.createAttributes().build());
		event.put(DungeonsEntities.BLACK_PUDDING_ENTITY.get(), BlackPudding.createAttributes().build());
		event.put(DungeonsEntities.ANIMATED_ARMOR_ENTITY.get(), AnimatedArmor.createAttributes().build());
		event.put(DungeonsEntities.MARGOYLE_ENTITY.get(), Margoyle.createAttributes().build());
		event.put(DungeonsEntities.ORC_ENTITY.get(), Orc.createAttributes().build());
		event.put(DungeonsEntities.ORC_SHAMAN_ENTITY.get(), OrcShaman.createAttributes().build());
		event.put(DungeonsEntities.ALLIGATOR_GAR_ENTITY.get(), AlligatorGar.createAttributes().build());
	}

	/**
	 * Where a rat is <em>allowed</em> to stand if something spawns one &mdash; not what causes one to
	 * spawn.
	 *
	 * <p>These two are separate mechanisms and it is easy to conflate them: this event only says
	 * "on the ground, at a position the heightmap agrees is solid, subject to the usual mob rules".
	 * What actually makes rats appear in a dungeon is the structure's {@code spawn_overrides}
	 * (backlog #42), which is still empty &mdash; so as of this commit the rats exist, render and can
	 * be spawn-egged, but nothing spawns them naturally.</p>
	 *
	 * <p>{@code Mob::checkMobSpawnRules} rather than the monster variant, matching the other GMM
	 * consumers: the monster rules add a light-level test, and a structure-scoped spawn should be
	 * governed by the structure, not by whether the player happens to have lit the room.</p>
	 */
	@SubscribeEvent
	public static void onRegisterSpawnPlacements(SpawnPlacementRegisterEvent event) {
		event.register(DungeonsEntities.RAT_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.GIANT_RAT_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		// Registered for the same reason the rats' are -- this says where one is ALLOWED to stand,
		// not what spawns one. Nothing spawns these naturally: the weathering pass places them
		// directly (FungusGrowth), which bypasses spawn placement entirely. The registration still
		// earns its keep for a spawn egg and for /summon.
		event.register(DungeonsEntities.SHRIEKER_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.VIOLET_FUNGUS_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);

		// The GMM roster. ON_GROUND for everything that walks; the Winged Skeleton is the one
		// flyer, and NO_RESTRICTIONS is what lets it be placed in the air rather than only on a
		// solid block it would then have to leave.
		// Mob::checkMobSpawnRules throughout, for the reason given above: a structure-scoped
		// spawn should be governed by the structure and not by whether the room is lit.
		event.register(DungeonsEntities.SKELETON_WARRIOR_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.WINGED_SKELETON_ENTITY.get(), SpawnPlacements.Type.NO_RESTRICTIONS,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.IRON_SKELETON_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.MAGMA_SKELETON_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.TAINTED_SKELETON_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.ACID_SKELETON_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.ELECTRIC_SKELETON_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.BURNING_SKELETON_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.BLOODY_BONES_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.SKELETON_CHAMPION_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.BLOATER_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.GRAVE_ZOMBIE_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.WIGHT_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.BODAK_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.GHOUL_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.GELATINOUS_CUBE_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.OCHRE_JELLY_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.GRAY_OOZE_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.BLACK_PUDDING_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.ANIMATED_ARMOR_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.MARGOYLE_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.ORC_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.ORC_SHAMAN_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);

		// The gar is the one water mob, so it is the one exception to both defaults above. IN_WATER
		// rather than ON_GROUND (vanilla's own placement type checks the block IS water), and
		// OCEAN_FLOOR rather than MOTION_BLOCKING_NO_LEAVES -- the latter heightmap stops at the water
		// SURFACE, so a gar placed against it would be asked to stand on top of the pool.
		event.register(DungeonsEntities.ALLIGATOR_GAR_ENTITY.get(), SpawnPlacements.Type.IN_WATER,
				Heightmap.Types.OCEAN_FLOOR, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
	}

	/**
	 * Reports whether #10's block, its block entity type and its marker are actually in the game's
	 * registries. Logged at INFO deliberately -- a missing block entity type is a hard fault, not a
	 * detail, and this is the line that says so without needing debug logging turned on.
	 */
	private static void logRegistryPresence() {
		java.util.List<String> report = new java.util.ArrayList<>();
		for (String name : java.util.List.of("mob_set_spawner", "spawner_marker")) {
			net.minecraft.resources.ResourceLocation id =
					new net.minecraft.resources.ResourceLocation(Dungeons.MOD_ID, name);
			report.add(name + ": block="
					+ net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id));
		}
		net.minecraft.resources.ResourceLocation spawner =
				new net.minecraft.resources.ResourceLocation(Dungeons.MOD_ID, "mob_set_spawner");
		report.add("mob_set_spawner: blockEntityType="
				+ net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(spawner));
		Dungeons.LOGGER.info("[D2-REGISTRY] {}", String.join("  |  ", report));
	}
}
