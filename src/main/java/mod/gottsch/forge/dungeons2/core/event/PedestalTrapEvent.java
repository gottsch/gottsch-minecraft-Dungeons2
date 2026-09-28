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

import mod.gottsch.forge.dungeonblocks.core.block.CrumblingFloorBlock;
import mod.gottsch.forge.dungeonblocks.core.block.PedestalBlock;
import mod.gottsch.forge.dungeonblocks.core.blockentity.PedestalBlockEntity;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomPedestalGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.CrumblingFloors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Take the prize, lose the floor: a pedestal armed over a hidden moat springs it when its item is
 * taken or the pedestal is broken.
 *
 * <h2>Why an event and not redstone</h2>
 * <p>A pedestal speaks only to a comparator, and a comparator has to stand beside it in plain view
 * &mdash; and the floor that falls cannot be crumbling floor from the start, because that falls to
 * the first foot on it, long before the prize. So the ring stays ordinary floor, the trap rides on
 * the pedestal's {@code ForgeData} ({@code RoomPedestalGenerator}), and this turns the ring into
 * DungeonBlocks' crumbling floor at the moment of the theft and sets the inner ring off. The collapse
 * spreads outward through the rest on its own, with its own shudder, dust and sound.</p>
 *
 * <h2>One shot</h2>
 * <p>The flag is removed as it fires, so putting the prize back and taking it again does nothing.</p>
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PedestalTrapEvent {

    /** Ticks before the inner ring starts to shake: the item is in hand, then the floor goes. */
    static final int FIRST_TREMOR = 4;

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        // PedestalBlock#use takes the item only with an empty main hand; and a sneaking player with
        // anything in the other hand never reaches use() at all.
        if (!player.getMainHandItem().isEmpty()
                || (player.isSecondaryUseActive() && !player.getOffhandItem().isEmpty())) {
            return;
        }
        BlockPos pos = event.getPos();
        if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal
                && !pedestal.getItem().isEmpty()) {
            spring(level, pos, pedestal, "taken by " + player.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide
                || !(event.getState().getBlock() instanceof PedestalBlock)) {
            return;
        }
        if (level.getBlockEntity(event.getPos()) instanceof PedestalBlockEntity pedestal) {
            spring(level, event.getPos(), pedestal, "pedestal broken");
        }
    }

    /** The armed radius, or 0 when this pedestal carries no trap (or has already fired). */
    static int trapRadius(PedestalBlockEntity pedestal) {
        CompoundTag ours = pedestal.getPersistentData().getCompound(RoomPedestalGenerator.TRAP_NAMESPACE);
        return ours.contains(RoomPedestalGenerator.TRAP_RADIUS, Tag.TAG_ANY_NUMERIC)
                ? ours.getInt(RoomPedestalGenerator.TRAP_RADIUS) : 0;
    }

    static void spring(Level level, BlockPos pedestalPos, PedestalBlockEntity pedestal, String why) {
        int radius = trapRadius(pedestal);
        if (radius <= 0) {
            return;
        }
        pedestal.getPersistentData().getCompound(RoomPedestalGenerator.TRAP_NAMESPACE)
                .remove(RoomPedestalGenerator.TRAP_RADIUS);
        pedestal.setChanged();

        List<BlockPos> inner = new ArrayList<>();
        BlockPos floor = pedestalPos.below();
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : ring(floor, radius)) {
            // Only floor over the moat: a ring cell with SOLID ground under it is not part of the
            // trap. Not "air under it" -- weathering hangs cobwebs, lichen and roots on the
            // underside of a floor over a void, and a cell with a web under it is still over the
            // moat. (Seen in game 2026-09-28: those cells were left standing.)
            BlockPos below = cell.below();
            if (level.getBlockState(below).isCollisionShapeFullBlock(level, below)) {
                continue;
            }
            if (!level.getBlockState(cell).isAir()) {
                cells.add(cell);
            }
        }
        // By the time a prize is taken, weathering has aged the floor into stones that have no
        // crumbling version (gravel, rubble, square brick...). Those take the ring's commonest
        // crumbling stone, so the whole ring shakes and falls together rather than some of it
        // vanishing (Seen in game 2026-09-28).
        Block fallback = commonestCrumbling(level, cells);
        for (BlockPos cell : cells) {
            BlockState state = level.getBlockState(cell);
            if (!(state.getBlock() instanceof CrumblingFloorBlock)) {
                Block crumbling = crumblingFor(state).orElse(fallback);
                level.setBlock(cell, crumbling.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (cell.distManhattan(floor) == 1 || isDiagonalNeighbour(cell, floor)) {
                inner.add(cell);
            }
        }
        // A tick on a still crumbling block sets it off; it passes the tremor to every crumbling
        // block touching it, so the inner ring is enough to bring the whole moat's lid down.
        for (BlockPos cell : inner) {
            level.scheduleTick(cell, level.getBlockState(cell).getBlock(), FIRST_TREMOR);
        }
        //   grep "D2-TRAP" run/logs/dungeons2.log
        Dungeons.LOGGER.info("[D2-TRAP] pedestal at {} sprang its moat (radius {}): {}",
                pedestalPos.toShortString(), radius, why);
    }

    /** Every cell within {@code radius} of {@code centre} on its plane, the centre excluded. */
    static List<BlockPos> ring(BlockPos centre, int radius) {
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx != 0 || dz != 0) {
                    cells.add(centre.offset(dx, 0, dz));
                }
            }
        }
        return cells;
    }

    private static boolean isDiagonalNeighbour(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) == 1 && Math.abs(a.getZ() - b.getZ()) == 1;
    }

    /**
     * The crumbling block the most ring cells map to, or crumbling stone bricks when none do. Ties
     * go to the first counted, and the ring is walked in a fixed order.
     */
    private static Block commonestCrumbling(Level level, List<BlockPos> cells) {
        java.util.Map<Block, Integer> counts = new java.util.LinkedHashMap<>();
        for (BlockPos cell : cells) {
            BlockState state = level.getBlockState(cell);
            Optional<Block> crumbling = state.getBlock() instanceof CrumblingFloorBlock
                    ? Optional.of(state.getBlock()) : crumblingFor(state);
            crumbling.ifPresent(block -> counts.merge(block, 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElseGet(() -> ForgeRegistries.BLOCKS.getValue(
                        new ResourceLocation("dungeonblocks:crumbling_stone_bricks")));
    }

    private static Optional<Block> crumblingFor(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return Optional.ofNullable(id)
                .flatMap(key -> CrumblingFloors.forFloor(key.toString()))
                .map(ResourceLocation::new)
                .filter(ForgeRegistries.BLOCKS::containsKey)
                .map(ForgeRegistries.BLOCKS::getValue);
    }
}
