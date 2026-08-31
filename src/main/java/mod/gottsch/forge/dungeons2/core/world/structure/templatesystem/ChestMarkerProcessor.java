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
import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.integration.TreasureIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Swaps an authored {@code dungeons2:chest_marker} for a chest with loot. Backlog #48 step 3, the
 * authored half of the two routes #10 established.
 *
 * <h2>Where the loot table comes from, in order</h2>
 * <ol>
 *   <li>the marker's own {@code lootTable}, read off its block entity NBT &mdash; per cell, so one
 *       special chest in a template of ordinary ones costs one line;</li>
 *   <li>otherwise a weighted draw over this processor's {@code loot_tables} list &mdash; per pool,
 *       and what a template full of ordinary chests relies on.</li>
 * </ol>
 *
 * <h2>Why the pool default is a WEIGHTED LIST and not one table</h2>
 * <p>Because the procedural route's already is. A motif declares {@code chestLootByFloorIndex} as
 * bands of weighted {@code {lootTable, weight}} entries &mdash; "mostly the common table,
 * occasionally something better" &mdash; and a single fixed id here would have made the authored
 * route the one place in the mod where a chest cannot say that. The entries are literally
 * {@link ChestConfig.LootTableEntry} and the draw is {@link ChestConfig.LootTableEntry#pick}, so
 * both routes read the same JSON shape and share the arithmetic.</p>
 *
 * <p><strong>What it still cannot do is follow depth.</strong> A {@link StructureProcessor} is
 * handed no {@code floorIndex} &mdash; it sees a block, a position and a placement, and floorIndex
 * is a separate axis from world Y &mdash; so a processor entry cannot select the band the way
 * {@code MotifConfig#chestBandFor} does for a procedural room. The list here is therefore one blend
 * for the whole motif, and a template that wants depth-correct loot names the table on the marker
 * itself. Exactly the same limitation, accepted for exactly the same reason, as
 * {@code dungeons2:spawner}'s single {@code mob_set} against the motif's {@code MobSetBand}s.</p>
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
 * <h2>The Treasure2 branch, per marker</h2>
 * <p>A marker with {@code treasure: true} asks Treasure2 for a real chest &mdash; its rarity draw,
 * its chest block, its locks, its table, and its cache entry. Everything else falls through to the
 * ordinary chest above, so a template can hold three plain chests and one Treasure2 boss chest and
 * says so in one line. With Treasure2 absent the flag is inert and every marker takes the D2 route,
 * which is why {@code lootTable} is still worth authoring on a {@code treasure} marker: it is what
 * that chest holds when the mod is not installed.</p>
 *
 * <p><strong>This processor is deliberately NOT {@code LevelIndependentProcessor}.</strong> It was,
 * until the Treasure2 branch: {@code TreasureApi.generateChest} reads the level (the fluid state
 * under the chest, and the biome it records), so the promise that interface makes &mdash; "reads
 * nothing but the block handed to it, so it is safe to run unclipped over a whole piece" &mdash;
 * stopped being true. Leaving it on would let a pack that put this processor in a weathering list
 * read outside the chunk box during worldgen, which is illegal. See {@code PieceProcessors}.</p>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public class ChestMarkerProcessor extends StructureProcessor {

    static final ResourceLocation DEFAULT_MARKER_BLOCK =
            new ResourceLocation(Dungeons.MOD_ID, "chest_marker");
    /** Vanilla's keys; see {@code RoomChestGenerator}, which writes the same pair procedurally. */
    static final String LOOT_TABLE_TAG = "LootTable";
    static final String LOOT_TABLE_SEED_TAG = "LootTableSeed";

    private final List<ChestConfig.LootTableEntry> lootTables;
    private final ResourceLocation markerBlock;
    private final ResourceLocation chestBlock;

    public ChestMarkerProcessor(List<ChestConfig.LootTableEntry> lootTables,
                                ResourceLocation markerBlock, ResourceLocation chestBlock) {
        this.lootTables = lootTables;
        this.markerBlock = markerBlock;
        this.chestBlock = chestBlock;
    }

    /**
     * {@code loot_tables} is optional here, unlike the spawner processor's {@code mob_set}: a
     * template whose markers all name their own table needs no pool-level default, and requiring one
     * would force an author to invent a table nothing draws from. Absent means an empty list, which
     * is the "every marker speaks for itself" configuration -- not a silent fallback to something.
     *
     * <p><strong>The field was {@code loot_table}, a single id, until 2026-08-30</strong> (#61).
     * Renamed rather than kept alongside the list: nothing shipped set it -- the one file that did,
     * {@code classic_chests.json}, was referenced by no pool and has been retired -- so there was no
     * compatibility to preserve, and two ways to say the same thing is what the closed schema exists
     * to prevent.</p>
     *
     * <p><strong>Optionality itself was broken until 2026-08-29.</strong> This read
     * {@code optionalFieldOf("loot_table", null)}, which looks like it means "absent is null" and
     * does not: when the field really is missing, DFU wraps the default in {@code Optional.of} and
     * throws NPE out of the decode. So a processor list that took this entry's documented option
     * &mdash; omitting the field &mdash; crashed the whole list, with a stack naming the JSON file
     * rather than the field. Nothing caught it because the one shipped entry always supplied the
     * field; it surfaced when #56's processor copied the pattern and shipped an entry with no fields
     * at all. The list form below is immune by construction: its default is a real empty list, not
     * a null dressed as one.</p>
     *
     * <h2>Why all three fields are {@code Codecs.strictOptionalFieldOf}</h2>
     * <p>DFU's own {@code optionalFieldOf(name, default)} swallows a decode FAILURE and hands back
     * the default, so it cannot tell "the author said nothing" from "the author said something
     * malformed". On this processor that lenience produces #61's bug from the other direction: a
     * {@code loot_tables} whose value is misspelled or the wrong shape would silently become the
     * empty list, every marker would resolve to no table, and the whole template would generate with
     * marker blocks standing in it &mdash; reported by nothing louder than a WARN per chest. Strict
     * makes it a load error naming the field, which is the {@code #31} closed-schema decision
     * applied to a processor entry.</p>
     */
    public static Codec<ChestMarkerProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codecs.strictOptionalFieldOf(ChestConfig.LootTableEntry.CODEC.listOf(),
                        "loot_tables", List.of()).forGetter(p -> p.lootTables),
                Codecs.strictOptionalFieldOf(ResourceLocation.CODEC, "marker_block",
                        DEFAULT_MARKER_BLOCK).forGetter(p -> p.markerBlock),
                Codecs.strictOptionalFieldOf(ResourceLocation.CODEC, "chest_block",
                        new ResourceLocation("minecraft:chest")).forGetter(p -> p.chestBlock)
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
            // settings.getRandom(pos) is seeded from the block's own position, so the draw is
            // deterministic per cell: the same marker rolls the same table on every pass over it,
            // which is what makes this safe in the clipped pass PieceProcessors puts it in (a piece
            // spanning two chunks is processed once per chunk). Two markers in one template still
            // draw independently, which is the point of a weighted list.
            table = ChestConfig.LootTableEntry.pick(lootTables, settings.getRandom(current.pos()));
        }
        if (table == null || table.isEmpty()) {
            // Left standing on purpose -- see the class note. Logged at WARN because a template that
            // reaches here is misconfigured and nothing downstream will say so.
            Dungeons.LOGGER.warn("[D2-CHEST] marker at {} resolved to no loot table; leaving the"
                    + " marker in place rather than generating an empty chest", current.pos().toShortString());
            return current;
        }

        // Treasure2 first, when this individual marker asked for it. Empty means the mod is absent
        // (or could not build one), and the fall-through below is then the whole point rather than a
        // failure -- the dungeon has to generate the same shape either way.
        if (wantsTreasureChest(current)) {
            Direction facing = markerFacing(current, settings);
            Optional<StructureTemplate.StructureBlockInfo> treasure =
                    TreasureIntegration.generateChest(level, current.pos(), facing,
                            settings.getRandom(current.pos()));
            if (treasure.isPresent()) {
                Dungeons.LOGGER.info("[D2-CHEST] {} -> treasure2 chest at {} (facing {})",
                        markerBlock, current.pos().toShortString(), facing);
                return treasure.get();
            }
        }

        BlockState chest = chestState(current, settings);
        if (chest == null) {
            Dungeons.LOGGER.warn("[D2-CHEST] chest block {} does not exist; leaving the marker at {}",
                    chestBlock, current.pos().toShortString());
            return current;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString(LOOT_TABLE_TAG, table);
        // Non-zero, for the reason RoomChestGenerator documents: vanilla reads 0 as "roll fresh on
        // open", which makes a structure chest re-rollable by reloading the save. The position is
        // the seed source so one template placed twice in a dungeon does not hold the same items.
        tag.putLong(LOOT_TABLE_SEED_TAG, lootSeed(current.pos()));

        // INFO, not debug, and for the reason the spawner probe was promoted: the mod's [logging]
        // level ships at "info", so a debug probe is off for every user AND for whoever is trying to
        // verify the feature -- which is how this line came to be missing from a log that had 214
        // [D2-SPAWNER] lines in it. One line per conversion, at the position it happened.
        // Facing and rotation are logged because the piece's rotation is NOT the chest's facing, and
        // only the second one is the thing worth checking. The piece rotation was inferable from the
        // vector between two markers at known template cells; the facing was inferable from nothing
        // at all, so verifying it meant walking to the chest. Printing both makes the transform
        // readable in one line: authored facing, rotation applied, facing that came out.
        Dungeons.LOGGER.info("[D2-CHEST] {} -> {} at {} (table {}, marker facing {} after rot {} -> chest facing {})",
                markerBlock, chestBlock, current.pos().toShortString(), table,
                // NOT the authored facing: StructureTemplate rotated it before we were handed it.
                current.state().hasProperty(ChestMarkerBlock.FACING)
                        ? current.state().getValue(ChestMarkerBlock.FACING) : "none",
                settings.getRotation(), chest.hasProperty(ChestMarkerBlock.FACING)
                        ? chest.getValue(ChestMarkerBlock.FACING) : "none");
        return new StructureTemplate.StructureBlockInfo(current.pos(), chest, tag);
    }

    /** Whether this individual marker opted in to a Treasure2 chest. */
    private static boolean wantsTreasureChest(StructureTemplate.StructureBlockInfo current) {
        CompoundTag nbt = current.nbt();
        return nbt != null && nbt.getBoolean(ChestMarkerBlockEntity.TREASURE);
    }

    /**
     * The facing to emit &mdash; the marker's own, with <strong>no rotation applied here</strong>.
     *
     * <h2>Vanilla rotates this processor's OUTPUT, not its input</h2>
     * <p>Confirmed in game 2026-08-18, by comparing the two ends rather than reasoning about
     * either. At a {@code CLOCKWISE_90} placement the marker arrived reading {@code south} &mdash;
     * its authored value, unrotated &mdash; this method emitted {@code south}, and the block that
     * landed in the world reads {@code facing: west}. South rotated clockwise is west, so the
     * rotation was applied to what this processor returned.</p>
     *
     * <p>So rotating here produced a <strong>double</strong> rotation: invisible at {@code NONE},
     * ninety degrees out everywhere else. A chest wrong <em>by</em> the rotation reads as a chest
     * that ignored it, which is why the bug was reported as "the chest isn't rotated".</p>
     *
     * <p>Two wrong explanations preceded the right one, and both were reasoned rather than measured:
     * first that the rotation simply was not being applied, then that the <em>input</em> arrived
     * pre-rotated. The log line above disproved the second (the marker reads its authored value at
     * a rotated placement) and the F3 screen settled the first. <strong>The processor's own log
     * cannot settle this</strong> &mdash; it prints what this code emits, which is upstream of the
     * transform in question. Only the placed block can.</p>
     *
     * <p>Treasure2 is not a counter-example. {@code IChestSubprocessor.process} rotates manually and
     * says why: its chests "do not extend vanilla chests and aren't recognized for rotation" &mdash;
     * i.e. their own {@code rotate()} does not carry their facing, so vanilla's pass over the output
     * does nothing and T2 must do it itself. {@link ChestMarkerBlock} overrides {@code rotate()},
     * so for this marker vanilla's pass does the work. Copying T2's code without its condition is
     * exactly how the double got written.</p>
     */
    private static Direction markerFacing(StructureTemplate.StructureBlockInfo current,
                                          StructurePlaceSettings settings) {
        return current.state().hasProperty(ChestMarkerBlock.FACING)
                ? current.state().getValue(ChestMarkerBlock.FACING)
                : Direction.NORTH;
    }

    /**
     * Completes the cache entry for every Treasure2 chest this processor placed.
     *
     * <p>{@code processBlock} only has a {@link LevelReader}, which cannot name its dimension, so a
     * Treasure2 chest is cached without one and stays that way unless this runs &mdash; the contract
     * {@code TreasureApi.generateChest} documents. Scanning the processed blocks rather than
     * remembering positions keeps this stateless, which matters because a structure processor
     * instance is shared across placements.</p>
     */
    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level, BlockPos piecePos, BlockPos originalPos,
            List<StructureTemplate.StructureBlockInfo> blocks,
            List<StructureTemplate.StructureBlockInfo> processedBlocks,
            StructurePlaceSettings settings) {

        if (TreasureIntegration.isLoaded()) {
            for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
                if (TreasureIntegration.isTreasureChest(info.state())) {
                    TreasureIntegration.finalizeChest(level, info.pos());
                }
            }
        }
        return super.finalizeProcessing(level, piecePos, originalPos, blocks, processedBlocks, settings);
    }

    /** The chest state, facing where the marker faced, rotated by the placement. */
    private BlockState chestState(StructureTemplate.StructureBlockInfo current,
                                  StructurePlaceSettings settings) {
        var block = ForgeRegistries.BLOCKS.getValue(chestBlock);
        if (block == null || block == Blocks.AIR) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        Direction facing = markerFacing(current, settings);
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
