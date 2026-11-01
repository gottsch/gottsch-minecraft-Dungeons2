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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.mojang.serialization.Codec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.HiddenDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@code dungeons2:pedestal_loot}: makes an authored template's pedestal roll its {@code LootTable}
 * when the template is placed into chunks that are already loaded.
 *
 * <p>A DungeonBlocks pedestal rolls from a block tick its block entity schedules in
 * {@code onLoad}. In ordinary generation that is fine: the chunk's block entities are loaded a tick
 * after the chunk is promoted, table and all. Placed into a LIVE chunk &mdash; {@code /d2-generate}
 * or {@code /place}, both through vanilla's {@code placeInWorld} &mdash; the block entity is created
 * and loaded by the {@code setBlock}, before vanilla has loaded the template's NBT into it, so it
 * schedules nothing and the pedestal stands empty until the chunk reloads. Mark saw exactly that on
 * the altar in {@code 13x17_alter_1} (2026-10-24). {@code DungeonPiece#scheduleLootRoll} closed the
 * same hole for procedural pedestals on 2026-09-28; this is the template twin.</p>
 *
 * <p>Scheduling ahead of the write is safe: the tick fires on a later server tick, by which time
 * the block is there and loaded. In ordinary generation it lands in the proto-chunk's tick list on
 * the same position and block as the {@code onLoad} one, and the two collapse into one. Only a cell
 * inside this pass's chunk box is scheduled, since a world-gen region cannot reach past it.</p>
 *
 * <pre>{ "processor_type": "dungeons2:pedestal_loot" }</pre>
 *
 * @author Mark Gottschling on Oct 24, 2026
 */
public class PedestalLootProcessor extends StructureProcessor {

    private final Supplier<StructureProcessorType<?>> type;

    public PedestalLootProcessor(Supplier<StructureProcessorType<?>> type) {
        this.type = type;
    }

    public static Codec<PedestalLootProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return Codec.unit(() -> new PedestalLootProcessor(type));
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos piecePos,
            BlockPos relativePos, StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        return current;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level, BlockPos piecePos, BlockPos originalPos,
            List<StructureTemplate.StructureBlockInfo> blocks,
            List<StructureTemplate.StructureBlockInfo> processedBlocks,
            StructurePlaceSettings settings) {
        BoundingBox box = settings.getBoundingBox();
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            if (rollsLoot(info, box)) {
                level.scheduleTick(info.pos(), info.state().getBlock(), 1);
            }
        }
        return super.finalizeProcessing(level, piecePos, originalPos, blocks, processedBlocks,
                settings);
    }

    /** A pedestal carrying a table to roll, in this pass's chunk. Package-private for the test. */
    static boolean rollsLoot(StructureTemplate.StructureBlockInfo info, @Nullable BoundingBox box) {
        CompoundTag nbt = info.nbt();
        return nbt != null && HiddenDoors.PEDESTAL.equals(nbt.getString("id"))
                && nbt.contains("LootTable", Tag.TAG_STRING)
                && (box == null || box.isInside(info.pos()));
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
