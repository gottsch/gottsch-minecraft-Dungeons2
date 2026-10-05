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
package mod.gottsch.forge.dungeons2.core.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.entity.MiningChestBlockEntity;
import mod.gottsch.forge.dungeons2.core.client.model.MiningChestModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws the Mining Chest with its lid animated, following Treasure2's
 * {@code AbstractChestBlockEntityRenderer} transform for the same model.
 *
 * <p>The texture sits under {@code textures/block/}, so it is stitched into the block atlas with no
 * atlas JSON, and the item model reads the same file.</p>
 *
 * @author Mark Gottschling on Oct 4, 2026
 */
@OnlyIn(Dist.CLIENT)
public class MiningChestRenderer implements BlockEntityRenderer<MiningChestBlockEntity> {
    public static final Material MATERIAL = new Material(TextureAtlas.LOCATION_BLOCKS,
            new ResourceLocation(Dungeons.MOD_ID, "block/mining_chest"));

    private final MiningChestModel model;

    public MiningChestRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new MiningChestModel(context.bakeLayer(MiningChestModel.LAYER_LOCATION));
    }

    @Override
    public void render(MiningChestBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(ChestBlock.FACING)
                ? state.getValue(ChestBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        // The model is centred on the origin and upside down, as entity models are.
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.scale(-1, -1, 1);
        poseStack.mulPose(Axis.YP.rotationDegrees(-horizontalAngle(facing)));

        // Vanilla's easing: fast at first, settling as it opens.
        float openness = 1.0F - blockEntity.getOpenNess(partialTicks);
        openness = 1.0F - openness * openness * openness;
        model.getLid().xRot = -(openness * (float) Math.PI / 2.0F);

        model.renderToBuffer(poseStack, MATERIAL.buffer(bufferSource, RenderType::entitySolid),
                combinedLight, combinedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    /** The model's front is at -Z after the flip above, so north is the unrotated case. */
    private static float horizontalAngle(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
