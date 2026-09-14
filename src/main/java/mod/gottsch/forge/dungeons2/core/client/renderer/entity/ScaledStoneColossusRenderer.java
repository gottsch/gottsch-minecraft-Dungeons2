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
package mod.gottsch.forge.dungeons2.core.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import mod.gottsch.forge.gmm.core.client.renderer.entity.StoneColossusRenderer;
import mod.gottsch.forge.gmm.core.entity.monster.StoneColossus;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GMM's Stone Colossus renderer, drawn at the size <em>this</em> mod registers it at.
 *
 * <p>Same split as {@link ScaledMinotaurRenderer}: GMM owns the class, the rig and the animation;
 * Dungeons2 registers the entity and therefore owns its size. GMM's renderer applies no scale of
 * its own, so {@code sized()} alone would leave a 7-block hitbox around a 2.75-block model.</p>
 *
 * <p>{@link DungeonsEntities#STONE_COLOSSUS_MODEL_SCALE} is DERIVED from the registered height
 * against the rig height, so the two cannot drift -- a literal here would be a second opinion on
 * the same quantity, and the kind nobody notices because a model slightly out of its box still
 * looks like a mob.</p>
 *
 * <p><strong>The shadow follows the FOOTPRINT, not the height</strong> (Mark, 2026-09-13: "it's
 * shadow is way too big. it's tall, but the silhouette is still relatively thin"). Multiplying
 * GMM's radius by the model scale was the obvious move and the wrong one: the scale exists to make
 * the mob TALL, and a shadow is cast by what stands on the ground. At 2.55x it drew a 2.3-block
 * radius puddle under a figure less than two blocks across. Half the registered hitbox width is
 * what the feet actually occupy, and deriving it from that constant means the shadow follows any
 * future change to the box.</p>
 */
public class ScaledStoneColossusRenderer<T extends StoneColossus> extends StoneColossusRenderer<T> {

    public ScaledStoneColossusRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = DungeonsEntities.STONE_COLOSSUS_WIDTH / 2.0F;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(DungeonsEntities.STONE_COLOSSUS_MODEL_SCALE,
                DungeonsEntities.STONE_COLOSSUS_MODEL_SCALE,
                DungeonsEntities.STONE_COLOSSUS_MODEL_SCALE);
    }
}
