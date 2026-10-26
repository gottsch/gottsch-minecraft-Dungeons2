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
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.Set;

/**
 * {@code dungeons2:grant_advancement}: awards an advancement to the player the loot is rolled for,
 * the moment the entry is drawn, and leaves the item as it is.
 *
 * <p>It is how a lore book turns up once per player. The book's entry is guarded by an
 * {@code entity_properties} condition on a hidden {@code lore/found_<book>} advancement; on its own
 * that advancement was earned only when the book reached the player's inventory, so every chest
 * opened before the first copy was taken could roll another. Granting it at the roll closes that:
 * the next chest's condition already fails, whether or not the player picks this copy up.</p>
 *
 * <p>Needs a player as {@code this} - a chest opened by one. Anything else (a hopper minecart, a
 * {@code /loot} with no player, a pot breaking) grants nothing, which is right: the condition
 * guarding the entry fails for those too.</p>
 *
 * <pre>{ "function": "dungeons2:grant_advancement", "advancement": "dungeons2:lore/found_sextons_ledger" }</pre>
 *
 * @author Mark Gottschling on 10/25/2026
 */
public record GrantAdvancementFunction(ResourceLocation advancement) implements LootItemFunction {

    @Override
    public LootItemFunctionType getType() {
        return Registration.GRANT_ADVANCEMENT_FUNCTION.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        if (context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) {
            Advancement found = player.server.getAdvancements().getAdvancement(this.advancement);
            if (found != null) {
                AdvancementProgress progress = player.getAdvancements().getOrStartProgress(found);
                for (String criterion : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(found, criterion);
                }
            }
        }
        return stack;
    }

    /** JSON only, which is what a 1.20.1 loot function is read with. */
    public static class Serializer implements net.minecraft.world.level.storage.loot.Serializer<GrantAdvancementFunction> {
        @Override
        public void serialize(JsonObject json, GrantAdvancementFunction function, JsonSerializationContext context) {
            json.addProperty("advancement", function.advancement().toString());
        }

        @Override
        public GrantAdvancementFunction deserialize(JsonObject json, JsonDeserializationContext context) {
            return new GrantAdvancementFunction(new ResourceLocation(GsonHelper.getAsString(json, "advancement")));
        }
    }
}
