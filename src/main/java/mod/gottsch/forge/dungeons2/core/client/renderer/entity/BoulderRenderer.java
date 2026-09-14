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
import com.mojang.math.Axis;
import mod.gottsch.forge.dungeons2.core.entity.projectile.Boulder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a {@link Boulder} as the block it was torn from, tumbling.
 *
 * <p>No mesh and no texture: the block renderer already knows how to draw every block in every
 * motif, so a thrown lump of the room self-themes to wherever the fight is happening. The same trick
 * was tried for {@code SmashShard} and rejected &mdash; but a shard is chip-sized and a cube cannot
 * fake a chip, whereas a boulder genuinely is a block-sized lump of masonry.
 *
 * <p><strong>The spin axis comes from the entity's id, not from a synched field.</strong> The id is
 * the same number on both sides and never changes, so every client tumbles a given rock identically
 * for free; a random axis picked client-side would have two players watching the same throw see
 * different rocks.
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
public class BoulderRenderer extends EntityRenderer<Boulder> {

    /** A little under a block, so it reads as thrown rather than as a placed block drifting past. */
    private static final float SCALE = 0.85F;

    public BoulderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Boulder entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float spin = (entity.tickCount + partialTick) * Boulder.SPIN_DEGREES_PER_TICK;
        int axis = Math.floorMod(entity.getId(), 3);

        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        // Two axes at once, at different rates, so it tumbles rather than spinning like a coin --
        // and which two is picked off the id, so no two rocks in a fight look like the same rock.
        poseStack.mulPose((axis == 0 ? Axis.XP : axis == 1 ? Axis.YP : Axis.ZP).rotationDegrees(spin));
        poseStack.mulPose((axis == 2 ? Axis.XP : Axis.ZP).rotationDegrees(spin * 0.6F));
        // BlockState models are drawn from a corner, not from the middle.
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                entity.getBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Boulder entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
