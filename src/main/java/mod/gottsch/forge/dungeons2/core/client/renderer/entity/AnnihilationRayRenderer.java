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
import mod.gottsch.forge.dungeons2.core.client.renderer.DungeonsRenderTypes;
import mod.gottsch.forge.dungeons2.core.entity.projectile.BeamMath;
import mod.gottsch.forge.dungeons2.core.entity.projectile.AnnihilationRay;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a {@link AnnihilationRay} as layered additive ribbons from the caster's eye to wherever
 * the server last measured the beam to stop.
 *
 * <h2>Three layers, two kinds of quad</h2>
 * <ul>
 *   <li><strong>Glow</strong> &mdash; wide, faint, on a <em>crossed</em> pair of planes at right
 *       angles around the beam, so it has body from every side.</li>
 *   <li><strong>Inner and core</strong> &mdash; narrow and bright, on a single plane turned to face
 *       the camera. A crossed pair seen along one of its planes collapses to a line, and for the
 *       glow that is invisible; for the core it would be the beam flickering out.</li>
 * </ul>
 * <p>Each ribbon is two quads, bright down the middle and transparent at the edges, so a layer
 * fades out rather than ending in a hard stripe. Additive blending then sums the layers into a hot
 * core with no texture to author.</p>
 *
 * <h2>Where the ends come from</h2>
 * <p>The origin is recomputed every frame from the owner's <em>interpolated</em> position, not
 * read from the beam entity: the beam's own position arrives on network ticks and would leave the
 * beam trailing the eye by a frame. The end point is the server's, synched each tick.</p>
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
public class AnnihilationRayRenderer extends EntityRenderer<AnnihilationRay> {

    /** Green, as a beholder's disintegration ray traditionally is. */
    private static final float GLOW_R = 0.35F, GLOW_G = 1.0F, GLOW_B = 0.25F;
    private static final float CORE_R = 0.85F, CORE_G = 1.0F, CORE_B = 0.8F;

    /** Half-widths, in blocks. */
    private static final float GLOW_WIDTH = 0.38F;
    private static final float INNER_WIDTH = 0.14F;
    private static final float CORE_WIDTH = 0.05F;
    /** Half-size of the flare at the burn point. */
    private static final float FLARE_SIZE = 0.45F;

    /** Ticks to reach full brightness when lit, and to fade before it goes out. */
    private static final float FADE_IN_TICKS = 3.0F;
    private static final float FADE_OUT_TICKS = 5.0F;

    public AnnihilationRayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AnnihilationRay beam, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Entity owner = beam.getOwner();
        if (owner == null) {
            return;
        }
        Vec3 end = beam.getEnd();
        Vec3 origin = AnnihilationRay.originToward(owner, end, partialTick);
        Vec3 axis = end.subtract(origin);
        double length = axis.length();
        if (length < 1.0E-3D) {
            return;
        }
        Vec3 dir = axis.scale(1.0D / length);

        // The pose stack sits at the beam entity's interpolated position; every vertex is written
        // relative to that.
        Vec3 base = new Vec3(Mth.lerp(partialTick, beam.xOld, beam.getX()),
                Mth.lerp(partialTick, beam.yOld, beam.getY()),
                Mth.lerp(partialTick, beam.zOld, beam.getZ()));
        Vec3 from = origin.subtract(base);
        Vec3 to = end.subtract(base);

        Vec3 toCamera = this.entityRenderDispatcher.camera.getPosition().subtract(origin.add(axis.scale(0.5D)));
        Vec3 facing = dir.cross(toCamera);
        facing = facing.lengthSqr() < 1.0E-6D ? BeamMath.perpendicular(dir) : facing.normalize();
        Vec3 side = BeamMath.perpendicular(dir);
        Vec3 otherSide = dir.cross(side);

        float age = beam.tickCount + partialTick;
        float fade = Math.min(1.0F, age / FADE_IN_TICKS)
                * Mth.clamp((AnnihilationRay.LIFETIME_TICKS - age) / FADE_OUT_TICKS, 0.0F, 1.0F);
        float flicker = 0.85F + 0.15F * Mth.sin(age * 1.7F);
        float a = fade * flicker;

        VertexConsumer consumer = buffer.getBuffer(DungeonsRenderTypes.BEAM);
        Matrix4f pose = poseStack.last().pose();
        ribbon(consumer, pose, from, to, side, GLOW_WIDTH * flicker, GLOW_R, GLOW_G, GLOW_B, 0.28F * a);
        ribbon(consumer, pose, from, to, otherSide, GLOW_WIDTH * flicker, GLOW_R, GLOW_G, GLOW_B, 0.28F * a);
        ribbon(consumer, pose, from, to, facing, INNER_WIDTH, GLOW_R, GLOW_G, GLOW_B, 0.7F * a);
        ribbon(consumer, pose, from, to, facing, CORE_WIDTH, CORE_R, CORE_G, CORE_B, a);

        Vec3 up = toCamera.lengthSqr() < 1.0E-6D ? otherSide : facing.cross(toCamera.normalize());
        flare(consumer, pose, to, facing, up, FLARE_SIZE * flicker, GLOW_R, GLOW_G, GLOW_B, 0.6F * a);

        super.render(beam, yaw, partialTick, poseStack, buffer, packedLight);
    }

    /** A ribbon of half-width {@code width} along {@code side}, opaque on its axis, clear at its edges. */
    private static void ribbon(VertexConsumer consumer, Matrix4f pose, Vec3 from, Vec3 to, Vec3 side,
                               float width, float r, float g, float b, float alpha) {
        Vec3 offset = side.scale(width);
        Vec3 fromLeft = from.subtract(offset), toLeft = to.subtract(offset);
        Vec3 fromRight = from.add(offset), toRight = to.add(offset);
        vertex(consumer, pose, fromLeft, r, g, b, 0.0F);
        vertex(consumer, pose, toLeft, r, g, b, 0.0F);
        vertex(consumer, pose, to, r, g, b, alpha);
        vertex(consumer, pose, from, r, g, b, alpha);

        vertex(consumer, pose, from, r, g, b, alpha);
        vertex(consumer, pose, to, r, g, b, alpha);
        vertex(consumer, pose, toRight, r, g, b, 0.0F);
        vertex(consumer, pose, fromRight, r, g, b, 0.0F);
    }

    /**
     * A camera-facing glow at the burn point: four quads meeting at a bright centre and fading to
     * nothing at the rim, which is as round as four vertices can make it.
     */
    private static void flare(VertexConsumer consumer, Matrix4f pose, Vec3 centre, Vec3 u, Vec3 v,
                              float size, float r, float g, float b, float alpha) {
        Vec3[] rim = {u.scale(size), v.scale(size), u.scale(-size), v.scale(-size)};
        for (int i = 0; i < rim.length; i++) {
            Vec3 edge = centre.add(rim[i]);
            Vec3 next = centre.add(rim[(i + 1) % rim.length]);
            Vec3 corner = centre.add(rim[i].add(rim[(i + 1) % rim.length]).scale(0.5D));
            vertex(consumer, pose, centre, r, g, b, alpha);
            vertex(consumer, pose, edge, r, g, b, 0.0F);
            vertex(consumer, pose, corner, r, g, b, 0.0F);
            vertex(consumer, pose, next, r, g, b, 0.0F);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec3 at,
                               float r, float g, float b, float a) {
        consumer.vertex(pose, (float) at.x, (float) at.y, (float) at.z).color(r, g, b, a).endVertex();
    }

    /** Unused: the beam is untextured. {@code EntityRenderer} requires an answer. */
    @Override
    public ResourceLocation getTextureLocation(AnnihilationRay beam) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
