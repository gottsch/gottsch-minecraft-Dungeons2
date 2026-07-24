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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Base for the two template-backed Phase 3 pieces ({@link DungeonEntrancePiece},
 * {@link DungeonTransitionPiece}). Both load an {@code .nbt} prefab via
 * {@link TemplateStructurePiece} and action the {@code d2:*} data markers from
 * the authoring spec in {@link #handleDataMarker}.
 *
 * <p>{@code templateName} (the parent class field) holds the template
 * {@link ResourceLocation} string; {@code motifValue} drives marker content
 * decisions (which loot table / mob a chest / spawner marker resolves to). The
 * template is built facing north and rotated by the {@link StructurePlaceSettings}
 * rotation the planner rolled.</p>
 *
 * <p><strong>Note:</strong> no {@code .nbt} prefabs ship yet, so these pieces are
 * compile-complete but exercised only once Phase 4 (DungeonStructure) emits them
 * against authored templates. The marker handling below is the contract those
 * templates target.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public abstract class DungeonTemplatePiece extends TemplateStructurePiece {

    protected String motifValue;

    /** Planning constructor. */
    protected DungeonTemplatePiece(StructurePieceType type, StructureTemplateManager templateManager,
                                   ResourceLocation templateId, BlockPos templatePosition,
                                   Rotation rotation, String motifValue) {
        super(type, 0, templateManager, templateId, templateId.toString(),
                makeSettings(rotation), templatePosition);
        this.motifValue = motifValue;
    }

    /** Load constructor. The settings factory restores rotation from NBT. */
    protected DungeonTemplatePiece(StructurePieceType type, CompoundTag tag,
                                   StructureTemplateManager templateManager) {
        super(type, tag, templateManager,
                location -> makeSettings(readRotation(tag.getString("Rot"))));
        this.motifValue = tag.getString("Motif");
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation) {
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(BlockPos.ZERO)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    private static Rotation readRotation(String name) {
        try {
            return Rotation.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Rotation.NONE;
        }
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
        tag.putString("Motif", motifValue == null ? "" : motifValue);
    }

    /**
     * Actions the {@code d2:*} markers (see the Template Authoring spec). The DATA
     * structure block is already cleared to air on placement, so this only adds
     * content. Alignment markers ({@code d2:door} / {@code d2:descend} /
     * {@code d2:ascend} / {@code d2:anchor}) are consumed by the planner, not at
     * runtime, so they are no-ops here.
     */
    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        switch (name) {
            case "d2:chest" -> placeChest(level, pos, random);
            case "d2:spawner" -> placeSpawner(level, pos, random);
            default -> {
                // alignment marker — nothing to place at runtime.
            }
        }
    }

    /**
     * Places a chest at the marker. Loot-table assignment is a later-phase
     * concern (needs the motif-to-loot policy), so for now it is a plain chest.
     */
    protected void placeChest(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        // TODO assign loot table from motif (Phase 4+).
    }

    /**
     * Places a mob spawner at the marker. Entity assignment is a later-phase
     * concern, so for now it is a default (pig) spawner.
     */
    protected void placeSpawner(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
        // TODO assign entity type from motif (Phase 4+).
    }
}
