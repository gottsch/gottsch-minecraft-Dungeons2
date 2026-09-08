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
import mod.gottsch.forge.dungeons2.core.entity.projectile.SmashShard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a {@link SmashShard} as a shrunken copy of the block it was cut from.
 *
 * <p>There is no model and no texture here on purpose. {@code renderSingleBlock} draws whatever
 * {@link SmashShard#getSmashedState()} says, so the shard is automatically the right material for
 * whatever the mob just broke &mdash; every motif, every strata band, and anything a datapack adds
 * later &mdash; and there is no art to keep in step with the palettes. {@code BoneShardRenderer}
 * carries a rig and a texture because a bone shard is one fixed thing; a chip of wall is not.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class SmashShardRenderer extends EntityRenderer<SmashShard> {

    /**
     * Degrees of tumble per tick. Fast enough to read as debris rather than as a thrown weapon,
     * which is the difference between this and the bone shard's flight.
     */
    private static final float SPIN_DEGREES_PER_TICK = 22.0F;

    public SmashShardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SmashShard entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        BlockState state = entity.getSmashedState();
        float scale = entity.getScale();
        float age = entity.tickCount + partialTick;

        poseStack.pushPose();
        // Tumble on two axes, offset per entity so a burst from one swing does not spin in unison
        // -- the id is stable client-side and needs no syncing of its own.
        float phase = (entity.getId() * 37) % 360;
        poseStack.mulPose(Axis.YP.rotationDegrees(phase + age * SPIN_DEGREES_PER_TICK));
        poseStack.mulPose(Axis.XP.rotationDegrees(phase + age * SPIN_DEGREES_PER_TICK * 0.7F));
        poseStack.scale(scale, scale, scale);
        // renderSingleBlock draws from the block's corner, so back off by half to spin about the
        // shard's own centre rather than about one of its corners.
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * Never sampled &mdash; {@code renderSingleBlock} binds the block atlas itself. Required by
     * {@link EntityRenderer}, so it returns the atlas rather than a texture of this mod's that
     * would have to exist without ever being drawn.
     *
     * <p>{@code InventoryMenu.BLOCK_ATLAS} rather than gmm's {@code TextureAtlas.LOCATION_BLOCKS};
     * they are the same location and the latter is deprecated.</p>
     */
    @Override
    public ResourceLocation getTextureLocation(SmashShard entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
