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
public class MobSetSpawnerBlock extends AbstractProximityBlock {

    /**
     * How far away a player triggers the spawn, in blocks. Matches the room scale Dungeons2 builds
     * at: rooms run 5&ndash;13 across, so this fires as the player enters the room rather than as
     * they step onto the cell.
     */
    private static final double DEFAULT_PROXIMITY = 8.0D;

    public MobSetSpawnerBlock(Properties properties) {
        super(properties);
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
