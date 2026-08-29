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
import mod.gottsch.forge.dungeons2.core.block.entity.PotMarkerBlockEntity;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.data.PotionEffectSpec;
import mod.gottsch.forge.dungeons2.core.world.structure.EntitySpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Backlog #56: turns an authored {@code dungeons2:pot_marker} into pot entities.
 *
 * <h2>Why this cannot work the way the chest marker does</h2>
 * <p>{@code ChestMarkerProcessor} does its whole job in {@code processBlock}, because a chest is a
 * BLOCK: it returns a chest state and vanilla writes it. A pot is a {@code PotEntity}, so there is
 * no state to return, and {@code processBlock} is handed only a {@link net.minecraft.world.level.LevelReader}
 * &mdash; which cannot spawn anything. The work therefore splits:</p>
 * <ol>
 *   <li>{@code processBlock} passes the marker through <strong>unchanged</strong>, so its
 *       per-cell NBT survives into the processed list where the next step can still read it.</li>
 *   <li>{@code finalizeProcessing} has a real {@link ServerLevelAccessor}, spawns the pots, and
 *       returns a list with every marker rewritten to air.</li>
 * </ol>
 *
 * <p>The rewrite has to happen in the returned list rather than by setting blocks, because
 * {@code finalizeProcessing} runs <strong>before</strong> vanilla writes anything &mdash; the same
 * fact {@code DecorationSweepProcessor} relies on. Calling {@code setBlock} here would be undone by
 * the write that follows, and the marker would stand in the finished dungeon.</p>
 *
 * <h2>Spawning twice is the hazard, and the box is the answer</h2>
 * <p>A piece's placement runs once per overlapping chunk, so a room straddling four of them runs
 * this four times. Writing a block four times is a no-op; spawning an entity four times is four
 * pots. Every spawn is therefore clipped to {@code settings.getBoundingBox()}, which during worldgen
 * is the chunk being generated &mdash; the same clip {@code DungeonPiece#placeEntities} applies for
 * the same reason, and the hazard {@link EntityPlacement} was documented with from the start.</p>
 *
 * <p><strong>Randomness is derived from the marker's position</strong>, not from a field or a
 * shared source. A processor instance is shared across placements, so per-instance state would leak
 * between dungeons; and each chunk pass must roll the SAME pot for the clip above to be the only
 * thing deciding whether it spawns. Position-seeding gives both, and it is what
 * {@code ChestMarkerProcessor.lootSeed} already does for loot.</p>
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
public class PotMarkerProcessor extends StructureProcessor {

    static final ResourceLocation DEFAULT_MARKER_BLOCK =
            new ResourceLocation(Dungeons.MOD_ID, "pot_marker");

    private final ResourceLocation markerBlock;
    private final Optional<ResourceLocation> lootTable;
    private final Optional<ResourceLocation> defaultVariant;

    public PotMarkerProcessor(ResourceLocation markerBlock, Optional<ResourceLocation> lootTable,
                              Optional<ResourceLocation> defaultVariant) {
        this.markerBlock = markerBlock;
        this.lootTable = lootTable;
        this.defaultVariant = defaultVariant;
    }

    /**
     * {@code loot_table} and {@code variant} are pool-level fallbacks for markers that name none of
     * their own, exactly as the chest processor's {@code loot_table} is. Both optional: a template
     * whose every marker is fully configured needs neither, and demanding them would force an author
     * to invent values nothing reads.
     *
     * <p><strong>{@code Optional}, not a null default.</strong> {@code optionalFieldOf(name, null)}
     * looks like it means "absent is null" and does not: when the field really is missing, DFU wraps
     * the default in {@code Optional.of} and throws NPE out of the decode, with a stack that names
     * the JSON file rather than the field. That is not hypothetical &mdash; it is what the first
     * version of this codec did, and it failed 60 tests the moment an entry omitted both fields.</p>
     */
    public static Codec<PotMarkerProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("marker_block", DEFAULT_MARKER_BLOCK)
                        .forGetter(p -> p.markerBlock),
                ResourceLocation.CODEC.optionalFieldOf("loot_table")
                        .forGetter(p -> p.lootTable),
                ResourceLocation.CODEC.optionalFieldOf("variant")
                        .forGetter(p -> p.defaultVariant)
        ).apply(instance, PotMarkerProcessor::new));
    }

    /**
     * Passes the marker through untouched. See the class note: its NBT is the whole payload, and
     * {@code finalizeProcessing} is the only place that can act on it.
     */
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            net.minecraft.world.level.LevelReader level, BlockPos piecePos, BlockPos relativePos,
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

        var marker = ForgeRegistries.BLOCKS.getValue(markerBlock);
        if (marker == null) {
            return super.finalizeProcessing(level, piecePos, originalPos, blocks, processedBlocks,
                    settings);
        }

        BoundingBox box = settings.getBoundingBox();
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(processedBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            if (!info.state().is(marker)) {
                out.add(info);
                continue;
            }
            // Air whether or not anything spawns: the marker is authoring scaffolding, and a
            // probability roll that comes up empty must leave an empty cell rather than a marker.
            out.add(new StructureTemplate.StructureBlockInfo(info.pos(),
                    Blocks.AIR.defaultBlockState(), null));

            if (box != null && !box.isInside(info.pos())) {
                continue;   // Another chunk's pass owns this one. See the class note.
            }
            spawnAt(level, info);
        }
        return super.finalizeProcessing(level, piecePos, originalPos, blocks, out, settings);
    }

    /** Rolls one marker and spawns whatever it asked for. */
    private void spawnAt(ServerLevelAccessor level, StructureTemplate.StructureBlockInfo info) {
        CompoundTag nbt = info.nbt();
        BlockPos pos = info.pos();
        RandomSource random = RandomSource.create(seedFor(pos));

        float probability = nbt != null && nbt.contains(PotMarkerBlockEntity.PROBABILITY)
                ? nbt.getFloat(PotMarkerBlockEntity.PROBABILITY) : 1.0F;
        if (random.nextFloat() >= probability) {
            return;
        }

        List<PotMarkerBlockEntity.Variant> variants = variants(nbt);
        if (variants.isEmpty()) {
            variants = defaultVariants();
        }
        if (variants.isEmpty()) {
            Dungeons.LOGGER.warn("[D2-POT] marker at {} names no pot variant and the processor has"
                    + " no default -- nothing spawned", pos.toShortString());
            return;
        }
        String table = markerLootTable(nbt);
        if (table == null) {
            table = lootTable.map(ResourceLocation::toString).orElse(null);
        }
        if (table == null) {
            // Not fatal, but it is always a mistake: PotEntity#dropLoot returns early on a null
            // table with no fallback to the entity type's own, so this pot shatters into nothing.
            // PotConfig makes the same field required for the procedural path and says why.
            Dungeons.LOGGER.warn("[D2-POT] marker at {} resolved to no loot table -- its pot(s) will"
                    + " shatter into nothing", pos.toShortString());
        }
        List<PotionEffectSpec> effects = effects(nbt);

        int min = nbt != null && nbt.contains(PotMarkerBlockEntity.MIN_COUNT)
                ? Math.max(0, nbt.getInt(PotMarkerBlockEntity.MIN_COUNT)) : 1;
        int max = nbt != null && nbt.contains(PotMarkerBlockEntity.MAX_COUNT)
                ? Math.max(0, nbt.getInt(PotMarkerBlockEntity.MAX_COUNT)) : 1;
        max = Math.max(min, max);
        int count = min == max ? min : min + random.nextInt(max - min + 1);

        for (int i = 0; i < count; i++) {
            EntityPlacement placement = new EntityPlacement(pos.getX(), pos.getY(), pos.getZ(),
                    pick(variants, random), random.nextFloat() * 360.0F, table, seedFor(pos));
            placement.setEffects(effects);
            // Position is absolute here, unlike the floor-local coords EntityPlacement carries out
            // of the room planners -- so the offsets are passed straight through as world coords.
            EntitySpawner.spawn(level, placement, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    // Package-private, not private: Forge LOCKS the block registry headlessly, so the marker
    // BLOCK cannot be exercised in a unit test -- but the authored NBT vocabulary it carries is
    // where the bugs would be, and these read it. See PotMarkerProcessorTest.
    static List<PotMarkerBlockEntity.Variant> variants(CompoundTag nbt) {
        List<PotMarkerBlockEntity.Variant> out = new ArrayList<>();
        if (nbt != null && nbt.contains(PotMarkerBlockEntity.VARIANTS, Tag.TAG_LIST)) {
            ListTag list = nbt.getList(PotMarkerBlockEntity.VARIANTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String entity = entry.getString(PotMarkerBlockEntity.ENTITY);
                if (entity.isEmpty()) {
                    continue;
                }
                int weight = entry.contains(PotMarkerBlockEntity.WEIGHT)
                        ? Math.max(1, entry.getInt(PotMarkerBlockEntity.WEIGHT)) : 1;
                out.add(new PotMarkerBlockEntity.Variant(entity, weight));
            }
        }
        return out;
    }

    static List<PotionEffectSpec> effects(CompoundTag nbt) {
        List<PotionEffectSpec> out = new ArrayList<>();
        if (nbt != null && nbt.contains(PotMarkerBlockEntity.EFFECTS, Tag.TAG_LIST)) {
            ListTag list = nbt.getList(PotMarkerBlockEntity.EFFECTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                String effect = entry.getString(PotMarkerBlockEntity.EFFECT);
                if (effect.isEmpty()) {
                    continue;
                }
                out.add(new PotionEffectSpec(effect,
                        entry.getInt(PotMarkerBlockEntity.AMPLIFIER),
                        entry.contains(PotMarkerBlockEntity.DURATION)
                                ? Math.max(1, entry.getInt(PotMarkerBlockEntity.DURATION)) : 200));
            }
        }
        return out;
    }

    static String pick(List<PotMarkerBlockEntity.Variant> variants, RandomSource random) {
        int total = variants.stream().mapToInt(PotMarkerBlockEntity.Variant::weight).sum();
        int roll = random.nextInt(total);
        for (PotMarkerBlockEntity.Variant variant : variants) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant.entity();
            }
        }
        return variants.get(variants.size() - 1).entity();
    }

    static String markerLootTable(CompoundTag nbt) {
        if (nbt == null || !nbt.contains(PotMarkerBlockEntity.LOOT_TABLE)) {
            return null;
        }
        String table = nbt.getString(PotMarkerBlockEntity.LOOT_TABLE);
        return table.isEmpty() ? null : table;
    }

    /** Never 0: a 0 {@code LootTableSeed} means "roll fresh when broken", which worldgen does not want. */
    static long seedFor(BlockPos pos) {
        long seed = pos.asLong();
        return seed == 0L ? 1L : seed;
    }

    /** The variants a marker falls back to when it names none, or empty when the pool named none either. */
    List<PotMarkerBlockEntity.Variant> defaultVariants() {
        return defaultVariant
                .map(id -> List.of(new PotMarkerBlockEntity.Variant(id.toString(), 1)))
                .orElseGet(List::of);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return mod.gottsch.forge.dungeons2.core.setup.Registration.POT_PROCESSOR.get();
    }
}
