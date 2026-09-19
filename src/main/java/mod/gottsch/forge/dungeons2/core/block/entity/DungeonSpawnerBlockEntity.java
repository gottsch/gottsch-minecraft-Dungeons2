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
package mod.gottsch.forge.dungeons2.core.block.entity;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.gottschcore.block.entity.ProximityMobSetSpawnerBlockEntity;
import mod.gottsch.forge.gottschcore.mobset.MobSetData;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import mod.gottsch.forge.gottschcore.mobset.WeightedMob;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.size.IntegerRange;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import mod.gottsch.forge.gottschcore.util.SpawnUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * GottschCore's proximity mob-set spawner plus two things Dungeons2 knows that a generic spawner
 * cannot: <strong>which floor of the dungeon it is on</strong>, and <strong>which boss the planner
 * chose</strong>.
 *
 * <h2>Why a subclass and not just another tag field</h2>
 * <p>{@code BlockEntityData} can carry any key, and {@code DungeonPiece.applyBlockEntity} will load
 * it &mdash; but a key the block entity has no field for is <strong>silently dropped at the next
 * save</strong>, because {@code saveAdditional} writes fields, not the tag it was loaded from. The
 * floor index would survive generation, survive until the chunk unloaded, and then be gone. A
 * spawner that fires on the player's first visit would look correct in every test and lose its depth
 * on a revisit &mdash; the exact shape of invisible failure this whole feature keeps producing.</p>
 *
 * <p>Subclassing here rather than adding the field to GottschCore keeps the change inside this repo.
 * Backlog #10's open half is a GottschCore base class designed against <em>two</em> consumers; a
 * field added now, for one consumer, would prejudge that design.</p>
 *
 * <h2>What floorIndex is for</h2>
 * <p>Nothing reads it yet. It is stored from generation so that when the Stronger Mobs Below
 * integration lands, dungeons generated before it still carry the depth their mobs should scale by.
 * Note that SMB's own axis is <strong>world Y</strong> ({@code EchelonConfigsHolder.Config
 * .getDifficulty(Integer y)} is an interval tree over Y), which is a different thing: a dungeon
 * under a mountain has its floor 3 higher than a ravine dungeon's floor 0. This field is the
 * dungeon-relative ordinal, 0 at the entrance.</p>
 *
 * <h2>The pinned boss</h2>
 * <p>Since 2026-09-10 a dungeon's boss is drawn at PLANNING, so that loot placed long before the
 * player arrives can depend on who it is. The draw reaches the boss spawner as {@link #PINNED_MOB},
 * written <strong>beside</strong> {@code mobSetName}, never in place of it (Mark: a set assigned to
 * a spawner in data must stay the spawner's set). The set keeps the final word: the pin is honoured
 * only while that set still offers the mob, so a datapack that later takes the Minotaur off the
 * medium menu wins over a Minotaur pinned into an old world, and the spawner draws from the set as
 * it always did.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public class DungeonSpawnerBlockEntity extends ProximityMobSetSpawnerBlockEntity {

    public static final String FLOOR_INDEX = "floorIndex";
    /** Marker/BE NBT, camelCase like its neighbours. See the class javadoc. */
    public static final String PINNED_MOB = "pinnedMob";

    /** Unset. Distinguishable from floor 0, which is a real and common answer. */
    public static final int UNKNOWN_FLOOR = -1;

    private int floorIndex = UNKNOWN_FLOOR;
    /** The planner's boss, or null to draw from the set. */
    private ResourceLocation pinnedMob;

    public DungeonSpawnerBlockEntity(Supplier<BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(FLOOR_INDEX)) {
            this.floorIndex = tag.getInt(FLOOR_INDEX);
        }
        if (tag.contains(PINNED_MOB, Tag.TAG_STRING)) {
            this.pinnedMob = ResourceLocation.tryParse(tag.getString(PINNED_MOB));
        }
    }

    /**
     * <p><strong>The null guard is not defensive coding.</strong> The parent's
     * {@code saveAdditional} reads {@code getMobSizeRange().getMin()} unguarded and rethrows, so it
     * throws {@code NullPointerException} for any spawner whose range has not been set &mdash; which
     * is <em>every</em> freshly created one, since the range only arrives when a tag is loaded. That
     * is not a hypothetical: {@code DungeonPiece.applyBlockEntity} saves the entity before applying
     * data to it, and before this guard that save threw and the spawner ended up with none of its
     * configuration. Seeding the parent's own documented default (1..1, from its
     * {@code defaultMobSpawnerSettings}) makes the entity saveable at any point in its life, which
     * is what a block entity is supposed to be.</p>
     */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        if (getMobSizeRange() == null) {
            setMobSizeRange(new IntegerRange(1, 1));
        }
        super.saveAdditional(tag);
        tag.putInt(FLOOR_INDEX, floorIndex);
        if (pinnedMob != null) {
            tag.putString(PINNED_MOB, pinnedMob.toString());
        }
    }

    /**
     * Spawns the pinned boss when there is one its set still offers; otherwise exactly the parent's
     * draw.
     *
     * <p>The tail repeats the parent's {@code selfDestruct}, which is private: mark dead, clear the
     * cell, drop the block entity. Same three steps in the same order, so a pinned spawner leaves
     * the same trace as a drawn one.</p>
     */
    @Override
    public void execute(Level world, RandomSource random, ICoords blockCoords, ICoords playerCoords) {
        // BEFORE the branch and before super, because both paths end in selfDestruct() and this
        // is the last moment the spawner's configuration still exists. See #probeSpawn.
        if (!world.isClientSide()) {
            probeSpawn((ServerLevel) world, blockCoords);
        }
        Optional<EntityType<?>> pinned = world.isClientSide() ? Optional.empty() : honouredPin();
        if (pinned.isEmpty()) {
            super.execute(world, random, blockCoords, playerCoords);
            // AFTER, and on the ordinary route only -- the pinned route below is a boss spawner,
            // which has never been in question. See #censusAfterSpawn.
            if (!world.isClientSide()) {
                censusAfterSpawn((ServerLevel) world, blockCoords);
            }
            return;
        }
        ServerLevel level = (ServerLevel) world;
        IntegerRange range = getMobSizeRange() != null ? getMobSizeRange() : new IntegerRange(1, 1);
        int count = RandomHelper.randomInt(random, range.getMin(), range.getMax());
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) pinned.get();
        for (int i = 0; i < count; i++) {
            SpawnUtil.spawnAndAddMob(level, random, type, blockCoords);
        }
        setDead(true);
        level.setBlock(getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        level.removeBlockEntity(getBlockPos());
    }

    /**
     * The 125 cells {@code SpawnUtil.spawnMob} can reach: its three offsets are each
     * {@code Mth.nextInt(1,2) * Mth.nextInt(-1,1)}, so each one lands in {@code [-2, 2]}.
     */
    private static final int PROBE_RADIUS = 2;

    /**
     * Says, in one log line per mob, why this spawner is about to spawn what it spawns &mdash; or
     * nothing at all.
     *
     * <h2>Why the probe is HERE and not in GottschCore</h2>
     * <p>A proximity spawner that spawns nothing is <strong>indistinguishable after the fact from
     * one that worked</strong>, and that is structural rather than an oversight:
     * {@code ProximityMobSetSpawnerBlockEntity.execute} calls {@code selfDestruct()} on the way out
     * whether or not a mob was added, so the cell ends up air &mdash; water, in a flooded room
     * &mdash; either way. And the step that actually fails, {@code SpawnUtil.spawnMob}, has no
     * logging of its own: it returns {@code Optional.empty()} after twenty silent attempts. Turning
     * {@code gottschcore-common.toml} up to {@code debug} buys only "proximity met" and
     * "self-destructing", which are the two facts already in evidence by then.
     *
     * <p>So the diagnosis has to be made on this side, from the state the spawner still holds.
     * Written 2026-09-16 for the sewer rooms: their {@code classic_water} markers convert correctly
     * and their spawners fire (both confirmed in {@code dungeons2.log}), the channels hold source
     * water, and yet no {@code dungeons2:alligator_gar} exists anywhere in the save while every
     * land mob set in the same world spawns normally.
     *
     * <h2>What each field is for</h2>
     * <p>The chain has four places it can break, and each gets a field:
     * <ul>
     *   <li>{@code set=} / {@code registered=} &mdash; whether {@code MobSetDataRegistry} holds the
     *       name the marker wrote. A miss makes {@code execute}'s {@code ifPresent} a no-op, and
     *       nothing anywhere says so.</li>
     *   <li>{@code range=} &mdash; the draw count. A {@code (0..0)} range spawns nothing and looks
     *       identical to everything else that spawns nothing.</li>
     *   <li>{@code type=} &mdash; whether {@code EntityType.byString} resolves the mob id at all.</li>
     *   <li>{@code placement=} / {@code okCells=} &mdash; the real suspect. {@code SpawnUtil} gates
     *       every attempt on {@code NaturalSpawner.isSpawnPositionOk}, whose answer depends entirely
     *       on the placement type: {@code IN_WATER} wants water AT the cell, while
     *       {@code ON_GROUND} rejects <em>any</em> cell whose fluid is non-empty (see
     *       {@code isValidEmptySpawnBlock}). So a water mob carrying a ground placement fails all
     *       125 cells while a land mob never notices the difference. {@code okCells=0} is the whole
     *       diagnosis in one number; anything above zero moves the fault downstream of placement,
     *       to the mob's own {@code checkSpawnObstruction} or to it being removed after it
     *       spawns.</li>
     * </ul>
     *
     * <h2>DEBUG, and guarded by {@code isDebugEnabled()} rather than merely logged at DEBUG</h2>
     * <p>It ran at INFO while it was being used, on the reasoning that a probe nobody can see is not
     * a probe. It is not needed at that volume any more &mdash; it fires for every spawner in every
     * dungeon &mdash; so it is DEBUG now, which is still visible in the development profile
     * ({@code [logging] level} in {@code dungeons2-common.toml} ships at {@code debug}) and silent
     * for a player.
     *
     * <p><strong>The guard is the part that matters.</strong> Dropping the level alone would keep
     * paying for the work: {@code LOGGER.debug(...)} evaluates its arguments before it decides to
     * discard the line, and those arguments are a 125-cell scan per mob, an entity constructed and
     * thrown away in {@link #dryRunGates}, and &mdash; in {@link #censusAfterSpawn} &mdash; an AABB
     * entity query. Cheap once, pointless once nobody is reading it. So both methods return
     * immediately when DEBUG is off, which is the same "guard the whole block rather than allocating
     * first and deciding second" note {@code MobSetSpawnerBlock#getTicker} left behind when its own
     * diagnostic was retired.
     *
     * <p>Positions go through {@code toShortString()}. The neighbouring {@code newBlockEntity} line
     * does not, and its {@code BlockPos&#123;x=.., y=.., z=..&#125;} has to be retyped by hand
     * before it can be pasted into a command.
     */
    private void probeSpawn(ServerLevel level, ICoords blockCoords) {
        if (!Dungeons.LOGGER.isDebugEnabled()) {
            return;
        }
        BlockPos pos = blockCoords.toPos();
        ResourceLocation setName = getMobSetName();
        Optional<MobSetData> data = setName == null
                ? Optional.empty()
                : MobSetDataRegistry.get(setName);
        IntegerRange range = getMobSizeRange();
        Dungeons.LOGGER.debug("[D2-PROBE] spawner at {} set={} registered={} range={} pinned={}"
                        + " cellFluid={}",
                pos.toShortString(), setName, data.isPresent(),
                range == null ? "null" : "(" + range.getMin() + ".." + range.getMax() + ")",
                pinnedMob, ForgeRegistries.FLUIDS.getKey(level.getFluidState(pos).getType()));

        if (data.isEmpty()) {
            // Nothing more to say: execute()'s ifPresent will not run, so the mob list, the
            // placement and the cells are all moot. This line IS the answer when it happens.
            return;
        }
        for (WeightedMob mob : data.get().getMobs()) {
            Optional<EntityType<?>> type = EntityType.byString(mob.id().toString());
            if (type.isEmpty()) {
                Dungeons.LOGGER.debug("[D2-PROBE]   mob={} weight={} type=UNRESOLVED",
                        mob.id(), mob.weight());
                continue;
            }
            SpawnPlacements.Type placement = SpawnPlacements.getPlacementType(type.get());
            // Walked once, reporting both the count and a witness cell.
            BlockPos witness = null;
            int ok = 0;
            for (int dx = -PROBE_RADIUS; dx <= PROBE_RADIUS; dx++) {
                for (int dy = -PROBE_RADIUS; dy <= PROBE_RADIUS; dy++) {
                    for (int dz = -PROBE_RADIUS; dz <= PROBE_RADIUS; dz++) {
                        BlockPos candidate = pos.offset(dx, dy, dz);
                        if (NaturalSpawner.isSpawnPositionOk(placement, level, candidate,
                                type.get())) {
                            ok++;
                            if (ok == 1) {
                                witness = candidate;
                            }
                        }
                    }
                }
            }
            Dungeons.LOGGER.debug("[D2-PROBE]   mob={} weight={} placement={} okCells={}/{}"
                            + " firstOk={} gates={}",
                    mob.id(), mob.weight(), placement, ok, cellCount(),
                    witness == null ? "none" : witness.toShortString(),
                    dryRunGates(level, type.get(), witness));
        }
    }

    /**
     * Dry-runs the two gates {@code SpawnUtil.spawnMob} applies <em>after</em>
     * {@code isSpawnPositionOk}, at the one cell we know passed it.
     *
     * <p>Added 2026-09-16, after the first cut of this probe ruled the position gate out: the gar
     * reports {@code placement=IN_WATER} with 47 of 125 cells valid, nearly three times what the
     * land mobs that spawn correctly get, and still no gar appears. So the fault is downstream, and
     * downstream is only three steps long &mdash; {@code create}, {@code checkSpawnObstruction},
     * {@code addFreshEntityWithPassengers}. This covers the first two; {@link #censusAfterSpawn}
     * covers the third.
     *
     * <p><strong>Creates an entity and throws it away.</strong> {@code EntityType.create} does not
     * add anything to the world, so the only cost is the {@code discard()} below &mdash; the same
     * create-test-discard {@code SpawnUtil} itself does on a failed attempt. It is worth the
     * allocation because {@code checkSpawnObstruction} is <em>polymorphic</em> and that is exactly
     * what makes it interesting here: {@code Mob}'s version refuses any position whose bounding box
     * holds liquid, and only {@code WaterAnimal} drops that clause. A water mob that did not extend
     * {@code WaterAnimal} would fail every cell in its own pool and this is the line that would
     * say so.
     *
     * @param cell a position {@code isSpawnPositionOk} already accepted, or null when none did
     * @return {@code create}/{@code obstruction} verdicts, or why they were not asked
     */
    private static String dryRunGates(ServerLevel level, EntityType<?> type, BlockPos cell) {
        if (cell == null) {
            return "notAsked(noOkCell)";
        }
        Entity probe = type.create(level);
        if (probe == null) {
            return "create=NULL";
        }
        try {
            probe.setPos(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D);
            if (!(probe instanceof Mob mob)) {
                return "create=ok obstruction=notAMob";
            }
            // The class is named because the answer comes from whichever override is in play, and
            // "which override" is the question.
            return "create=ok obstruction=" + mob.checkSpawnObstruction(level)
                    + " via=" + mob.getClass().getSimpleName();
        } finally {
            probe.discard();
        }
    }

    /**
     * What is actually standing near the spawner once GottschCore has had its turn.
     *
     * <p>This is the only step of the chain that cannot be predicted, only observed:
     * {@code SpawnUtil.spawnAndAddMob} adds the mob and returns, and
     * {@code ProximityMobSetSpawnerBlockEntity.execute} then calls {@code selfDestruct()}
     * <strong>whether or not anything was added</strong>. That is why a broken spawner leaves
     * exactly the same trace as a working one, and why "no mob in the save" could not be pinned on
     * any particular step.
     *
     * <p>Reports every entity in the box, not just the set's own mobs, and says whether each is
     * still alive. Three readings, all decisive:
     * <ul>
     *   <li>the set's mob <strong>present and alive</strong> &rarr; the spawn works and the fault
     *       is downstream of the world: rendering, tracking, or a removal that happens later.</li>
     *   <li><strong>present but removed</strong> &rarr; it spawned and was killed or discarded
     *       inside the same tick.</li>
     *   <li><strong>absent</strong> &rarr; {@code SpawnUtil} never added it, and with the gates
     *       above reported green the remaining suspect is {@code addFreshEntityWithPassengers}.</li>
     * </ul>
     *
     * <p>A 12-block box rather than the spawn radius of 2: a mob that spawned and swam off is still
     * a mob that spawned, and counting only the cells it was born in would report it missing.
     */
    private void censusAfterSpawn(ServerLevel level, ICoords blockCoords) {
        if (!Dungeons.LOGGER.isDebugEnabled()) {
            return;
        }
        BlockPos pos = blockCoords.toPos();
        AABB box = new AABB(pos).inflate(12.0D);
        List<Entity> nearby = level.getEntities((Entity) null, box, entity -> true);
        Map<String, Integer> alive = new LinkedHashMap<>();
        Map<String, Integer> dead = new LinkedHashMap<>();
        for (Entity entity : nearby) {
            String id = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
            (entity.isRemoved() ? dead : alive).merge(id, 1, Integer::sum);
        }
        Dungeons.LOGGER.debug("[D2-PROBE]   after spawn at {}: alive={} removed={}",
                pos.toShortString(), alive.isEmpty() ? "{}" : alive,
                dead.isEmpty() ? "{}" : dead);
    }

    /** How many cells {@code SpawnUtil} can reach, for the {@code okCells} denominator. */
    private static int cellCount() {
        int side = PROBE_RADIUS * 2 + 1;
        return side * side * side;
    }

    /** The pinned mob's type, if there is a pin and the assigned set still offers it. */
    private Optional<EntityType<?>> honouredPin() {
        if (pinnedMob == null) {
            return Optional.empty();
        }
        boolean offered = MobSetDataRegistry.get(getMobSetName())
                .map(data -> setOffers(data, pinnedMob))
                .orElse(false);
        if (!offered) {
            Dungeons.LOGGER.warn("[D2-SPAWNER] pinned boss {} is no longer offered by {} at {};"
                    + " drawing from the set instead", pinnedMob, getMobSetName(),
                    getBlockPos().toShortString());
            return Optional.empty();
        }
        return EntityType.byString(pinnedMob.toString());
    }

    /** Whether {@code data} still offers {@code mob} at a weight that can be drawn. */
    public static boolean setOffers(MobSetData data, ResourceLocation mob) {
        return data.getMobs().stream().anyMatch(entry -> entry.weight() > 0 && entry.id().equals(mob));
    }

    /** Which floor of the dungeon this spawner is on, 0 at the entrance; {@link #UNKNOWN_FLOOR} if unset. */
    public int getFloorIndex() {
        return floorIndex;
    }

    public void setFloorIndex(int floorIndex) {
        this.floorIndex = floorIndex;
    }

    public ResourceLocation getPinnedMob() {
        return pinnedMob;
    }
}
