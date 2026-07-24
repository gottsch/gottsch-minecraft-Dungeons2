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

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
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
}
