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
package mod.gottsch.forge.dungeons2.core.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.client.renderer.RenderType;

/**
 * This mod's own render types. A subclass only to reach {@code RenderStateShard}'s protected
 * shards; it is never instantiated.
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
public final class DungeonsRenderTypes extends RenderType {

    /**
     * Additive, unlit, untextured quads: a beam of light.
     *
     * <ul>
     *   <li><strong>Lightning's shader and blend</strong> &mdash; position + colour only, no
     *       lightmap, so it is full-bright in a dark dungeon; {@code SRC_ALPHA, ONE}, so overlapping
     *       layers add up to a hot core, and alpha fades a layer out rather than darkening it.</li>
     *   <li><strong>Colour writes only, no depth.</strong> Vanilla's {@code lightning()} writes
     *       depth, and for crossed quads that means whichever plane is drawn first hides the other
     *       where it passes behind. Additive blending does not care about order, so there is nothing
     *       for the depth buffer to decide.</li>
     *   <li><strong>No culling</strong>, so one quad serves both faces.</li>
     * </ul>
     */
    public static final RenderType BEAM = create(Dungeons.MOD_ID + ":beam",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private DungeonsRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
