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
package mod.gottsch.neo.dungeons2.core.advancement;

import mod.gottsch.neo.dungeons2.Dungeons;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Dungeons2's own advancement criteria. Registered through a deferred register on the
 * {@code trigger_type} registry, which NeoForge fills before any datapack (and so any advancement
 * naming them) loads.
 *
 * @author Mark Gottschling on 10/26/2026
 */
public final class DungeonsTriggers {

    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Dungeons.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, ReachedFloorTrigger> REACHED_FLOOR =
            TRIGGERS.register("reached_floor", ReachedFloorTrigger::new);

    private DungeonsTriggers() {}

    /** Called from the mod constructor (via Registration.init). */
    public static void register(IEventBus eventBus) {
        TRIGGERS.register(eventBus);
    }
}
