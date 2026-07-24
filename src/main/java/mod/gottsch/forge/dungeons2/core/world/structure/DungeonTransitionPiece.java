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
 * A 2-story floor-to-floor link, loaded from an {@code .nbt} prefab under
 * {@code data/dungeons2/structures/transitions/}. One piece is simultaneously the
 * upper floor's END slot and the lower floor's START slot; its bounding box spans
 * both floor Y levels and is built one chunk-slice at a time by the
 * {@link DungeonTemplatePiece} base.
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public class DungeonTransitionPiece extends DungeonTemplatePiece {

    private int upperFloorIndex;
    private int lowerFloorIndex;

    public DungeonTransitionPiece(StructureTemplateManager templateManager, ResourceLocation templateId,
                                  BlockPos templatePosition, Rotation rotation, String motifValue,
                                  int upperFloorIndex, int lowerFloorIndex) {
        super(StructurePieces.TRANSITION, templateManager, templateId, templatePosition, rotation, motifValue);
        this.upperFloorIndex = upperFloorIndex;
        this.lowerFloorIndex = lowerFloorIndex;
    }

    public DungeonTransitionPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.TRANSITION, tag, context.structureTemplateManager());
        this.upperFloorIndex = tag.getInt("UpperFloor");
        this.lowerFloorIndex = tag.getInt("LowerFloor");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putInt("UpperFloor", upperFloorIndex);
        tag.putInt("LowerFloor", lowerFloorIndex);
    }

    public int getUpperFloorIndex() {
        return upperFloorIndex;
    }

    public int getLowerFloorIndex() {
        return lowerFloorIndex;
    }
}
