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
package mod.gottsch.forge.dungeons2.core.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import mod.gottsch.forge.dungeons2.core.map.DungeonMaps;
import mod.gottsch.forge.dungeons2.core.map.FloorMapStamp;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * {@code dungeons2:dungeon_map}: turns the rolled item into a Dungeon Map of the floor the chest is
 * on - a blank, locked vanilla map centred on the floor, which fills in as its holder explores (see
 * {@link DungeonMaps}).
 *
 * <p>The floor is read off the chest: the room piece stamped it there as
 * {@code ForgeData.}{@value FloorMapStamp#KEY} when it wrote the chest. A chest with no stamp - a
 * template's, or one outside a dungeon - has no floor to map, and the item comes out
 * <strong>empty</strong>, so the entry gives nothing rather than a map of nowhere.</p>
 *
 * <p>The origin and its four horizontal neighbours are read, as {@link BossTierCondition} reads them,
 * so the same lookup serves a container that rolls from beside its own block.</p>
 *
 * <pre>{ "function": "dungeons2:dungeon_map" }</pre>
 *
 * @author Mark Gottschling on 10/25/2026
 */
public class DungeonMapFunction implements LootItemFunction {

    @Override
    public LootItemFunctionType getType() {
        return Registration.DUNGEON_MAP_FUNCTION.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        FloorMapStamp stamp = origin == null ? null : stampNear(context, BlockPos.containing(origin));
        if (stamp == null) {
            return ItemStack.EMPTY;
        }
        return DungeonMaps.create(context.getLevel(), stamp);
    }

    @Nullable
    private static FloorMapStamp stampNear(LootContext context, BlockPos pos) {
        FloorMapStamp stamp = stampAt(context, pos);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (stamp != null) {
                break;
            }
            stamp = stampAt(context, pos.relative(direction));
        }
        return stamp;
    }

    @Nullable
    private static FloorMapStamp stampAt(LootContext context, BlockPos pos) {
        BlockEntity blockEntity = context.getLevel().getBlockEntity(pos);
        if (blockEntity == null || !blockEntity.getPersistentData().contains(FloorMapStamp.KEY)) {
            return null;
        }
        return FloorMapStamp.load(blockEntity.getPersistentData().getCompound(FloorMapStamp.KEY));
    }

    /** JSON only, which is what a 1.20.1 loot function is read with. It has no fields. */
    public static class Serializer implements net.minecraft.world.level.storage.loot.Serializer<DungeonMapFunction> {
        @Override
        public void serialize(JsonObject json, DungeonMapFunction function, JsonSerializationContext context) {
        }

        @Override
        public DungeonMapFunction deserialize(JsonObject json, JsonDeserializationContext context) {
            return new DungeonMapFunction();
        }
    }
}
