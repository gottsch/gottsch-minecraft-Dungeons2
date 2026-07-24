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
package mod.gottsch.forge.dungeons2.core.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

/**
 * Thin helper over {@link StructureTemplateManager} for resolving template
 * prefabs by {@link ResourceLocation} and reading their dimensions.
 *
 * <p>Phase 3 plumbing: the planner stores template footprints in
 * {@code TemplateEntry}, and the catalog loader uses {@link #size} to fill
 * {@code width/depth/height} straight from the {@code .nbt} (per the authoring
 * spec, dimensions are auto-read, never hand-entered). The template pieces use
 * the manager directly through {@link TemplateStructurePiece}.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public final class TemplateLoader {

    private TemplateLoader() {}

    /** Resolves a template by id, or empty if it isn't loaded / doesn't exist. */
    public static Optional<StructureTemplate> get(StructureTemplateManager manager, ResourceLocation id) {
        return manager.get(id);
    }

    /** The template's bounding-box size in blocks (width=X, height=Y, depth=Z), or ZERO if absent. */
    public static Vec3i size(StructureTemplateManager manager, ResourceLocation id) {
        return manager.get(id).map(StructureTemplate::getSize).orElse(Vec3i.ZERO);
    }

    /**
     * Where to place a template (as the {@code position} argument to a
     * {@code TemplateStructurePiece}/{@code StructureTemplate}) so that its
     * placed, rotated bounding box's min corner lands exactly at
     * {@code desiredMinCorner}.
     *
     * <p>Rotation pivots at local {@link BlockPos#ZERO} (this mod's authoring
     * convention &mdash; see the structures README's NW-bottom-corner origin
     * rule), which is a template corner, not its center. Vanilla rotates around
     * whatever pivot it's given without re-centering, so for any rotation other
     * than {@code NONE} the rotated footprint swings into a different quadrant
     * relative to that corner &mdash; e.g. a 90&deg; rotation flips the Z span
     * negative. Placing the naive, unrotated min corner at a planner-reserved
     * position is therefore wrong for 3 of every 4 rotations; this computes the
     * correction using the template's real bounding box instead of hand-derived
     * rotation math.</p>
     *
     * @return {@code desiredMinCorner} unchanged if the template isn't loaded
     *         (caller's placement will simply do nothing useful, same as today).
     */
    public static BlockPos correctedOriginForRotation(StructureTemplateManager manager, ResourceLocation id,
                                                       Rotation rotation, BlockPos desiredMinCorner) {
        Optional<StructureTemplate> templateOpt = manager.get(id);
        if (templateOpt.isEmpty()) {
            return desiredMinCorner;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(BlockPos.ZERO);
        BoundingBox localBox = templateOpt.get().getBoundingBox(settings, BlockPos.ZERO);
        int dx = desiredMinCorner.getX() - localBox.minX();
        int dz = desiredMinCorner.getZ() - localBox.minZ();
        return new BlockPos(dx, desiredMinCorner.getY(), dz);
    }
}
