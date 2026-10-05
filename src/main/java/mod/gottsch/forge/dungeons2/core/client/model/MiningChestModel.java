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
package mod.gottsch.forge.dungeons2.core.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Mining Chest's model: Treasure2's {@code BandedChestModel} (the ironbound chest), copied.
 *
 * <p>Copied rather than referenced because Treasure2 is an optional dependency &mdash; no Treasure2
 * class may load unless the mod is present, and the Mining Chest exists either way. Its texture
 * shares the ironbound chest's 128x128 UV layout.</p>
 *
 * @author Mark Gottschling on Oct 4, 2026
 */
@OnlyIn(Dist.CLIENT)
public class MiningChestModel extends Model {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(new ResourceLocation(Dungeons.MOD_ID, "mining_chest"), "main");

    private final ModelPart main;
    private final ModelPart lid;

    public MiningChestModel(ModelPart root) {
        super(RenderType::entitySolid);
        this.main = root.getChild("main");
        this.lid = root.getChild("lid");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition root = meshdefinition.getRoot();

        PartDefinition main = root.addOrReplaceChild("main", CubeListBuilder.create().texOffs(0, 20).mirror().addBox(-7.0F, 0.0F, -14.0F, 14.0F, 9.0F, 14.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 14.5F, 7.0F));
        main.addOrReplaceChild("hinge2", CubeListBuilder.create().texOffs(11, 86).mirror().addBox(-1.0F, 0.0F, -0.8F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-2.0F, 0.0F, 0.0F));
        main.addOrReplaceChild("hinge1", CubeListBuilder.create().texOffs(11, 86).addBox(-1.0F, 0.0F, -0.8F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 0.0F, 0.0F));
        main.addOrReplaceChild("leftBottomBand", CubeListBuilder.create().texOffs(35, 44).addBox(-1.0F, 0.0F, -15.0F, 2.0F, 9.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 0.5F, 0.5F));
        main.addOrReplaceChild("rightBottomBand", CubeListBuilder.create().texOffs(0, 44).mirror().addBox(-1.0F, 0.0F, -15.0F, 2.0F, 9.0F, 15.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-4.0F, 0.5F, 0.5F));
        main.addOrReplaceChild("pad", CubeListBuilder.create().texOffs(0, 86).mirror().addBox(-2.0F, -1.0F, -14.2F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 1.0F, 0.0F));

        PartDefinition lid = root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-7.0F, -5.0F, -14.0F, 14.0F, 5.0F, 14.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 15.5F, 7.0F));
        lid.addOrReplaceChild("frontRightTopBand", CubeListBuilder.create().texOffs(57, 0).mirror().addBox(-1.0F, -4.0F, -15.0F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-4.0F, -0.5F, 0.5F));
        lid.addOrReplaceChild("backLeftTopBand", CubeListBuilder.create().texOffs(64, 7).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -0.5F, 0.5F));
        lid.addOrReplaceChild("rightTopBand", CubeListBuilder.create().texOffs(0, 69).mirror().addBox(0.0F, -5.0F, -15.0F, 2.0F, 1.0F, 15.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-5.0F, -0.5F, 0.5F));
        lid.addOrReplaceChild("leftTopBand", CubeListBuilder.create().texOffs(36, 69).addBox(0.0F, -5.0F, -15.0F, 2.0F, 1.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offset(3.0F, -0.5F, 0.5F));
        lid.addOrReplaceChild("frontLeftTopBand", CubeListBuilder.create().texOffs(64, 0).addBox(-1.0F, -4.0F, -15.0F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -0.5F, 0.5F));
        lid.addOrReplaceChild("backRightTopBand", CubeListBuilder.create().texOffs(57, 7).mirror().addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-4.0F, -0.5F, 0.5F));
        lid.addOrReplaceChild("ledge3", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-1.0F, -1.0F, -9.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-7.0F, -0.5F, 0.0F));
        lid.addOrReplaceChild("ledge", CubeListBuilder.create().texOffs(58, 16).mirror().addBox(-2.0F, -1.0F, -15.0F, 4.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, -0.5F, 0.0F));
        lid.addOrReplaceChild("ledge2", CubeListBuilder.create().texOffs(58, 16).mirror().addBox(0.0F, -1.0F, -9.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(7.0F, -0.5F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    public ModelPart getLid() {
        return lid;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                               int packedOverlay, float red, float green, float blue, float alpha) {
        main.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        lid.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
