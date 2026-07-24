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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The surface-to-floor-0 entrance, loaded from an {@code .nbt} prefab under
 * {@code data/dungeons2/structures/entrances/}. Spans from the surface down to
 * floor 0's opening and reserves floor 0's START slot (the maze routes around it).
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public class DungeonEntrancePiece extends DungeonTemplatePiece {

    public DungeonEntrancePiece(StructureTemplateManager templateManager, ResourceLocation templateId,
                                BlockPos templatePosition, Rotation rotation, String motifValue) {
        super(StructurePieces.ENTRANCE, templateManager, templateId, templatePosition, rotation, motifValue);
    }

    public DungeonEntrancePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.ENTRANCE, tag, context.structureTemplateManager());
    }
}
