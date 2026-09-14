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
package mod.gottsch.forge.dungeons2.core.entity.projectile;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Beholder-kin's annihilation ray: a beam from the caster's eye that burns through whatever
 * stands between it and its target.
 *
 * <p>A Dungeons2 spell, not a gmm one, for the same reason {@code SmashBlocksGoal} is: what a mob
 * may take apart is a property of the dungeon, not of the mob. gmm's {@code DisintegrateSpell} is
 * an unrelated thing &mdash; a thrown bolt &mdash; and is left as it is.</p>
 *
 * <h2>Not a projectile</h2>
 * <p>Nothing travels. Each server tick the beam is re-measured from the caster's eye along its aim,
 * and its length is wherever that stops: the first thing it cannot burn, the first target in the
 * way, or {@link #MAX_RANGE}. The end point is synched and the client draws from the caster's
 * interpolated position to it, so the beam stays welded to the eye however the caster moves.</p>
 *
 * <h2>Two ways of measuring &mdash; a line, or the caster's body</h2>
 * <p>A {@link Mode#STRIKE} beam is a line: one ray, one block burned at a time. That is an attack,
 * and a one-block hole is all an attack needs.</p>
 *
 * <p>A <strong>boring</strong> beam ({@link Mode#TUNNEL}, {@link Mode#ESCAPE}) is cutting a way for
 * the caster to <em>follow</em>, and a ray is the wrong instrument for that. A Beholder is 3.5 tall;
 * aimed through a two-block dungeon doorway, its centre ray sails straight through the gap and never
 * touches the lintel that actually stops its body. So a boring beam sweeps the caster's own bounding
 * box forward along the aim ({@link #obstructionAhead}) and burns every block that box first runs
 * into &mdash; the hole this mob needs, read off its own size, as {@code SmashBlocksGoal
 * #findSmashTarget} does for the Minotaur.</p>
 *
 * <h2>What it burns &mdash; the power budget</h2>
 * <p>Every beam starts with {@link #power()} equal to <strong>obsidian's blast resistance</strong>
 * (Mark, 2026-09-10: "enough power to break obsidian &mdash; 1 block"). Each block burned spends its
 * own resistance, so one beam goes through exactly one obsidian, or any mix of lesser blocks adding
 * up to the same total. A block is burned only if what is left covers it <em>entirely</em>: the
 * test is {@code resistance <= power}, so obsidian on a full budget goes and a second one does not.
 * Read from {@code Blocks.OBSIDIAN} rather than written as 1200, so the rule stays "one obsidian" if
 * that number ever moves.</p>
 *
 * <p>In practice dungeon brick costs 6, so the budget is rarely what stops a beam in a dungeon
 * &mdash; the range and the burn rate are. It matters for what a player builds to hide behind.</p>
 *
 * <p>Beyond the budget, the same three exclusions as {@code SmashBlocksGoal}, for the same reasons:
 * nothing with a <strong>block entity</strong> (a chest is loot, a spawner is an encounter, a marker
 * is authoring), nothing unbreakable, and nothing outside {@link #LEASH_RADIUS} of where the caster
 * first appeared unless it is escaping. And {@code mobGriefing} off means it burns nothing at all
 * but still hurts.</p>
 *
 * <p>Like the Minotaur, it ignores the boss room lock: {@code destroyBlock} raises no
 * {@code BreakEvent}. A mob breaching a wall is the fight. It <em>does</em> raise Forge's
 * {@code LivingDestroyBlockEvent}, as the Wither does, so a protection mod can veto it.</p>
 *
 * <h2>Who it hurts</h2>
 * <p>Players, and whatever the caster is currently targeting &mdash; nothing else. Damage is
 * {@link #DAMAGE} per pulse, entirely separate from the power budget, under the beam's own damage
 * type so armour counts and the death message reads right. Everything else is passed through:
 * a beam that cut down its own thralls and escort would disarm the encounter before the player
 * arrived (same reasoning as the fire mobs being pulled out of mixed sets, 2026-09-09), and
 * gmm's {@code MobHurtByTargetGoal} would turn every stray hit into infighting.</p>
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
public class AnnihilationRay extends Entity {

    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(Dungeons.MOD_ID, "annihilation_ray"));

    /**
     * What the beam is fired for, which decides how it behaves once lit.
     */
    public enum Mode {
        /**
         * At a target: a one-block line that tracks it at {@link #MAX_TURN_RADIANS} a tick. Used by
         * the spell pools and by {@code BeamBreachGoal} shooting through a wall.
         */
        STRIKE(false, false),
        /**
         * Toward a target the caster cannot get to: a fixed aim, bored to the caster's size so it
         * can follow, and leashed &mdash; the Minotaur digging toward someone, in light.
         */
        TUNNEL(true, false),
        /**
         * Out of confinement: a fixed aim, bored to the caster's size, and exempt from the leash
         * &mdash; see {@code SmashBlocksGoal#isSmashable} for why escaping is exempt.
         */
        ESCAPE(true, true);

        /** Sweeps the caster's body rather than casting a line. See the class javadoc. */
        private final boolean bores;
        /** May burn outside the leash. */
        private final boolean unleashed;

        Mode(boolean bores, boolean unleashed) {
            this.bores = bores;
            this.unleashed = unleashed;
        }
    }

    /**
     * Where a caster's body, pushed along an aim, first runs into something &mdash; how far it got
     * and every block it is touching there.
     */
    public record Obstruction(double distance, List<BlockPos> cells) {}

    /** Damage per pulse to anyone the beam is allowed to hurt. Independent of the power budget. */
    public static final float DAMAGE = 4.0F;
    /**
     * Ticks between pulses. Ten, which is exactly the window a hurt entity is immune to equal or
     * lesser damage &mdash; any shorter and the extra pulses would be silently thrown away.
     */
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    /** How long one beam burns: a second and a half, so at most three pulses land. */
    public static final int LIFETIME_TICKS = 30;
    /**
     * Ticks the beam must hold on a block, or a boring beam on a slice, before it goes. Two, so a
     * wall visibly burns through rather than evaporating, and a {@link #LIFETIME_TICKS} beam cuts at
     * most fifteen slices deep.
     */
    private static final int BURN_TICKS_PER_BLOCK = 2;
    /** Reach of the beam, and of the ray or sweep that measures it each tick. */
    public static final double MAX_RANGE = 32.0D;
    /** How fast a STRIKE beam follows its target &mdash; see {@link BeamMath#rotateToward}. */
    private static final double MAX_TURN_RADIANS = Math.toRadians(4.0D);
    /**
     * How far the body is pushed per step of {@link #obstructionAhead}. Half a block: a whole block
     * could step a box clean past the corner of a one-block pillar on a diagonal.
     */
    private static final double SWEEP_STEP = 0.5D;
    /** Same leash as {@code SmashBlocksGoal}: the place it belongs to, not a player's house. */
    private static final int LEASH_RADIUS = 48;
    /** See {@link #recordHome}. */
    private static final String HOME_TAG = Dungeons.MOD_ID + ":beam_home";
    /** Shrinks a box before reading the cells it covers. See {@code SmashBlocksGoal.EDGE_BIAS}. */
    private static final double EDGE_BIAS = 1.0E-7D;

    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(AnnihilationRay.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_END =
            SynchedEntityData.defineId(AnnihilationRay.class, EntityDataSerializers.VECTOR3);

    // server-side state; the beam is never saved (see shouldBeSaved), so none of it is persisted
    private Mode mode = Mode.STRIKE;
    private Vec3 aim = new Vec3(0.0D, 0.0D, 1.0D);
    private float power;
    private int age;
    private BlockPos burning;
    private int burnTicks;
    private int damageCooldown;

    public AnnihilationRay(EntityType<? extends AnnihilationRay> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Lights a beam from {@code caster} along {@code aim}.
     *
     * @return false if the caster already has a beam burning; one at a time
     */
    public static boolean fire(Mob caster, Vec3 aim, Mode mode) {
        if (caster.level().isClientSide || aim.lengthSqr() < 1.0E-4D || hasActiveBeam(caster)) {
            return false;
        }
        AnnihilationRay beam = new AnnihilationRay(
                DungeonsEntities.ANNIHILATION_RAY_ENTITY.get(), caster.level());
        beam.mode = mode;
        beam.aim = aim.normalize();
        beam.power = power();
        Vec3 origin = originAlong(caster, beam.aim, 1.0F);
        beam.setPos(origin.x, origin.y, origin.z);
        beam.entityData.set(DATA_OWNER, caster.getId());
        // Set before the entity is added, so the pairing packet carries a real end point and the
        // client's first frame is not a beam to the world origin.
        beam.setEnd(origin);
        caster.level().addFreshEntity(beam);
        caster.playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.8F);
        return true;
    }

    /** Unit vector from the caster's body centre to its target's: where to SHOOT. */
    public static Vec3 aimAt(Mob caster, LivingEntity target) {
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(centreOf(caster, 1.0F));
        return to.lengthSqr() < 1.0E-4D ? caster.getViewVector(1.0F) : to.normalize();
    }

    /**
     * Where the caster's body centre would be to hang level with its target: the target's feet plus
     * half the caster's height. Where to GO, as opposed to {@link #aimAt}.
     *
     * <p>The difference is the floor. A Beholder floats well above the player it is chasing, so the
     * line from its centre to the player's chest points down &mdash; and its 3.5-block body, swept
     * along that line, meets the floor long before it meets the player. Aimed here instead, the
     * bottom of the body arrives at the player's feet and the sweep runs level.</p>
     */
    public static Vec3 pursuitPoint(Mob caster, LivingEntity target) {
        return target.position().add(0.0D, caster.getBbHeight() * 0.5D, 0.0D);
    }

    /** Unit vector toward {@link #pursuitPoint}. */
    public static Vec3 pursuitAim(Mob caster, LivingEntity target) {
        Vec3 to = pursuitPoint(caster, target).subtract(centreOf(caster, 1.0F));
        return to.lengthSqr() < 1.0E-4D ? caster.getViewVector(1.0F) : to.normalize();
    }

    /** The obsidian budget &mdash; see the class javadoc. */
    public static float power() {
        return Blocks.OBSIDIAN.getExplosionResistance();
    }

    /**
     * Pins the caster's leash centre the first time it joins a level, in its own saved data.
     *
     * <p>Deliberately <em>not</em> {@code SmashBlocksGoal}'s approach of capturing the position when
     * the goal is attached. That re-anchors on every chunk load, so a mob that has wandered 40 blocks
     * gets a fresh 48 around wherever it is now; recorded once it stays where the mob came from.</p>
     */
    public static void recordHome(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(HOME_TAG)) {
            data.putLong(HOME_TAG, mob.blockPosition().asLong());
        }
    }

    /**
     * Whether a beam from {@code mob} may burn the block at {@code pos}, budget aside.
     *
     * <p>Public so {@code BeamBreachGoal} can ask before it spends a charge: a wall it cannot burn
     * is not worth firing at.</p>
     */
    public static boolean canAnnihilate(Mob mob, BlockPos pos, boolean escaping) {
        Level level = mob.level();
        if (!escaping && !withinLeash(mob, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.getDestroySpeed(level, pos) >= 0.0F;
    }

    /**
     * Whether one boring beam could clear every one of {@code cells}: each burnable, and all of them
     * together within the budget. All or nothing, because a hole with one block left in it is a hole
     * the caster still cannot fit through, and firing at it would only spend the charge.
     */
    public static boolean canBoreThrough(Mob mob, List<BlockPos> cells, boolean escaping) {
        Level level = mob.level();
        float cost = 0.0F;
        for (BlockPos cell : cells) {
            if (!canAnnihilate(mob, cell, escaping)) {
                return false;
            }
            cost += level.getBlockState(cell).getExplosionResistance(level, cell, null);
        }
        return cost <= power();
    }

    /**
     * Pushes {@code mob}'s bounding box along {@code aim}, {@link #SWEEP_STEP} at a time, up to
     * {@code maxDistance}, and reports the first place it would run into anything with a collision
     * shape. Null if the way is clear that far.
     *
     * <p>This is the question a boring beam and {@code BeholderkinPursueGoal} both need answered
     * &mdash; "could my whole body get there" &mdash; and it is exactly the test gmm's
     * {@code BeholderkinMoveControl#canReach} applies before it will move at all.</p>
     */
    public static Obstruction obstructionAhead(Mob mob, Vec3 aim, double maxDistance) {
        Level level = mob.level();
        AABB body = mob.getBoundingBox();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (double d = SWEEP_STEP; d <= maxDistance; d += SWEEP_STEP) {
            AABB moved = body.move(aim.scale(d));
            List<BlockPos> cells = new ArrayList<>();
            for (int x = Mth.floor(moved.minX); x <= Mth.floor(moved.maxX - EDGE_BIAS); x++) {
                for (int y = Mth.floor(moved.minY); y <= Mth.floor(moved.maxY - EDGE_BIAS); y++) {
                    for (int z = Mth.floor(moved.minZ); z <= Mth.floor(moved.maxZ - EDGE_BIAS); z++) {
                        cursor.set(x, y, z);
                        if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                            cells.add(cursor.immutable());
                        }
                    }
                }
            }
            if (!cells.isEmpty()) {
                return new Obstruction(d, cells);
            }
        }
        return null;
    }

    private static boolean withinLeash(Mob mob, BlockPos pos) {
        CompoundTag data = mob.getPersistentData();
        BlockPos home = data.contains(HOME_TAG) ? BlockPos.of(data.getLong(HOME_TAG)) : mob.blockPosition();
        return pos.distSqr(home) <= LEASH_RADIUS * LEASH_RADIUS;
    }

    /** Whether {@code caster} already has a beam burning. */
    public static boolean hasActiveBeam(Mob caster) {
        return !caster.level().getEntitiesOfClass(AnnihilationRay.class,
                caster.getBoundingBox().inflate(4.0D),
                beam -> beam.isAlive() && beam.entityData.get(DATA_OWNER) == caster.getId()).isEmpty();
    }

    /** Middle of the caster's body. */
    public static Vec3 centreOf(Entity owner, float partialTick) {
        return owner.getPosition(partialTick).add(0.0D, owner.getBbHeight() * 0.5D, 0.0D);
    }

    /**
     * Where the beam leaves the caster: on the surface of its body, facing along {@code direction}.
     * Beholder-kin are round, so "the eye" is simply the point of the body nearest what it looks at.
     */
    public static Vec3 originAlong(Entity owner, Vec3 direction, float partialTick) {
        return centreOf(owner, partialTick).add(direction.scale(owner.getBbWidth() * 0.5D));
    }

    /**
     * The renderer's origin: toward the synched end point rather than along the server's aim, which
     * the client never sees. Same point, derived from what is actually synched.
     */
    public static Vec3 originToward(Entity owner, Vec3 end, float partialTick) {
        Vec3 to = end.subtract(centreOf(owner, partialTick));
        return to.lengthSqr() < 1.0E-4D
                ? centreOf(owner, partialTick)
                : originAlong(owner, to.normalize(), partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            sparkAtEnd();
            return;
        }
        if (!(getOwner() instanceof Mob caster) || !caster.isAlive() || ++this.age > LIFETIME_TICKS) {
            discard();
            return;
        }
        if (this.damageCooldown > 0) {
            this.damageCooldown--;
        }
        steer(caster);

        Vec3 origin = originAlong(caster, this.aim, 1.0F);
        setPos(origin.x, origin.y, origin.z);

        Vec3 end;
        BlockPos struck = null;
        Obstruction ahead = null;
        if (this.mode.bores) {
            ahead = obstructionAhead(caster, this.aim, MAX_RANGE);
            // Drawn to where the front of the body would meet the obstruction: the face being cut.
            end = ahead == null
                    ? origin.add(this.aim.scale(MAX_RANGE))
                    : centreOf(caster, 1.0F).add(this.aim.scale(ahead.distance() + caster.getBbWidth() * 0.5D));
        } else {
            // COLLIDER: the line passes grass, torches and cobweb rather than stopping on them.
            // Fluids too -- a beam that stopped at the surface of a puddle would read as a bug.
            Vec3 far = origin.add(this.aim.scale(MAX_RANGE));
            BlockHitResult blockHit = this.level().clip(new ClipContext(
                    origin, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            end = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getLocation();
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                struck = blockHit.getBlockPos();
            }
        }

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this.level(), this, origin, end,
                new AABB(origin, end).inflate(1.0D), entity -> canHurt(caster, entity));
        if (entityHit != null) {
            // Something it may hurt is in front of the wall: the beam stops on it, and the wall
            // behind it is not burning.
            end = entityHit.getLocation();
            this.burning = null;
            this.burnTicks = 0;
            if (this.damageCooldown == 0) {
                entityHit.getEntity().hurt(damageSource(caster), DAMAGE);
                this.damageCooldown = DAMAGE_INTERVAL_TICKS;
            }
        } else if (ahead != null) {
            bore(caster, ahead.cells());
        } else if (struck != null) {
            burn(caster, struck);
        }
        setEnd(end);
    }

    /** STRIKE beams follow the caster's target, at a capped rate; boring beams hold their aim. */
    private void steer(Mob caster) {
        if (this.mode != Mode.STRIKE) {
            return;
        }
        LivingEntity target = caster.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        this.aim = BeamMath.rotateToward(this.aim, aimAt(caster, target), MAX_TURN_RADIANS);
    }

    /** A STRIKE beam: holds on the one block on its line, and burns it once it has held long enough. */
    private void burn(Mob caster, BlockPos pos) {
        if (!mayBurn()) {
            return;
        }
        if (!pos.equals(this.burning)) {
            this.burning = pos;
            this.burnTicks = 0;
        }
        if (++this.burnTicks < BURN_TICKS_PER_BLOCK) {
            return;
        }
        this.burnTicks = 0;
        // If it cannot go, the beam ends here for the rest of its life.
        if (annihilate(caster, pos)) {
            this.burning = null;
        }
    }

    /**
     * A boring beam: burns every block of the slice the caster's body would run into. Anything in
     * the slice it cannot burn stays, and the beam holds on it &mdash; it does not cut around it.
     */
    private void bore(Mob caster, List<BlockPos> cells) {
        if (!mayBurn() || ++this.burnTicks < BURN_TICKS_PER_BLOCK) {
            return;
        }
        this.burnTicks = 0;
        for (BlockPos cell : cells) {
            annihilate(caster, cell);
        }
    }

    private boolean mayBurn() {
        return this.power > 0.0F && this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private boolean annihilate(Mob caster, BlockPos pos) {
        Level level = this.level();
        if (!canAnnihilate(caster, pos, this.mode.unleashed)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        // null explosion, as gmm's DisintegrateSpell does: resistance is a property of the block,
        // and there is no Explosion here to hand it.
        float resistance = state.getExplosionResistance(level, pos, null);
        if (resistance > this.power) {
            return false;
        }
        if (!ForgeEventFactory.onEntityDestroyBlock(caster, pos, state)) {
            return false;
        }
        this.power -= resistance;
        // no drops: it is ANNIHILATED. destroyBlock still plays the break particles and sound.
        level.destroyBlock(pos, false, caster);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, this.getSoundSource(),
                0.4F, 2.0F + this.random.nextFloat() * 0.4F);
        return true;
    }

    private static boolean canHurt(Mob caster, Entity entity) {
        return entity != caster
                && entity.isAlive()
                && entity instanceof LivingEntity
                && EntitySelector.NO_SPECTATORS.test(entity)
                && (entity instanceof Player || entity == caster.getTarget())
                && !caster.isAlliedTo(entity);
    }

    private DamageSource damageSource(Mob caster) {
        return new DamageSource(this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE), this, caster);
    }

    /** Client only: the burn point spits sparks and smoke. Nothing is sent for this. */
    private void sparkAtEnd() {
        Vec3 end = getEnd();
        if (this.random.nextInt(2) == 0) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                    this.random.nextGaussian() * 0.08D, this.random.nextGaussian() * 0.08D,
                    this.random.nextGaussian() * 0.08D);
        }
        if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0.0D, 0.03D, 0.0D);
        }
    }

    public Entity getOwner() {
        int id = this.entityData.get(DATA_OWNER);
        return id < 0 ? null : this.level().getEntity(id);
    }

    public Vec3 getEnd() {
        Vector3f end = this.entityData.get(DATA_END);
        return new Vec3(end.x, end.y, end.z);
    }

    private void setEnd(Vec3 end) {
        this.entityData.set(DATA_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
    }

    /**
     * The whole beam, not the speck of an entity at its origin. Without this the beam vanishes the
     * moment the caster's eye leaves the screen, however much of it is still in view.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        Vec3 end = getEnd();
        return new AABB(getX(), getY(), getZ(), end.x, end.y, end.z).inflate(1.0D);
    }

    /** Vanilla scales render distance by box size, which for a 0.25 box is 16 blocks. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 128.0D * 128.0D;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER, -1);
        this.entityData.define(DATA_END, new Vector3f());
    }

    /** A beam is a moment, not a thing: a chunk unloading mid-burn ends it. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
