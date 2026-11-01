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
import mod.gottsch.forge.gmm.core.client.renderer.entity.MinotaurRenderer;
import mod.gottsch.forge.gmm.core.entity.monster.Minotaur;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * GMM's Minotaur renderer, drawn at the size <em>this</em> mod registers the Minotaur at.
 *
 * <h2>Why a subclass here rather than a change in GMM</h2>
 * <p>Dungeons2 registers its own {@code MINOTAUR_ENTITY} and its own hitbox, so the size is this
 * mod's decision (Mark, 2026-09-09: "slightly bigger, but less than 3 blocks tall"). GMM's renderer
 * applies no scale of its own &mdash; it draws the model at the size the rig was authored &mdash;
 * so changing {@code sized()} alone would leave a taller hitbox around an unchanged model. Scaling
 * here keeps the change inside the repo that owns the decision, which is also the pattern
 * {@code MinotaurGoalsEvent} already follows for the Minotaur's behaviour.</p>
 *
 * <h2>The scale is DERIVED, never authored twice</h2>
 * <p>{@link DungeonsEntities#MINOTAUR_MODEL_SCALE} is computed from the registered height against
 * the height the rig was built at, so the hitbox and the model cannot drift: raise the one constant
 * in {@code DungeonsEntities} and both follow. A literal here would be a second opinion on the same
 * quantity, and the kind that goes unnoticed because a model slightly out of its box still looks
 * like a mob.</p>
 *
 * <p><strong>Applied to all three axes, and the width mismatch is intentional.</strong> The hitbox
 * stays 1.0 wide however big the model gets, because a box over 1.0 cannot path a 1-block corridor
 * and a brute that cannot follow the player down a hallway is not a threat &mdash; see
 * {@code DungeonsEntities#MINOTAUR_ENTITY}. The rig was always wider than its box (~1.25 against
 * 1.0); this widens that gap rather than introducing it.</p>
 */
public class ScaledMinotaurRenderer<T extends Minotaur> extends MinotaurRenderer<T> {

    public ScaledMinotaurRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        super.scale(entity, poseStack, partialTick);
        poseStack.scale(DungeonsEntities.MINOTAUR_MODEL_SCALE,
                DungeonsEntities.MINOTAUR_MODEL_SCALE,
                DungeonsEntities.MINOTAUR_MODEL_SCALE);
    }
}
