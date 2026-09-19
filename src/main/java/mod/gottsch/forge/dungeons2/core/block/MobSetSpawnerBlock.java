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
package mod.gottsch.forge.dungeons2.core.block;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.entity.DungeonSpawnerBlockEntity;
import mod.gottsch.forge.dungeons2.core.block.entity.DungeonsBlockEntities;
import mod.gottsch.forge.gottschcore.block.AbstractProximityBlock;
import mod.gottsch.forge.gottschcore.block.entity.ProximityMobSetSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * An <strong>invisible</strong> block whose block entity spawns a datapack-defined mob set when a
 * player comes near. Backlog #10's Dungeons2-side implementation of the {@code d2:spawner} marker.
 *
 * <h2>Why a block at all, rather than a vanilla spawner</h2>
 * <p>GottschCore's {@link ProximityMobSetSpawnerBlockEntity} is proximity-triggered and draws from
 * a named {@code mob_sets} entry, so the mobs a dungeon spawns are datapack content rather than a
 * constant in code &mdash; the same direction every other Dungeons2 knob has moved. A vanilla
 * {@code minecraft:mob_spawner} cannot express a weighted set and would put the mob choice back in
 * Java.</p>
 *
 * <h2>GottschCore registers none of this, by design</h2>
 * <p>Like the Monster Manual, it is a library: {@code ProximityMobSetSpawnerBlockEntity} takes a
 * {@code Supplier<BlockEntityType<?>>} precisely so each consuming mod supplies its own registered
 * type, and {@code MobSetDataHandler}'s javadoc says outright that a consumer must register the
 * reload listener. So this block, its block entity type, and the listener are all Dungeons2's to
 * own &mdash; see {@code DungeonsBlockEntities} and {@code LoadMobSetDataEvent}.</p>
 *
 * <p><strong>The supplier form of the constructor is deliberate.</strong> The obvious version calls
 * {@code DungeonsBlockEntities.MOB_SET_SPAWNER.get()} directly in {@link #newBlockEntity}; Village
 * Dungeons does that and has left a {@code // TODO this is incorrect!} on the line. Passing the
 * supplier keeps the block-entity-type lookup lazy, which is what stops a block placed during
 * worldgen from resolving a {@code RegistryObject} that is still being populated.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
public class MobSetSpawnerBlock extends AbstractProximityBlock implements SimpleWaterloggedBlock {

    /**
     * How far away a player triggers the spawn, in blocks. Matches the room scale Dungeons2 builds
     * at: rooms run 5&ndash;13 across, so this fires as the player enters the room rather than as
     * they step onto the cell.
     */
    private static final double DEFAULT_PROXIMITY = 12.0D;

    /**
     * <h2>Why this block is waterloggable, which is not a cosmetic choice</h2>
     * <p><strong>Without it, water DELETES this block, block entity and all.</strong>
     * {@code FlowingFluid.canHoldFluid} ends in {@code return !state.blocksMotion()}, and
     * {@code blocksMotion()} is {@code legacySolid}, which {@code calculateSolid()} derives from
     * the collision shape &mdash; empty here, because of the {@code noCollission()} in
     * {@link #properties} and the empty {@link #getShape}. So a spawner in a flooded room passes
     * {@code canSpreadTo}, and {@code spreadTo} takes the {@code else} branch:
     * {@code beforeDestroyingBlock} then {@code setBlock(pos, water)}. Implementing
     * {@link SimpleWaterloggedBlock} sends it down the {@code LiquidBlockContainer} branch instead,
     * where {@code placeLiquid} flips {@code WATERLOGGED} and <em>leaves the block standing</em>.
     *
     * <p>Found 2026-09-16, and it is worth recording how invisible it was. The sewer rooms author
     * their {@code classic_water} markers inside the channel. Generation was provably correct
     * &mdash; the marker converted with the right mob set, and {@code newBlockEntity OK} logged
     * server-side at the exact position &mdash; and then the spawner simply never ticked: no
     * {@code [D2-PROBE]} line for it while a vermin spawner seven blocks away in dry air probed and
     * spawned normally. The cell read as plain water afterwards, which is <em>also</em> what
     * {@code selfDestruct()} leaves behind, so a washed-out spawner and a spawner that had fired
     * were indistinguishable in the world and in the save. Every land mob set worked, because a
     * spawner standing in air is never asked to hold a fluid.
     *
     * <p>{@code forceSolidOn()} would also stop the destruction and was rejected: it makes an
     * invisible cell a pathfinding wall and a dry plug in the middle of the channel, so the one mob
     * the spawner exists to place could not swim through its own spawner.
     *
     * <p>A cell water never flows INTO stays {@code waterlogged=false} and is a one-block dry
     * pocket. That is pre-existing behaviour and harmless &mdash; a spawner in exactly that state
     * is the one that was observed working &mdash; so no {@code updateShape} hook tries to correct
     * it. If one is ever added, note that it must schedule through {@code LevelAccessor} and never
     * cast it to {@code Level}: that cast is what killed chunk generation in dungeonblocks.
     */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public MobSetSpawnerBlock(Properties properties) {
        super(properties);
        // Explicitly false. stateDefinition.any() resolves every unnamed boolean to TRUE, so a
        // default state that did not name this would ship waterlogged -- the same trap that made a
        // dungeonblocks lantern flood its own pit.
        registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.state.BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    /**
     * The water the cell is holding, so the cell still reads as water to everything that asks
     * &mdash; which includes {@code NaturalSpawner.canSpawnAtBody}'s {@code IN_WATER} test, the one
     * that decides whether a fish may be placed here.
     */
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    /**
     * The shipped block properties, as a factory so a test can assert on the real ones without a
     * populated Forge registry &mdash; {@code DungeonsBlocks}' {@code RegistryObject} cannot be
     * resolved headlessly.
     *
     * <p><strong>No {@code air()}</strong> &mdash; kept off deliberately, though it was never the
     * bug it was once blamed for. See {@link #getShape}.</p>
     */
    public static Properties properties() {
        return Properties.of().noCollission().noLootTable().noOcclusion().instabreak();
    }

    /**
     * <p><strong>The try/catch is a diagnostic, not defensive coding.</strong> If this throws, the
     * block state has <em>already</em> been written by the caller, so the world gets the block with
     * no block entity while vanilla's command dispatcher swallows the exception &mdash; the same
     * swallowing {@code DungeonStructure} documents. It has never actually fired; it was added while
     * hunting a fault that turned out to be in {@code SpawnUtil}, and is kept because that failure
     * mode would otherwise be completely silent.</p>
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        try {
            ProximityMobSetSpawnerBlockEntity blockEntity = new DungeonSpawnerBlockEntity(
                    DungeonsBlockEntities::mobSetSpawnerType, pos, state);
            blockEntity.setProximity(DEFAULT_PROXIMITY);
            // Once per block entity created, so cheap. Kept because "is the world even asking this
            // block for an entity" was the question that finally narrowed #10, and a spawner that
            // works looks exactly like one that was never placed.
            Dungeons.LOGGER.debug("[D2-SPAWNER] newBlockEntity OK at {}", pos);
            return blockEntity;
        } catch (RuntimeException e) {
            Dungeons.LOGGER.error("[D2-SPAWNER] newBlockEntity FAILED at {} -- the block will exist "
                    + "with no block entity and never tick", pos, e);
            throw e;
        }
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        // Server only: the spawn decision must happen once, authoritatively.
        //
        // Kept deliberately bare. A first-tick diagnostic lived here while #10 was being debugged
        // and was removed once it had done its job: it called pos.immutable() on every tick of
        // every spawner to key a seen-set, which is an allocation on a ticking path to serve a
        // question nobody is asking any more. If it is ever needed again, guard the whole block on
        // isDebugEnabled() rather than allocating first and deciding second.
        return level.isClientSide() ? null : (lvl, pos, blockState, be) -> {
            if (be instanceof ProximityMobSetSpawnerBlockEntity spawner) {
                spawner.tickServer();
            }
        };
    }

    /**
     * Empty outline, so the block cannot be looked at, highlighted or broken. With
     * {@code noCollission} on the properties and {@code BaseEntityBlock}'s {@code INVISIBLE} render
     * shape, that is the whole of "behaves like air to the player".
     *
     * <p><strong>This block was NOT the bug, and the record should say so.</strong> The spawner
     * appeared to have no block entity ({@code /data get block} answered "The target block is not a
     * Block Entity"), which was read as the block being at fault &mdash; first its {@code air()}
     * property, then its {@code isAir} override, both copied from Village Dungeons. Neither was the
     * cause. The real fault was in GottschCore's {@code SpawnUtil.spawnMob}, which treated Forge's
     * normal {@code onFinalizeSpawn} return of {@code null} as a cancelled spawn, discarded the mob
     * on every attempt, and returned empty &mdash; after which {@code execute()} called
     * {@code selfDestruct()} anyway, deleting the block and its entity. The cell really was air by
     * the time anyone looked at it; the query was telling the truth about the wrong moment.
     *
     * <p>The {@code air()} removal is <strong>kept on its own merits, not as a fix</strong>: a block
     * that hosts a block entity has no business being marked air, and invisibility never needed it
     * &mdash; {@code BaseEntityBlock} renders {@code INVISIBLE}, {@code noCollission} lets the
     * player through, and this empty outline stops it being targeted. Reverting it would very
     * probably also work.</p>
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return Shapes.empty();
    }
}
