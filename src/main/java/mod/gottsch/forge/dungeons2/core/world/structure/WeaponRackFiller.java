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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeonblocks.core.block.WeaponRackBlock;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.WeaponRackConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomFurnitureGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a weapon rack's plan into the weapons on it, as the rack is written.
 *
 * <p>{@code RoomFurnitureGenerator#rackData} decides, from the room's own stream, which slots hold
 * something, which loot table each rolls and the seed. This rolls those tables and writes the
 * results as the rack's {@code Items}, the list {@code WeaponRackBlockEntity} loads. The plan keys
 * themselves never reach the block entity.</p>
 *
 * <h2>Why now, and not when a player looks</h2>
 * <p>A chest can leave its table unrolled until it is opened, because nobody can see inside it. A
 * rack shows its weapons, so they have to exist when the chunk does. The seed is the plan's, so the
 * same dungeon racks the same weapons however its chunks are generated.</p>
 *
 * <p>A roll that yields no sword or axe leaves its slot empty: the rack would refuse anything else
 * from a player, so it does not get it from a table either.</p>
 *
 * @author Mark Gottschling on 10/22/2026
 */
public final class WeaponRackFiller {

    /** The rack's own key for its slots, as {@code ContainerHelper} writes it. */
    static final String ITEMS = "Items";
    private static final String SLOT = "Slot";

    /** Tables already reported missing; worldgen runs on many threads. */
    private static final Set<String> MISSING_TABLES = ConcurrentHashMap.newKeySet();

    private WeaponRackFiller() {}

    /** Whether {@code data} is a planned rack rather than some other block entity's data. */
    static boolean isRackPlan(BlockEntityData data) {
        return RoomFurnitureGenerator.RACK_ENTITY.equals(data.getType())
                && data.getData().containsKey(RoomFurnitureGenerator.RACK_SEED);
    }

    /** A plan key: consumed here, and not to be copied into the block entity's tag. */
    static boolean isPlanKey(String key) {
        return key.equals(RoomFurnitureGenerator.RACK_SEED)
                || key.startsWith(RoomFurnitureGenerator.RACK_TABLE);
    }

    /**
     * The rack's {@code Items}, rolled from its plan, or empty when there is no server level to roll
     * against (the headless test harness) &mdash; the rack is then placed empty rather than not at all.
     */
    static Optional<ListTag> rollItems(WorldGenLevel level, BlockPos pos, BlockEntityData data) {
        ServerLevel server;
        try {
            server = level.getLevel();
        } catch (RuntimeException noServer) {
            return Optional.empty();
        }
        long seed;
        try {
            seed = Long.parseLong(data.getData().get(RoomFurnitureGenerator.RACK_SEED));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }

        ListTag items = new ListTag();
        for (int slot = 0; slot < WeaponRackConfig.SLOTS; slot++) {
            String table = data.getData().get(RoomFurnitureGenerator.RACK_TABLE + slot);
            if (table == null) {
                continue;
            }
            // A different seed per slot, or two slots on the same table would rack the same weapon.
            ItemStack weapon = roll(server, pos, table, seed + 31L * slot);
            if (weapon.isEmpty()) {
                continue;
            }
            CompoundTag item = weapon.save(new CompoundTag());
            item.putByte(SLOT, (byte) slot);
            items.add(item);
        }
        return Optional.of(items);
    }

    /** The first rackable item {@code table} rolls, or {@link ItemStack#EMPTY}. */
    private static ItemStack roll(ServerLevel server, BlockPos pos, String table, long seed) {
        ResourceLocation id = ResourceLocation.tryParse(table);
        Optional<LootTable> lootTable = id == null ? Optional.empty()
                : server.getServer().getLootData().getElementOptional(LootDataType.TABLE, id);
        if (lootTable.isEmpty()) {
            if (MISSING_TABLES.add(table)) {
                Dungeons.LOGGER.warn("[D2-RACK] loot table {} does not exist; its rack slots stay"
                        + " empty", table);
            }
            return ItemStack.EMPTY;
        }
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : lootTable.get().getRandomItems(params, seed)) {
            if (WeaponRackBlock.canRack(stack)) {
                return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }
}
