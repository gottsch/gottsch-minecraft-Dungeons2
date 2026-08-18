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
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.ChestMarkerBlock;
import mod.gottsch.forge.dungeons2.core.block.entity.ChestMarkerBlockEntity;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Swaps an authored {@code dungeons2:chest_marker} for a chest with loot. Backlog #48 step 3, the
 * authored half of the two routes #10 established.
 *
 * <h2>Where the loot table comes from, in order</h2>
 * <ol>
 *   <li>the marker's own {@code lootTable}, read off its block entity NBT &mdash; per cell, so one
 *       special chest in a template of ordinary ones costs one line;</li>
 *   <li>otherwise this processor's {@code loot_table} field &mdash; per pool, and what a template
 *       full of ordinary chests relies on.</li>
 * </ol>
 *
 * <p>There is deliberately <strong>no third fallback</strong>. A marker that resolves to no table at
 * all is left in place rather than becoming an empty chest, and logs &mdash; see below.</p>
 *
 * <h2>Why the marker survives a failure instead of becoming a plain chest</h2>
 * <p>An empty chest is indistinguishable from a looted one, so a misconfigured template would ship
 * as "the chests in this room are always empty" and read as a design choice. The marker block left
 * standing is unmistakably wrong, and it is visible from across the room.</p>
 *
 * <h2>Facing</h2>
 * <p>Taken from the marker's own {@code FACING} and rotated by the placement, exactly as a vanilla
 * chest in the same template would be. The author orients the marker; the pool's rotation does the
 * rest.</p>
 *
 * <p>{@code treasure} on the marker is read by {@link ChestMarkerBlockEntity} and <strong>not acted
 * on here</strong> &mdash; that is #48 step 4. A template may already carry it.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public class ChestMarkerProcessor extends StructureProcessor implements LevelIndependentProcessor {

    static final ResourceLocation DEFAULT_MARKER_BLOCK =
            new ResourceLocation(Dungeons.MOD_ID, "chest_marker");
    /** Vanilla's keys; see {@code RoomChestGenerator}, which writes the same pair procedurally. */
    static final String LOOT_TABLE_TAG = "LootTable";
    static final String LOOT_TABLE_SEED_TAG = "LootTableSeed";

    private final ResourceLocation lootTable;
    private final ResourceLocation markerBlock;
    private final ResourceLocation chestBlock;

    public ChestMarkerProcessor(ResourceLocation lootTable, ResourceLocation markerBlock,
                                ResourceLocation chestBlock) {
        this.lootTable = lootTable;
        this.markerBlock = markerBlock;
        this.chestBlock = chestBlock;
    }

    /**
     * {@code loot_table} is optional here, unlike the spawner processor's {@code mob_set}: a
     * template whose markers all name their own table needs no pool-level default, and requiring one
     * would force an author to invent a table nothing draws from.
     */
    public static Codec<ChestMarkerProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("loot_table", null).forGetter(p -> p.lootTable),
                ResourceLocation.CODEC.optionalFieldOf("marker_block", DEFAULT_MARKER_BLOCK)
                        .forGetter(p -> p.markerBlock),
                ResourceLocation.CODEC.optionalFieldOf("chest_block",
                                new ResourceLocation("minecraft:chest"))
                        .forGetter(p -> p.chestBlock)
        ).apply(instance, ChestMarkerProcessor::new));
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos piecePos,
                                                             BlockPos relativePos,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current,
                                                             StructurePlaceSettings settings) {
        if (!isChestMarker(current)) {
            return current;
        }

        String table = markerLootTable(current);
        if (table == null) {
            table = lootTable == null ? null : lootTable.toString();
        }
        if (table == null || table.isEmpty()) {
            // Left standing on purpose -- see the class note. Logged at WARN because a template that
            // reaches here is misconfigured and nothing downstream will say so.
            Dungeons.LOGGER.warn("[D2-CHEST] marker at {} resolved to no loot table; leaving the"
                    + " marker in place rather than generating an empty chest", current.pos());
            return current;
        }

        BlockState chest = chestState(current, settings);
        if (chest == null) {
            Dungeons.LOGGER.warn("[D2-CHEST] chest block {} does not exist; leaving the marker at {}",
                    chestBlock, current.pos());
            return current;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString(LOOT_TABLE_TAG, table);
        // Non-zero, for the reason RoomChestGenerator documents: vanilla reads 0 as "roll fresh on
        // open", which makes a structure chest re-rollable by reloading the save. The position is
        // the seed source so one template placed twice in a dungeon does not hold the same items.
        tag.putLong(LOOT_TABLE_SEED_TAG, lootSeed(current.pos()));

        Dungeons.LOGGER.debug("[D2-CHEST] {} -> {} at {} (table {})",
                markerBlock, chestBlock, current.pos(), table);
        return new StructureTemplate.StructureBlockInfo(current.pos(), chest, tag);
    }

    /** The chest state, facing where the marker faced, rotated by the placement. */
    private BlockState chestState(StructureTemplate.StructureBlockInfo current,
                                  StructurePlaceSettings settings) {
        var block = ForgeRegistries.BLOCKS.getValue(chestBlock);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        Direction facing = current.state().hasProperty(ChestMarkerBlock.FACING)
                ? settings.getRotation().rotate(current.state().getValue(ChestMarkerBlock.FACING))
                : Direction.NORTH;
        // A chest_block a pack points at might not have FACING at all (a barrel's is a full
        // DirectionProperty, a shulker box has none), so this asks rather than assumes.
        for (var property : state.getProperties()) {
            if (property.getName().equals("facing") && property.getPossibleValues().contains(facing)) {
                return state.setValue(ChestMarkerBlock.FACING, facing);
            }
        }
        return state;
    }

    /** The table this individual marker names, or null when it defers to the pool's. */
    private static String markerLootTable(StructureTemplate.StructureBlockInfo current) {
        CompoundTag nbt = current.nbt();
        if (nbt == null || !nbt.contains(ChestMarkerBlockEntity.LOOT_TABLE)) {
            return null;
        }
        String table = nbt.getString(ChestMarkerBlockEntity.LOOT_TABLE);
        return table.isEmpty() ? null : table;
    }

    static long lootSeed(BlockPos pos) {
        long seed = pos.asLong();
        return seed == 0L ? 1L : seed;
    }

    private boolean isChestMarker(StructureTemplate.StructureBlockInfo current) {
        var block = ForgeRegistries.BLOCKS.getValue(markerBlock);
        return block != null && current.state().is(block);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return mod.gottsch.forge.dungeons2.core.setup.Registration.CHEST_PROCESSOR.get();
    }
}
