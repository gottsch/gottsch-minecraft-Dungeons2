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
