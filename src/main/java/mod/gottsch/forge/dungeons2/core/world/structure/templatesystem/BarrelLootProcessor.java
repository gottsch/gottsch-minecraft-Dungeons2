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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.function.Supplier;

/**
 * {@code dungeons2:barrel_loot}: gives an authored template's barrel a loot table, so every barrel
 * in a dungeon is worth a look &mdash; mostly junk or nothing, now and then something good. The
 * template twin of a barrel prop's {@code loot_table}.
 *
 * <p>Only a barrel with nothing of its own: one whose NBT already names a {@code LootTable}, or
 * holds {@code Items}, was stocked by its author and is left exactly as authored.</p>
 *
 * <p>The seed comes from the barrel's world position, mixed, and is never zero (vanilla rolls a
 * zero seed fresh on every open, so a player could re-roll the barrel by reloading).</p>
 *
 * <pre>{ "processor_type": "dungeons2:barrel_loot", "loot_table": "dungeons2:barrels/classic" }</pre>
 *
 * @author Mark Gottschling on 10/22/2026
 */
public class BarrelLootProcessor extends StructureProcessor implements LevelIndependentProcessor {

    private final ResourceLocation lootTable;
    private final Supplier<StructureProcessorType<?>> type;

    public BarrelLootProcessor(ResourceLocation lootTable, Supplier<StructureProcessorType<?>> type) {
        this.lootTable = lootTable;
        this.type = type;
    }

    public static Codec<BarrelLootProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("loot_table").forGetter(p -> p.lootTable)
        ).apply(instance, table -> new BarrelLootProcessor(table, type)));
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos piecePos,
            BlockPos relativePos, StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        CompoundTag stocked = stocked(current, lootTable);
        return stocked == null ? current
                : new StructureTemplate.StructureBlockInfo(current.pos(), current.state(), stocked);
    }

    /** The barrel's NBT with the table and a seed added, or null when this is not an unstocked barrel. */
    static CompoundTag stocked(StructureTemplate.StructureBlockInfo info, ResourceLocation lootTable) {
        if (!info.state().is(Blocks.BARREL)) {
            return null;
        }
        CompoundTag nbt = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        if (nbt.contains("LootTable", Tag.TAG_STRING)
                || !nbt.getList("Items", Tag.TAG_COMPOUND).isEmpty()) {
            return null;
        }
        long seed = SarcophagusMarkerProcessor.randomFor(info.pos()).nextLong();
        nbt.putString("LootTable", lootTable.toString());
        nbt.putLong("LootTableSeed", seed == 0L ? 1L : seed);
        return nbt;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
