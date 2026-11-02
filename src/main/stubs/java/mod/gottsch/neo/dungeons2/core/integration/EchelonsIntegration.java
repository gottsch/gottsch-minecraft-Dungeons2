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
package mod.gottsch.neo.dungeons2.core.integration;

import mod.gottsch.neo.dungeons2.core.config.EchelonConfig;
import net.minecraft.world.entity.Mob;

import java.util.OptionalInt;

/**
 * TODO port: compile stub for the NeoForge 1.21.1 port. The Enemy Echelons API's NeoForge build
 * is 1.0.0 and this mod needs 1.1.0, so the real facade (src/main/java/.../integration/
 * EchelonsIntegration.java) is excluded from compilation and this stand-in reports the API as
 * never loaded: dungeon mobs keep this mod's own scaling. Delete this file when the real one is restored.
 *
 * @author Mark Gottschling on 11/02/2026
 */
public final class EchelonsIntegration {

    public static final String EECHELONSAPI = "eechelonsapi";

    private EchelonsIntegration() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean apply(Mob mob, EchelonConfig config, int difficulty) {
        return false;
    }

    public static void pinUnscaled(Mob mob) {
    }

    public static OptionalInt difficultyOf(Mob mob) {
        return OptionalInt.empty();
    }
}
