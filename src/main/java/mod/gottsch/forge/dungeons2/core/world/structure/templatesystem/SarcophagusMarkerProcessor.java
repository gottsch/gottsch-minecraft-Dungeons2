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
import mod.gottsch.forge.dungeons2.core.block.entity.SarcophagusMarkerBlockEntity;
import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.TombContents;
import mod.gottsch.forge.dungeons2.core.event.EchelonSpawnEvent;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Turns an authored {@code dungeons2:sarcophagus_marker} into a sealed two-block tomb (#104) &mdash;
 * the authored twin of the {@code tombs} scheme slot.
 *
 * <h2>Why the work is in {@code finalizeProcessing}</h2>
 * <p>{@code processBlock} can only answer for the cell it is handed, and a tomb is two cells: the
 * marker's, which becomes the FOOT, and the one its {@code facing} points at, which becomes the
 * HEAD. Only {@code finalizeProcessing} sees the whole list, so the marker passes through
 * {@code processBlock} untouched &mdash; the pot marker's split &mdash; and both halves are written
 * here, into the returned list rather than the world, since nothing has been written yet.</p>
 *
 * <p>That list is the WHOLE piece on every chunk pass (vanilla clips only when writing), so every
 * pass builds the identical pair and each half lands in its own chunk. Randomness is seeded from
 * the marker's position alone, for the same reason.</p>
 *
 * <h2>The head cell must be authored air</h2>
 * <p>The head is not the marker's to take unless the template left it empty: a head written over a
 * wall, a column or another marker is a template fault, and the marker is left standing with a WARN
 * &mdash; as a chest marker that resolves to no table is. A head cell outside the template, or under
 * {@code structure_void}, is refused the same way: nothing guarantees what is there.</p>
 *
 * <h2>Rotation</h2>
 * <p>States here are UNROTATED &mdash; vanilla rotates this processor's output (see
 * {@code ChestMarkerProcessor#markerFacing}) &mdash; while positions are already in the world. So
 * both halves keep the marker's authored {@code facing} and only the head's POSITION applies the
 * placement's transform. Every half states {@code part}, {@code facing} and {@code open=false}
 * outright.</p>
 *
 * <h2>Contents, in order</h2>
 * <ol>
 *   <li>the marker's own {@code lootTable} / {@code guardian} / weights, each on its own;</li>
 *   <li>otherwise this entry's &mdash; a weighted draw from its lists, and its weights.</li>
 * </ol>
 * <p>An entry is per pool, and pools are per stratum, which is how an authored tomb gets the
 * contents and the block of its depth: the deepslate list names {@code deepslate_sarcophagus} and
 * the deep dead. What an entry cannot do is follow the floor inside one pool, for the chest marker's
 * reason &mdash; a processor is handed no {@code floorIndex}. A tomb that resolves to nothing it could
 * release is left as a marker.</p>
 *
 * <p>Level-independent: it reads nothing but the block list, so on a procedural piece it belongs in
 * {@code PieceProcessors}' unclipped pass &mdash; where it never fires anyway, since only a template
 * contains a marker.</p>
 *
 * @author Mark Gottschling on Sep 26, 2026
 */
public class SarcophagusMarkerProcessor extends StructureProcessor implements LevelIndependentProcessor {

    static final ResourceLocation DEFAULT_MARKER_BLOCK =
            new ResourceLocation(Dungeons.MOD_ID, "sarcophagus_marker");
    static final ResourceLocation DEFAULT_SARCOPHAGUS_BLOCK =
            new ResourceLocation("dungeonblocks", "stone_sarcophagus");
    /** Forge's persistent-data key on any block entity; see {@link EchelonSpawnEvent}. */
    static final String FORGE_DATA = "ForgeData";

    private final ResourceLocation markerBlock;
    private final ResourceLocation sarcophagusBlock;
    private final TombContents contents;

    public SarcophagusMarkerProcessor(ResourceLocation markerBlock, ResourceLocation sarcophagusBlock,
                                      TombContents contents) {
        this.markerBlock = markerBlock;
        this.sarcophagusBlock = sarcophagusBlock;
        this.contents = contents;
    }

    /**
     * Every field optional. The contents keys are {@link TombContents}' own, flat on the entry,
     * so an entry reads exactly like a motif's tomb band.
     */
    public static Codec<SarcophagusMarkerProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codecs.strictOptionalFieldOf(ResourceLocation.CODEC, "marker_block",
                        DEFAULT_MARKER_BLOCK).forGetter(p -> p.markerBlock),
                Codecs.strictOptionalFieldOf(ResourceLocation.CODEC, "sarcophagus_block",
                        DEFAULT_SARCOPHAGUS_BLOCK).forGetter(p -> p.sarcophagusBlock),
                TombContents.MAP_CODEC.forGetter(p -> p.contents)
        ).apply(instance, SarcophagusMarkerProcessor::new));
    }

    /** Passes the marker through; see the class note. */
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos piecePos,
                                                             BlockPos relativePos,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current,
                                                             StructurePlaceSettings settings) {
        return current;
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level, BlockPos piecePos, BlockPos originalPos,
            List<StructureTemplate.StructureBlockInfo> blocks,
            List<StructureTemplate.StructureBlockInfo> processedBlocks,
            StructurePlaceSettings settings) {

        Block marker = ForgeRegistries.BLOCKS.getValue(markerBlock);
        if (marker == null || marker == Blocks.AIR
                || processedBlocks.stream().noneMatch(info -> info.state().is(marker))) {
            return super.finalizeProcessing(level, piecePos, originalPos, blocks, processedBlocks,
                    settings);
        }
        Block tomb = ForgeRegistries.BLOCKS.getValue(sarcophagusBlock);
        BlockState tombState = tomb == null ? null : tomb.defaultBlockState();
        if (tomb == null || tomb == Blocks.AIR || !isTombShaped(tombState)) {
            // A pack pointing at a block that is not a two-block tomb gets markers, not half-tombs.
            Dungeons.LOGGER.warn("[D2-TOMB] sarcophagus_block {} is missing or has no part/facing;"
                    + " leaving every sarcophagus marker in this piece standing", sarcophagusBlock);
            return super.finalizeProcessing(level, piecePos, originalPos, blocks, processedBlocks,
                    settings);
        }
        List<StructureTemplate.StructureBlockInfo> out = convert(processedBlocks,
                state -> state.is(marker), tombState, settings.getRotation(), settings.getMirror(),
                contents, settings.getBoundingBox(), sarcophagusBlock.toString());
        return super.finalizeProcessing(level, piecePos, originalPos, blocks, out, settings);
    }

    /**
     * Every marker in {@code processed} rewritten to a foot, and the air cell ahead of it to a head.
     * Pure, apart from logging; package-private because Forge locks the block registry headlessly,
     * so a test hands in vanilla stand-ins for the marker and the tomb.
     *
     * @param box the chunk box; only the pass whose box holds a marker logs about it
     */
    static List<StructureTemplate.StructureBlockInfo> convert(
            List<StructureTemplate.StructureBlockInfo> processed, Predicate<BlockState> isMarker,
            BlockState tomb, Rotation rotation, Mirror mirror, TombContents pool,
            @Nullable BoundingBox box, String tombName) {

        Map<BlockPos, Integer> index = new HashMap<>(processed.size() * 2);
        for (int i = 0; i < processed.size(); i++) {
            index.put(processed.get(i).pos(), i);
        }
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processed);
        Set<BlockPos> claimed = new HashSet<>();
        for (int i = 0; i < processed.size(); i++) {
            StructureTemplate.StructureBlockInfo info = processed.get(i);
            if (!isMarker.test(info.state())) {
                continue;
            }
            boolean owner = box == null || box.isInside(info.pos());
            Direction facing = markerFacing(info.state());
            BlockPos headPos = info.pos().relative(rotation.rotate(mirror.mirror(facing)));
            Integer head = index.get(headPos);
            if (head == null || !processed.get(head).state().isAir() || claimed.contains(headPos)) {
                if (owner) {
                    Dungeons.LOGGER.warn("[D2-TOMB] marker at {} needs AIR at {} for the tomb's head"
                                    + " (found {}); leaving the marker standing",
                            info.pos().toShortString(), headPos.toShortString(),
                            head == null ? "no template cell" : processed.get(head).state());
                }
                continue;
            }
            TombContents.Drawn drawn = contentsFor(info.nbt(), pool, randomFor(info.pos()));
            if (!drawn.releasesAnything()) {
                if (owner) {
                    Dungeons.LOGGER.warn("[D2-TOMB] marker at {} resolved to a tomb that can never"
                            + " hold anything ({}); leaving the marker standing",
                            info.pos().toShortString(), drawn.describe());
                }
                continue;
            }
            CompoundTag origin = origin(info.nbt(), info.pos(), owner);

            out.set(i, new StructureTemplate.StructureBlockInfo(info.pos(),
                    half(tomb, facing, BedPart.FOOT), footTag(origin)));
            out.set(head, new StructureTemplate.StructureBlockInfo(headPos,
                    half(tomb, facing, BedPart.HEAD), headTag(drawn, origin)));
            claimed.add(headPos);
            if (owner) {
                // INFO for the chest probe's reason. The facing logged is the AUTHORED one; the
                // head's position is where the rotation shows.
                Dungeons.LOGGER.info("[D2-TOMB] marker -> {} foot at {} head at {} (authored facing"
                                + " {}, rot {}; {}{})", tombName, info.pos().toShortString(),
                        headPos.toShortString(), facing, rotation, drawn.describe(),
                        origin == null ? "" : ", scaled as floor "
                                + origin.getInt(EchelonSpawnEvent.FLOOR_INDEX));
            }
        }
        return out;
    }

    /**
     * The marker's own values over the pool's, then drawn. The marker names ONE table and ONE mob,
     * so each becomes a one-entry list and {@link TombContents#over} does the precedence &mdash; the
     * rule is written once, whichever route asks.
     */
    static TombContents.Drawn contentsFor(@Nullable CompoundTag nbt, TombContents pool,
                                          RandomSource random) {
        return markerContents(nbt).over(pool).draw(random);
    }

    /** What the marker itself states, as a {@link TombContents}; {@link TombContents#NONE} if nothing. */
    static TombContents markerContents(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return TombContents.NONE;
        }
        String table = nbt.getString(SarcophagusMarkerBlockEntity.LOOT_TABLE);
        String guardian = nbt.getString(SarcophagusMarkerBlockEntity.GUARDIAN);
        return new TombContents(
                table.isEmpty() ? Optional.empty()
                        : Optional.of(List.of(new ChestConfig.LootTableEntry(table, 1))),
                guardian.isEmpty() ? Optional.empty()
                        : Optional.of(List.of(new TombContents.GuardianEntry(guardian, 1))),
                weight(nbt, SarcophagusMarkerBlockEntity.LOOT_WEIGHT),
                weight(nbt, SarcophagusMarkerBlockEntity.GUARDIAN_WEIGHT),
                weight(nbt, SarcophagusMarkerBlockEntity.EMPTY_WEIGHT));
    }

    private static Optional<Integer> weight(CompoundTag nbt, String key) {
        return nbt.contains(key, Tag.TAG_ANY_NUMERIC)
                ? Optional.of(Math.max(0, nbt.getInt(key))) : Optional.empty();
    }

    /**
     * The echelon stamp, when the marker states BOTH {@code floorIndex} and {@code motif} &mdash; one
     * alone names no depth band, and says so, like the spawner marker.
     */
    static @Nullable CompoundTag origin(@Nullable CompoundTag nbt, BlockPos pos, boolean owner) {
        if (nbt == null) {
            return null;
        }
        boolean hasFloor = nbt.contains(SarcophagusMarkerBlockEntity.FLOOR_INDEX, Tag.TAG_ANY_NUMERIC);
        String motif = nbt.getString(SarcophagusMarkerBlockEntity.MOTIF);
        if (hasFloor && !motif.isEmpty()) {
            return EchelonSpawnEvent.originTag(motif,
                    nbt.getInt(SarcophagusMarkerBlockEntity.FLOOR_INDEX));
        }
        if ((hasFloor || !motif.isEmpty()) && owner) {
            Dungeons.LOGGER.warn("[D2-TOMB] marker at {} states only one of floorIndex/motif; both are"
                    + " needed to scale its guardian, so it is left to Stronger Mobs Below",
                    pos.toShortString());
        }
        return null;
    }

    /** One half: the tomb block, the marker's authored facing, this part, and closed. */
    static BlockState half(BlockState tomb, Direction facing, BedPart part) {
        BlockState state = tomb.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.BED_PART, part);
        // Stated, never inherited: a boolean's first value is true, and a tomb generated open
        // is one whose first opening never happens.
        return state.hasProperty(BlockStateProperties.OPEN)
                ? state.setValue(BlockStateProperties.OPEN, false) : state;
    }

    static boolean isTombShaped(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.hasProperty(BlockStateProperties.BED_PART);
    }

    /** The foot carries only the stamp; null when there is none, so the block's own entity stands. */
    static @Nullable CompoundTag footTag(@Nullable CompoundTag origin) {
        if (origin == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.put(FORGE_DATA, forgeData(origin));
        return tag;
    }

    /** The head carries the contents, which DungeonBlocks reads first, and the stamp. */
    static CompoundTag headTag(TombContents.Drawn drawn, @Nullable CompoundTag origin) {
        CompoundTag tag = drawn.tag();
        if (origin != null) {
            tag.put(FORGE_DATA, forgeData(origin));
        }
        return tag;
    }

    private static CompoundTag forgeData(CompoundTag origin) {
        CompoundTag forgeData = new CompoundTag();
        forgeData.put(EchelonSpawnEvent.ORIGIN, origin.copy());
        return forgeData;
    }

    private static Direction markerFacing(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /**
     * A random seeded from the marker's position alone, MIXED first: the first draw of a legacy
     * {@code RandomSource} barely moves across neighbouring seeds, which is the trap
     * {@code CounterItemPlanner} fell into, and markers sit a few blocks apart.
     */
    static RandomSource randomFor(BlockPos pos) {
        long z = pos.asLong() + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return RandomSource.create(z ^ (z >>> 31));
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return mod.gottsch.forge.dungeons2.core.setup.Registration.SARCOPHAGUS_PROCESSOR.get();
    }
}
