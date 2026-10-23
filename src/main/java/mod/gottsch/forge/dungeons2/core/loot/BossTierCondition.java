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
import com.google.gson.JsonSyntaxException;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Set;

/**
 * {@code dungeons2:boss_tier}: passes when the container being rolled belongs to a dungeon whose
 * boss is of this tier. It is what lets a boss tier's lore book turn up in the chests ABOVE that
 * boss, and only in a dungeon that has one: the hint has to arrive before the fight to be any use.
 *
 * <p>The tier is not something a loot roll can find out for itself &mdash; a structure lookup cannot
 * see a D2 dungeon from inside past eight chunks &mdash; so the room piece stamps it on each chest
 * and tomb it writes, as {@code ForgeData.}{@value #KEY}, and this reads it back off the block
 * entity at the roll's origin. A container with no stamp (a template's chest, the boss room's own
 * reward chest, anything outside a dungeon) never passes.</p>
 *
 * <p>The origin and its four horizontal neighbours are read, not the origin alone: a tomb rolls
 * from the middle of its lid, over the seam between its two halves, and which half that lands in
 * depends on the tomb's facing. Both halves carry the stamp.</p>
 *
 * <pre>{ "condition": "dungeons2:boss_tier", "tier": "small" }</pre>
 *
 * @author Mark Gottschling on 10/22/2026
 */
public record BossTierCondition(String tier) implements LootItemCondition {

    /** The {@code ForgeData} key a room piece writes on its chests and tombs. */
    public static final String KEY = "D2BossTier";

    /** The tiers, which are the dungeon sizes: a dungeon's boss comes from its size's boss set. */
    public static final Set<String> TIERS = Set.of("small", "medium", "large");

    /** The stamp for a dungeon of this size. */
    public static String tierOf(DungeonSize size) {
        return size.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public LootItemConditionType getType() {
        return Registration.BOSS_TIER_CONDITION.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    public boolean test(LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(origin);
        if (stamped(context, pos)) {
            return true;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (stamped(context, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private boolean stamped(LootContext context, BlockPos pos) {
        BlockEntity blockEntity = context.getLevel().getBlockEntity(pos);
        return blockEntity != null && tier.equals(blockEntity.getPersistentData().getString(KEY));
    }

    /** JSON only, which is what a 1.20.1 loot condition is read with. */
    public static class Serializer implements net.minecraft.world.level.storage.loot.Serializer<BossTierCondition> {
        @Override
        public void serialize(JsonObject json, BossTierCondition condition, JsonSerializationContext context) {
            json.addProperty("tier", condition.tier());
        }

        @Override
        public BossTierCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            String tier = GsonHelper.getAsString(json, "tier");
            // A typo would be a book that never drops anywhere, with no error -- so it is one.
            if (!TIERS.contains(tier)) {
                throw new JsonSyntaxException("dungeons2:boss_tier: unknown tier '" + tier
                        + "', expected one of " + TIERS);
            }
            return new BossTierCondition(tier);
        }
    }
}
