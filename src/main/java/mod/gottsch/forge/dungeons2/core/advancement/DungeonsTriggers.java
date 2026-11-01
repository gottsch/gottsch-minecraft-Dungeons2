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

import net.minecraft.advancements.CriteriaTriggers;

/**
 * Dungeons2's own advancement criteria. Registered from the mod constructor, before any datapack
 * (and so any advancement naming them) loads; vanilla has no Forge registry for these.
 *
 * @author Mark Gottschling on 10/26/2026
 */
public final class DungeonsTriggers {

    public static final ReachedFloorTrigger REACHED_FLOOR = CriteriaTriggers.register(new ReachedFloorTrigger());

    private DungeonsTriggers() {}

    /** Loads this class, which is what registers the triggers. */
    public static void register() {
        // the static initializer does the work
    }
}
