/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.loader.BlockProviderReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the mod's datapack reload listeners onto the server resource manager:
 * currently just the block-provider palette, which is server-data driven and so
 * reloads on world load and on {@code /reload}.
 *
 * <p>Weathering is <em>not</em> here &mdash; it is a vanilla
 * {@code worldgen/processor_list} registry entry ({@code dungeons2:<motif>_weathering}),
 * loaded by vanilla's own datapack registry machinery. See
 * {@link mod.gottsch.forge.dungeons2.core.world.structure.PieceProcessors}.</p>
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DataPackEvents {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BlockProviderReloadListener());
    }
}
