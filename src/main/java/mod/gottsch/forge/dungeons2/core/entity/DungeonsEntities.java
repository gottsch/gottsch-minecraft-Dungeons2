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
package mod.gottsch.forge.dungeons2.core.entity;

import mod.gottsch.forge.dungeons2.core.setup.Registration;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.registries.RegistryObject;

/**
 * The dungeon's mobs (backlog #40 / #41).
 *
 * <h2>GMM is a library; this mod does the registering</h2>
 * <p>gottsch's Monster Manual ships the mob classes, models, renderers and textures and
 * <strong>registers nothing</strong> &mdash; that is the design, not an omission. The consuming mod
 * registers what it wants under its own namespace, which is why these are {@code dungeons2:rat} and
 * {@code dungeons2:giant_rat} rather than {@code gmm:} ids. {@code Dungeon-Denizens} and
 * {@code Village-Dungeons} are the other two consumers; the shape below follows Village Dungeons,
 * which is the most recent.</p>
 *
 * <h2>Why the giant rat is not a subclass</h2>
 * <p>A subclass buys nothing here. The giant rat differs from the rat in exactly two ways &mdash;
 * how big its hitbox is and how hard it hits &mdash; and both are data on the {@link EntityType} and
 * the {@link AttributeSupplier}, not behaviour. Village Dungeons registers its own giant rat the
 * same way, on {@code Rat::new}; it subclasses only where behaviour actually changes (its
 * {@code InfectedRat} overrides {@code doHurtTarget} to apply poison and {@code canBeAffected} to be
 * immune to it). <strong>Subclass when there is behaviour to override, not to make a variant.</strong>
 * Giving the giant rat its own goals later is the point at which that changes.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
public class DungeonsEntities {

    public static final String RAT = "rat";
    public static final String GIANT_RAT = "giant_rat";

    /**
     * GMM's rat at its own size. {@code Rat.WIDTH} (0.8) is deliberately wider than the model so
     * that a player can actually hit something this flat &mdash; do not "correct" it to match the
     * art.
     */
    public static final RegistryObject<EntityType<Rat>> RAT_ENTITY =
            Registration.ENTITIES.register(RAT,
                    () -> EntityType.Builder.of(Rat::new, MobCategory.MONSTER)
                            .sized(Rat.WIDTH, Rat.HEIGHT)
                            .clientTrackingRange(8)
                            .setShouldReceiveVelocityUpdates(true)
                            .build(RAT));

    /**
     * Twice the height, and rendered at twice the scale (see {@code ClientSetup}).
     *
     * <p><strong>The hitbox and the render scale are two independent numbers</strong> and have to be
     * kept in step by hand; a mismatch is the classic giant-mob bug, where the model clips through
     * walls or the hitbox cannot be hit. The width is left alone because it is already over-sized
     * for hittability.</p>
     *
     * <p>0.8 x 0.5 fits the narrowest corridor a datapack can configure (width 1, height 5), so this
     * cannot wedge in a passage.</p>
     */
    public static final RegistryObject<EntityType<Rat>> GIANT_RAT_ENTITY =
            Registration.ENTITIES.register(GIANT_RAT,
                    () -> EntityType.Builder.of(Rat::new, MobCategory.MONSTER)
                            .sized(Rat.WIDTH, Rat.HEIGHT * 2)
                            .clientTrackingRange(8)
                            .setShouldReceiveVelocityUpdates(true)
                            .build(GIANT_RAT));

    public static final String SHRIEKER = "shrieker";
    public static final String VIOLET_FUNGUS = "violet_fungus";

    /**
     * The fungi, placed by the weathering pass rather than spawned (see {@code FungusGrowth}).
     *
     * <h2>They are mobs that behave like plants, and that is the whole reason they fit here</h2>
     * <p>Both are {@code GMMMonster}s, but neither moves: they root themselves on spawn and never
     * path. That is what makes them placeable as <em>growth</em> — a tuft of something on a patch of
     * decayed dirt — where an ordinary monster would wander off the cell that justified it.</p>
     *
     * <h2>Sized from Dungeon Denizens, deliberately</h2>
     * <p>{@code 1.3 x 0.85} is the box DD arrived at from the rebuilt Blockbench rig, and it is
     * <strong>wider than one block</strong> on purpose. That matters here in a way it does not in a
     * cave: growth lands on any dirt cell, including one against a wall, so a fungus can visually
     * overlap the wall beside it. That is cosmetic — these have no collision to speak of and no AI
     * to get stuck — and narrowing the box to fit the grid would make the hitbox disagree with the
     * model, which is the worse bug of the two. Keep them in step with GMM if DD re-sizes.</p>
     *
     * <p>Registered here rather than depended upon: {@code ddenizens} already registers both, but
     * D2 does not depend on it and the dungeon's monsters are its own — the same call #40/#41 made
     * for the rats. GMM is already a mandatory dependency, so this costs nothing new.</p>
     */
    public static final RegistryObject<EntityType<Shrieker>> SHRIEKER_ENTITY =
            Registration.ENTITIES.register(SHRIEKER,
                    () -> EntityType.Builder.of(Shrieker::new, MobCategory.MONSTER)
                            .sized(1.3F, 0.85F)
                            .clientTrackingRange(10)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(SHRIEKER));

    /** See {@link #SHRIEKER_ENTITY}; GMM currently shares the shrieker's rig for this one. */
    public static final RegistryObject<EntityType<VioletFungus>> VIOLET_FUNGUS_ENTITY =
            Registration.ENTITIES.register(VIOLET_FUNGUS,
                    () -> EntityType.Builder.of(VioletFungus::new, MobCategory.MONSTER)
                            .sized(1.3F, 0.85F)
                            .clientTrackingRange(10)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(VIOLET_FUNGUS));

    /**
     * GMM's skeleton family &mdash; eleven of them, not twelve.
     *
     * <p>{@code BowSkeleton} looks like one of them in the package listing and is
     * <strong>abstract</strong>: it is the ranged base class the Iron and Magma skeletons extend,
     * not a mob. Registering it would not compile, which is the only reason that mistake cannot be
     * made silently here.</p>
     *
     * <p>GMM's {@code FrostSkeleton} is deliberately absent (Mark, 2026-08-31): it is cold-themed
     * and the classic motif is not, so it waits for an ice motif to belong to. A different kind of
     * exclusion from the mini-bosses below &mdash; those are held back until their placement is
     * designed, this one until there is somewhere for it to fit.</p>
     *
     * <p>Sizes, tracking ranges and {@code fireImmune} are Dungeon Denizens' numbers verbatim. They
     * are not arbitrary &mdash; each was fitted to its rebuilt Blockbench rig &mdash; and the same
     * mob should read the same in any pack carrying both mods.</p>
     */
    public static final String SKELETON_WARRIOR = "skeleton_warrior";
    public static final String WINGED_SKELETON = "winged_skeleton";
    public static final String IRON_SKELETON = "iron_skeleton";
    public static final String MAGMA_SKELETON = "magma_skeleton";
    public static final String TAINTED_SKELETON = "tainted_skeleton";
    public static final String ACID_SKELETON = "acid_skeleton";
    public static final String ELECTRIC_SKELETON = "electric_skeleton";
    public static final String BURNING_SKELETON = "burning_skeleton";
    public static final String BLOODY_BONES = "bloody_bones";
    public static final String SKELETON_CHAMPION = "skeleton_champion";

    public static final RegistryObject<EntityType<SkeletonWarrior>> SKELETON_WARRIOR_ENTITY =
            Registration.ENTITIES.register(SKELETON_WARRIOR,
                    () -> EntityType.Builder.of(SkeletonWarrior::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(SKELETON_WARRIOR));

    public static final RegistryObject<EntityType<WingedSkeleton>> WINGED_SKELETON_ENTITY =
            Registration.ENTITIES.register(WINGED_SKELETON,
                    () -> EntityType.Builder.of(WingedSkeleton::new, MobCategory.MONSTER)
                            .sized(0.75F, 1.95F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(WINGED_SKELETON));

    public static final RegistryObject<EntityType<IronSkeleton>> IRON_SKELETON_ENTITY =
            Registration.ENTITIES.register(IRON_SKELETON,
                    () -> EntityType.Builder.of(IronSkeleton::new, MobCategory.MONSTER)
                            .sized(0.63F, 2.1F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(IRON_SKELETON));

    public static final RegistryObject<EntityType<MagmaSkeleton>> MAGMA_SKELETON_ENTITY =
            Registration.ENTITIES.register(MAGMA_SKELETON,
                    () -> EntityType.Builder.of(MagmaSkeleton::new, MobCategory.MONSTER)
                            .sized(0.63F, 2.1F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .fireImmune()
                            .build(MAGMA_SKELETON));


    public static final RegistryObject<EntityType<TaintedSkeleton>> TAINTED_SKELETON_ENTITY =
            Registration.ENTITIES.register(TAINTED_SKELETON,
                    () -> EntityType.Builder.of(TaintedSkeleton::new, MobCategory.MONSTER)
                            .sized(0.7F, 1.99F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(TAINTED_SKELETON));

    public static final RegistryObject<EntityType<AcidSkeleton>> ACID_SKELETON_ENTITY =
            Registration.ENTITIES.register(ACID_SKELETON,
                    () -> EntityType.Builder.of(AcidSkeleton::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.99F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(ACID_SKELETON));

    public static final RegistryObject<EntityType<ElectricSkeleton>> ELECTRIC_SKELETON_ENTITY =
            Registration.ENTITIES.register(ELECTRIC_SKELETON,
                    () -> EntityType.Builder.of(ElectricSkeleton::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.99F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(ELECTRIC_SKELETON));

    public static final RegistryObject<EntityType<BurningSkeleton>> BURNING_SKELETON_ENTITY =
            Registration.ENTITIES.register(BURNING_SKELETON,
                    () -> EntityType.Builder.of(BurningSkeleton::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.99F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .fireImmune()
                            .build(BURNING_SKELETON));

    public static final RegistryObject<EntityType<BloodyBones>> BLOODY_BONES_ENTITY =
            Registration.ENTITIES.register(BLOODY_BONES,
                    () -> EntityType.Builder.of(BloodyBones::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.99F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(BLOODY_BONES));

    public static final RegistryObject<EntityType<SkeletonChampion>> SKELETON_CHAMPION_ENTITY =
            Registration.ENTITIES.register(SKELETON_CHAMPION,
                    () -> EntityType.Builder.of(SkeletonChampion::new, MobCategory.MONSTER)
                            .sized(0.72F, 2.39F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(SKELETON_CHAMPION));

    /**
     * The zombie family. Three of the four are registered but never spawned &mdash; see
     * {@link #MINI_BOSSES}.
     */
    public static final String BLOATER = "bloater";
    public static final String GRAVE_ZOMBIE = "grave_zombie";
    public static final String WIGHT = "wight";
    public static final String BODAK = "bodak";

    public static final RegistryObject<EntityType<Bloater>> BLOATER_ENTITY =
            Registration.ENTITIES.register(BLOATER,
                    () -> EntityType.Builder.of(Bloater::new, MobCategory.MONSTER)
                            .sized(0.7F, 2.1F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(BLOATER));

    public static final RegistryObject<EntityType<GraveZombie>> GRAVE_ZOMBIE_ENTITY =
            Registration.ENTITIES.register(GRAVE_ZOMBIE,
                    () -> EntityType.Builder.of(GraveZombie::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(GRAVE_ZOMBIE));

    public static final RegistryObject<EntityType<Wight>> WIGHT_ENTITY =
            Registration.ENTITIES.register(WIGHT,
                    () -> EntityType.Builder.of(Wight::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(WIGHT));

    public static final RegistryObject<EntityType<Bodak>> BODAK_ENTITY =
            Registration.ENTITIES.register(BODAK,
                    () -> EntityType.Builder.of(Bodak::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(BODAK));

    /**
     * The ghoul. GMM's {@code SewerGhoul} is deliberately absent: Mark is reserving it for a Sewer
     * dungeon mod, and a mob that appears in two of his mods under two ids is a mob players will
     * report as a duplicate.
     */
    public static final String GHOUL = "ghoul";

    public static final RegistryObject<EntityType<Ghoul>> GHOUL_ENTITY =
            Registration.ENTITIES.register(GHOUL,
                    () -> EntityType.Builder.of(Ghoul::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.68F)
                            .clientTrackingRange(8)
                            .setShouldReceiveVelocityUpdates(false)
                            .setTrackingRange(20)
                            .build(GHOUL));

    /**
     * The oozes. Cubic hitboxes, and all four are slow &mdash; they are a corridor problem rather
     * than a chase, which is what makes them read as a dungeon hazard.
     */
    public static final String GELATINOUS_CUBE = "gelatinous_cube";
    public static final String OCHRE_JELLY = "ochre_jelly";
    public static final String GRAY_OOZE = "gray_ooze";
    public static final String BLACK_PUDDING = "black_pudding";

    public static final RegistryObject<EntityType<GelatinousCube>> GELATINOUS_CUBE_ENTITY =
            Registration.ENTITIES.register(GELATINOUS_CUBE,
                    () -> EntityType.Builder.of(GelatinousCube::new, MobCategory.MONSTER)
                            .sized(1.1F, 1.1F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(GELATINOUS_CUBE));

    public static final RegistryObject<EntityType<OchreJelly>> OCHRE_JELLY_ENTITY =
            Registration.ENTITIES.register(OCHRE_JELLY,
                    () -> EntityType.Builder.of(OchreJelly::new, MobCategory.MONSTER)
                            .sized(0.85F, 0.85F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(OCHRE_JELLY));

    public static final RegistryObject<EntityType<GrayOoze>> GRAY_OOZE_ENTITY =
            Registration.ENTITIES.register(GRAY_OOZE,
                    () -> EntityType.Builder.of(GrayOoze::new, MobCategory.MONSTER)
                            .sized(0.9F, 0.9F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(GRAY_OOZE));

    public static final RegistryObject<EntityType<BlackPudding>> BLACK_PUDDING_ENTITY =
            Registration.ENTITIES.register(BLACK_PUDDING,
                    () -> EntityType.Builder.of(BlackPudding::new, MobCategory.MONSTER)
                            .sized(0.9F, 0.9F)
                            .clientTrackingRange(15)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(BLACK_PUDDING));

    /** Constructs, the margoyle, and the orcs. */
    public static final String ANIMATED_ARMOR = "animated_armor";
    public static final String MARGOYLE = "margoyle";
    public static final String ORC = "orc";
    public static final String ORC_SHAMAN = "orc_shaman";

    public static final RegistryObject<EntityType<AnimatedArmor>> ANIMATED_ARMOR_ENTITY =
            Registration.ENTITIES.register(ANIMATED_ARMOR,
                    () -> EntityType.Builder.of(AnimatedArmor::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.99F)
                            .clientTrackingRange(10)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(ANIMATED_ARMOR));

    public static final RegistryObject<EntityType<Margoyle>> MARGOYLE_ENTITY =
            Registration.ENTITIES.register(MARGOYLE,
                    () -> EntityType.Builder.of(Margoyle::new, MobCategory.MONSTER)
                            .sized(0.9F, 2.1F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(MARGOYLE));

    public static final RegistryObject<EntityType<Orc>> ORC_ENTITY =
            Registration.ENTITIES.register(ORC,
                    () -> EntityType.Builder.of(Orc::new, MobCategory.MONSTER)
                            .sized(1F, 1.99F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(ORC));

    public static final RegistryObject<EntityType<OrcShaman>> ORC_SHAMAN_ENTITY =
            Registration.ENTITIES.register(ORC_SHAMAN,
                    () -> EntityType.Builder.of(OrcShaman::new, MobCategory.MONSTER)
                            .sized(1F, 1.99F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(ORC_SHAMAN));

    public static final String ALLIGATOR_GAR = "alligator_gar";

    /**
     * A water ambush predator, for the water rooms Mark is authoring (2026-08-31).
     *
     * <p>The only mob in this roster that is not registered {@code ON_GROUND} &mdash; see
     * {@code CommonSetup}. Flat and wide rather than tall: {@code 0.6 x 0.4} is a fish lying in
     * water, and like the rat's box it is wider than the art so that a player can hit something
     * that low. {@code setTrackingRange(20)} is GMM's own value and is what lets it notice a player
     * across a flooded room rather than only at the water's edge.</p>
     */
    public static final RegistryObject<EntityType<AlligatorGar>> ALLIGATOR_GAR_ENTITY =
            Registration.ENTITIES.register(ALLIGATOR_GAR,
                    () -> EntityType.Builder.of(AlligatorGar::new, MobCategory.MONSTER)
                            .sized(0.6F, 0.4F)
                            .clientTrackingRange(8)
                            .setShouldReceiveVelocityUpdates(false)
                            .setTrackingRange(20)
                            .build(ALLIGATOR_GAR));

    /**
     * The projectiles and spells the roster's mobs throw &mdash; and the reason they have to be here.
     *
     * <h2>A GMM mob's ranged attack is opt-in, and silently absent if you skip it</h2>
     * <p>Six of the mobs above carry a {@code public static} hook GMM leaves null: {@code Orc
     * .projectileLauncher}, {@code OrcShaman.spellCaster}, {@code Bodak.spellCaster},
     * {@code BloodyBones}/{@code TaintedSkeleton.shardFactory}, {@code Bloater.armFactory}. Each is
     * guarded by a null check, so a consumer that registers the mob and stops there gets a mob that
     * <strong>compiles, spawns, renders and never uses its signature attack</strong> &mdash; no
     * warning, no crash. The same library-registers-nothing design as the mobs themselves, one level
     * down, and far easier to miss.</p>
     *
     * <p>So these five entity types exist to give those hooks something to throw; {@code CommonSetup}
     * wires them. {@code MobCategory.MISC}: they are projectiles, and putting them in MONSTER would
     * enter them into the mob cap.</p>
     */
    public static final String BONE_SHARD = "bone_shard";
    public static final String BLOATER_ARM = "bloater_arm";
    public static final String ROCK = "rock";
    public static final String SPIKE_GROWTH_SPELL = "spike_growth_spell";
    public static final String WITHERING_GAZE_SPELL = "withering_gaze_spell";

    /** The shrapnel Bloody Bones and the Tainted Skeleton throw. */
    public static final RegistryObject<EntityType<BoneShard>> BONE_SHARD_ENTITY =
            Registration.ENTITIES.register(BONE_SHARD,
                    () -> EntityType.Builder.<BoneShard>of(BoneShard::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build(BONE_SHARD));

    /** The limbs a Bloater ruptures into on death. */
    public static final RegistryObject<EntityType<BloaterArm>> BLOATER_ARM_ENTITY =
            Registration.ENTITIES.register(BLOATER_ARM,
                    () -> EntityType.Builder.<BloaterArm>of(BloaterArm::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build(BLOATER_ARM));

    /** The Orc's thrown rock. */
    public static final RegistryObject<EntityType<Rock>> ROCK_ENTITY =
            Registration.ENTITIES.register(ROCK,
                    () -> EntityType.Builder.<Rock>of(Rock::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(ROCK));

    /** The Orc Shaman's spell. */
    public static final RegistryObject<EntityType<SpikeGrowthSpell>> SPIKE_GROWTH_SPELL_ENTITY =
            Registration.ENTITIES.register(SPIKE_GROWTH_SPELL,
                    () -> EntityType.Builder.<SpikeGrowthSpell>of(SpikeGrowthSpell::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(SPIKE_GROWTH_SPELL));

    /** The Bodak's withering gaze. */
    public static final RegistryObject<EntityType<WitheringGazeSpell>> WITHERING_GAZE_SPELL_ENTITY =
            Registration.ENTITIES.register(WITHERING_GAZE_SPELL,
                    () -> EntityType.Builder.<WitheringGazeSpell>of(WitheringGazeSpell::new, MobCategory.MISC)
                            .sized(1F, 1F)
                            .clientTrackingRange(12)
                            .setShouldReceiveVelocityUpdates(false)
                            .build(WITHERING_GAZE_SPELL));

    /**
     * The three mobs that are registered but must never be <em>spawned</em> by the dungeon (Mark,
     * 2026-08-31: "none of the small nor big bosses are in either spawners").
     *
     * <p>They are intended as mini-bosses / small dungeon bosses, which is a placement decision that
     * has not been designed yet &mdash; so until it is, they are reachable by spawn egg and by
     * {@code /summon} and by nothing else. Declared here rather than left as an absence in two JSON
     * files, because "this mob is missing from the mob sets" and "this mob is deliberately excluded"
     * look identical in a datapack. {@code MobSpawnExclusionTest} reads this list and fails the build
     * if any of them turns up in a mob set or in the structure's spawn overrides.</p>
     */
    public static final java.util.List<String> MINI_BOSSES =
            java.util.List.of(SKELETON_CHAMPION, WIGHT, BODAK);

    /** Twice the rat's health and damage; same speed, so it is a threat rather than a chase. */
    public static AttributeSupplier.Builder createGiantRatAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    /**
     * Forces this class to load so its {@code static} fields actually reach the
     * {@code DeferredRegister}.
     *
     * <p>A {@code DeferredRegister} collects an entry when the {@link RegistryObject} field
     * initialises, which only happens when the holding class is first touched. A registry class that
     * nothing references before the registry events fire silently registers <em>nothing</em>, with no
     * error &mdash; so {@code Registration.init()} calls this rather than relying on some other code
     * path happening to mention the class first.</p>
     */
    public static void register() {
        // Intentionally empty -- calling it is the point.
    }

    private DungeonsEntities() {}
}
