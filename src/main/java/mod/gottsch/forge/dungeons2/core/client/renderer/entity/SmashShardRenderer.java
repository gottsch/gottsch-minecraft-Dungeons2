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
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.entity.projectile.SmashShard;
import mod.gottsch.forge.gmm.core.client.model.BoneShardModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a {@link SmashShard} with gmm's bone-shard rig and this mod's stone texture.
 *
 * <p>A deliberate copy of {@code BoneShardRenderer}. The model is gmm's, and its layer definitions
 * are <em>already</em> registered by {@code ClientSetup} for the bone shard itself, so reusing them
 * costs nothing and guarantees the two read as the same kind of object. Only {@link #TEXTURE}
 * differs, which is the whole intent: a masonry chip should look like a bone shard cut from stone.</p>
 *
 * <p>{@code stone_shard.png} is a luminance remap of gmm's {@code bone_shard.png} onto a stone-brick
 * grey ramp, so it lands on exactly the UVs the rig was authored against. Re-derive it the same way
 * if the bone shard's own texture is ever re-cut.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class SmashShardRenderer extends EntityRenderer<SmashShard> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Dungeons.MOD_ID, "textures/entity/stone_shard.png");

    private final BoneShardModel[] models;

    public SmashShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.models = new BoneShardModel[BoneShardModel.LAYERS.length];
        for (int i = 0; i < this.models.length; i++) {
            this.models[i] = new BoneShardModel(context.bakeLayer(BoneShardModel.LAYERS[i]));
        }
    }

    @Override
    public void render(SmashShard entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // orient toward motion
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        // tumble on the long axis -- frozen once the shard sticks/stops (spinTicks stops advancing)
        float spin = (entity.getSpinTicks() + (entity.isStuck() ? 0.0F : partialTick)) * 40.0F;
        poseStack.mulPose(Axis.XP.rotationDegrees(spin));
        float scale = entity.getScale();
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }
        BoneShardModel model = this.models[Math.floorMod(entity.getVariant(), this.models.length)];
        VertexConsumer vertexConsumer = buffer.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SmashShard entity) {
        return TEXTURE;
    }
}
