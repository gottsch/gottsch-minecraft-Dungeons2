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
package mod.gottsch.forge.dungeons2.core.advancement;

import com.google.gson.JsonObject;
import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * {@code dungeons2:reached_floor}: the player is standing on a dungeon level at least
 * {@code min_floor} down (0 = the entrance level). Counts levels, not strata or biomes, so it means
 * the same in every motif however its depth bands are drawn.
 *
 * <p>Fired by {@code ReachedFloorEvent} from the saved floor index once a second.
 *
 * @author Mark Gottschling on 10/26/2026
 */
public class ReachedFloorTrigger extends SimpleCriterionTrigger<ReachedFloorTrigger.TriggerInstance> {

    static final ResourceLocation ID = new ResourceLocation(Dungeons.MOD_ID, "reached_floor");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                             DeserializationContext context) {
        return new TriggerInstance(player, GsonHelper.getAsInt(json, "min_floor", 0));
    }

    /** {@code floorIndex}: the level the player is on, 0 at the entrance. */
    public void trigger(ServerPlayer player, int floorIndex) {
        this.trigger(player, instance -> floorIndex >= instance.minFloor);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {
        private final int minFloor;

        public TriggerInstance(ContextAwarePredicate player, int minFloor) {
            super(ID, player);
            this.minFloor = minFloor;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.addProperty("min_floor", minFloor);
            return json;
        }
    }
}
