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
package mod.gottsch.neo.dungeons2.core.integration.jade;

import mod.gottsch.neo.dungeons2.Dungeons;
import mod.gottsch.neo.dungeons2.core.integration.EchelonsIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.OptionalInt;

/**
 * Adds an "Echelon: N" line to Jade's tooltip for a mob that Enemy Echelons has given a difficulty,
 * with EE's difficulty name when it has one. The server reads the difficulty through
 * {@link EchelonsIntegration}, so this shows nothing without EE, and nothing on a mob EE never touched.
 * It reports any EE difficulty, not just the ones Dungeons2 set.
 *
 * @author Mark Gottschling on 11/03/2026
 */
public enum EchelonJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Dungeons.MOD_ID, "echelon");
    private static final String DIFFICULTY = "Difficulty";
    private static final String NAME = "Name";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof Mob mob)) {
            return;
        }
        OptionalInt difficulty = EchelonsIntegration.difficultyOf(mob);
        if (difficulty.isEmpty()) {
            return;
        }
        data.putInt(DIFFICULTY, difficulty.getAsInt());
        EchelonsIntegration.difficultyNameOf(mob).ifPresent(name -> data.putString(NAME, name));
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(DIFFICULTY)) {
            return;
        }
        int difficulty = data.getInt(DIFFICULTY);
        if (data.contains(NAME)) {
            tooltip.add(Component.translatable("jade.dungeons2.echelon_named", difficulty, data.getString(NAME)));
        } else {
            tooltip.add(Component.translatable("jade.dungeons2.echelon", difficulty));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
