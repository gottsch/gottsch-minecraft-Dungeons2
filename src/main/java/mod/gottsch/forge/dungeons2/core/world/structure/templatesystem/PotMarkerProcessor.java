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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
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
 * <p>The clip is on each POT's cell, not the marker's. A marker asking for several pots packs them
 * round itself ({@link #arrange}), a group of big pots overhangs into neighbouring blocks, and so a
 * group can straddle a chunk edge. That
 * works because the list {@code finalizeProcessing} receives is the whole piece in every pass
 * &mdash; vanilla applies the box only when it writes &mdash; so every pass plans the identical
 * group and each pot is spawned by exactly one of them, into the chunk being generated.</p>
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

        if (processedBlocks.stream().noneMatch(info -> info.state().is(marker))) {
            return super.finalizeProcessing(level, piecePos, originalPos, blocks, processedBlocks,
                    settings);
        }

        // The WHOLE piece, not this chunk's slice: vanilla's placeInWorld hands processBlockInfos
        // the full palette and applies the chunk box only afterwards, in its write loop. So every
        // chunk pass sees the same cells and plans the same group -- see groupCells.
        Map<BlockPos, BlockState> states = new HashMap<>(processedBlocks.size() * 2);
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            states.put(info.pos(), info.state());
        }
        List<Spot> taken = new ArrayList<>();

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

            // Planned in EVERY pass, spawned only in the pass whose box holds each pot's own cell.
            // A marker near a chunk edge can group across it, so no single pass owns the whole
            // group; planning everywhere is what keeps the taken spots -- and the random draws --
            // the same in each pass, which is what makes the per-cell clip exactly-once.
            boolean owner = box == null || box.isInside(info.pos());
            Predicate<BlockPos> canStand = cell -> standsFree(states.get(cell),
                    states.get(cell.below()));
            for (EntityPlacement placement : plan(info, canStand, taken, owner)) {
                BlockPos cell = new BlockPos(placement.getX(), placement.getY(), placement.getZ());
                if (box != null && !box.isInside(cell)) {
                    continue;   // Another chunk's pass spawns this one. See the class note.
                }
                // Position is absolute here, unlike the floor-local coords EntityPlacement carries
                // out of the room planners -- so the cell is passed straight through as world coords.
                EntitySpawner.spawn(level, placement, cell.getX(), cell.getY(), cell.getZ());
            }
        }
        return super.finalizeProcessing(level, piecePos, originalPos, blocks, out, settings);
    }

    /**
     * Rolls one marker into the pots it asks for, one {@link EntityPlacement} per cell. Pure apart
     * from logging, and identical in every chunk pass; {@code owner} only stops each warning being
     * repeated once per chunk the piece spans.
     */
    private List<EntityPlacement> plan(StructureTemplate.StructureBlockInfo info,
                                       Predicate<BlockPos> canStand, List<Spot> taken,
                                       boolean owner) {
        CompoundTag nbt = info.nbt();
        BlockPos pos = info.pos();
        RandomSource random = RandomSource.create(seedFor(pos));

        float probability = nbt != null && nbt.contains(PotMarkerBlockEntity.PROBABILITY)
                ? nbt.getFloat(PotMarkerBlockEntity.PROBABILITY) : 1.0F;
        if (random.nextFloat() >= probability) {
            return List.of();
        }

        List<PotMarkerBlockEntity.Variant> variants = variants(nbt);
        if (variants.isEmpty()) {
            variants = defaultVariants();
        }
        if (variants.isEmpty()) {
            if (owner) {
                Dungeons.LOGGER.warn("[D2-POT] marker at {} names no pot variant and the processor"
                        + " has no default -- nothing spawned", pos.toShortString());
            }
            return List.of();
        }
        String table = markerLootTable(nbt);
        if (table == null) {
            table = lootTable.map(ResourceLocation::toString).orElse(null);
        }
        if (table == null && owner) {
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

        // Variants first: the group's spacing comes from the widest pot actually drawn, so a
        // cluster of flasks packs into one block where a cluster of big pots spreads.
        List<String> ids = new ArrayList<>(count);
        double width = 0.0D;
        for (int i = 0; i < count; i++) {
            String id = pick(variants, random);
            ids.add(id);
            width = Math.max(width, widthOf(id));
        }
        List<Spot> spots = arrange(pos, count, width, canStand, taken, random);
        if (spots.size() < count && owner) {
            Dungeons.LOGGER.info("[D2-POT] marker at {} rolled {} pot(s) but only {} fit on the free"
                    + " floor around it -- the rest are dropped", pos.toShortString(), count,
                    spots.size());
        }
        List<EntityPlacement> out = new ArrayList<>(spots.size());
        for (int i = 0; i < spots.size(); i++) {
            Spot spot = spots.get(i);
            taken.add(spot);
            BlockPos cell = BlockPos.containing(spot.x(), pos.getY(), spot.z());
            // Seeded per POT: seeding every pot by the marker gave a whole group identical loot.
            EntityPlacement placement = new EntityPlacement(cell.getX(), cell.getY(), cell.getZ(),
                    ids.get(i), random.nextFloat() * 360.0F, table, lootSeed(pos, i));
            placement.setXOffset(spot.x() - cell.getX());
            placement.setZOffset(spot.z() - cell.getZ());
            placement.setEffects(effects);
            out.add(placement);
        }
        return out;
    }

    /** An entity type's footprint width, or a whole block for an id that does not resolve. */
    private static double widthOf(String id) {
        // A bad id spawns nothing (EntitySpawner warns), so its width only has to be harmless.
        return EntityType.byString(id).map(type -> (double) type.getWidth()).orElse(1.0D);
    }

    /** Clear space between two neighbouring pots, in blocks: two pixels, so each reads as its own prop. */
    static final double POT_GAP = 0.125D;

    /** Angles tried for a group before settling for one pot fewer. */
    private static final int ROTATION_TRIES = 8;

    /**
     * How far a group's centre may slide off the centre of the marker's block, nearest first: up
     * to half a block each way, so the group still centres inside the block the author chose.
     */
    private static final List<double[]> SHIFTS = shifts();

    private static List<double[]> shifts() {
        double[] steps = {0.0D, 0.25D, -0.25D, 0.5D, -0.5D};
        List<double[]> out = new ArrayList<>(steps.length * steps.length);
        for (double dx : steps) {
            for (double dz : steps) {
                out.add(new double[] {dx, dz});
            }
        }
        out.sort(Comparator.comparingDouble(s -> s[0] * s[0] + s[1] * s[1]));
        return List.copyOf(out);
    }

    /**
     * Where one pot stands: the world X/Z of its centre, and its CLEARANCE radius -- half its
     * footprint plus half the gap, so two spots touch exactly when their pots are one gap apart.
     */
    record Spot(double x, double z, double radius) {
        boolean overlaps(Spot other) {
            double dx = x - other.x;
            double dz = z - other.z;
            double reach = radius + other.radius;
            return dx * dx + dz * dz < reach * reach - 1.0E-9;
        }
    }

    /**
     * Where a marker's {@code count} pots stand, as a group packed round the centre of the
     * marker's block.
     *
     * <p>Every pot used to be spawned at the marker's own position, so a marker asking for three
     * pots produced three entities in one spot. A pot is an ENTITY, not a block, so a group only
     * needs its pots' widths plus a gap: centres are {@code width + POT_GAP} apart, where
     * {@code width} is the widest pot in the group. Four flasks fit inside the marker's block; three
     * big pots overhang it.</p>
     *
     * <p><strong>Overhang must land on free floor.</strong> Every block a pot's footprint covers,
     * other than the marker's own, has to pass {@code canStand}, or the pot would stand half inside
     * a wall. The shape ({@link #layout}) is turned to a random angle, which both varies the
     * groups and lets one that meets a wall find the orientation that runs along it; and its centre
     * may slide up to half a block off the marker's ({@link #SHIFTS}, centred first). The slide is
     * what makes walls and corners work -- where pots are most often put. A centred triangle of big
     * pots pokes a vertex into a wall at every angle; slid half a block away from it, it fits. If
     * nothing fits the whole group it tries one pot fewer, down to a single pot at the centre -- so
     * a cramped marker gets a smaller group that still reads as a group, never an overlap.</p>
     *
     * <p>{@code taken} holds the pots of markers planned earlier in this piece, so two markers
     * placed side by side do not push their groups into each other.</p>
     *
     * <p>Package-private for {@code PotMarkerProcessorTest}.</p>
     */
    static List<Spot> arrange(BlockPos marker, int count, double width,
                              Predicate<BlockPos> canStand, List<Spot> taken, RandomSource random) {
        double spacing = width + POT_GAP;
        double centreX = marker.getX() + 0.5D;
        double centreZ = marker.getZ() + 0.5D;
        for (int size = count; size >= 1; size--) {
            List<double[]> offsets = layout(size, spacing);
            // A lone pot at the centre looks the same at every angle, so one try is all of them.
            int tries = size == 1 ? 1 : ROTATION_TRIES;
            for (int attempt = 0; attempt < tries; attempt++) {
                double angle = size == 1 ? 0.0D : random.nextDouble() * Math.PI * 2.0D;
                double sin = Math.sin(angle);
                double cos = Math.cos(angle);
                for (double[] shift : SHIFTS) {
                    List<Spot> spots = place(offsets, centreX + shift[0], centreZ + shift[1],
                            sin, cos, spacing, width, marker, canStand, taken);
                    if (spots != null) {
                        return spots;
                    }
                }
            }
        }
        return List.of();
    }

    /** One orientation and position of a group: every spot, or null if any pot does not fit. */
    private static List<Spot> place(List<double[]> offsets, double centreX, double centreZ,
                                    double sin, double cos, double spacing, double width,
                                    BlockPos marker, Predicate<BlockPos> canStand,
                                    List<Spot> taken) {
        List<Spot> spots = new ArrayList<>(offsets.size());
        for (double[] offset : offsets) {
            Spot spot = new Spot(centreX + offset[0] * cos - offset[1] * sin,
                    centreZ + offset[0] * sin + offset[1] * cos, spacing / 2.0D);
            if (!fits(spot, width, marker, canStand, taken)) {
                return null;
            }
            spots.add(spot);
        }
        return spots;
    }

    /**
     * A group's shape as X/Z offsets from its centre, neighbours {@code spacing} apart: one pot
     * at the centre, two to six evenly round a ring, seven or more packed hexagonally
     * nearest-first (a ring of six round a centre pot, then the next ring).
     */
    static List<double[]> layout(int count, double spacing) {
        List<double[]> out = new ArrayList<>(Math.max(count, 1));
        if (count <= 1) {
            out.add(new double[] {0.0D, 0.0D});
            return out;
        }
        if (count <= 6) {
            // The radius at which neighbours on the ring sit exactly one spacing apart.
            double radius = spacing / (2.0D * Math.sin(Math.PI / count));
            for (int i = 0; i < count; i++) {
                double angle = 2.0D * Math.PI * i / count;
                out.add(new double[] {radius * Math.cos(angle), radius * Math.sin(angle)});
            }
            return out;
        }
        int rings = 1;
        while (3 * rings * (rings + 1) + 1 < count) {
            rings++;
        }
        double rowStep = spacing * Math.sqrt(3.0D) / 2.0D;
        for (int q = -rings; q <= rings; q++) {
            for (int r = -rings; r <= rings; r++) {
                if (Math.abs(q + r) <= rings) {
                    out.add(new double[] {spacing * (q + r / 2.0D), rowStep * r});
                }
            }
        }
        out.sort(Comparator.<double[]>comparingDouble(p -> p[0] * p[0] + p[1] * p[1])
                .thenComparingDouble(p -> Math.atan2(p[1], p[0])));
        return new ArrayList<>(out.subList(0, count));
    }

    /**
     * Every block, at the marker's level, that a pot of {@code width} centred on {@code spot}
     * covers. Package-private for {@code PotMarkerProcessorTest}.
     */
    static List<BlockPos> footprint(Spot spot, double width, int y) {
        double half = width / 2.0D;
        // The far edge is exclusive: a pot ending exactly on a block boundary does not reach into
        // the next block.
        int minX = (int) Math.floor(spot.x() - half);
        int maxX = (int) Math.floor(spot.x() + half - 1.0E-9);
        int minZ = (int) Math.floor(spot.z() - half);
        int maxZ = (int) Math.floor(spot.z() + half - 1.0E-9);
        List<BlockPos> out = new ArrayList<>(4);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                out.add(new BlockPos(x, y, z));
            }
        }
        return out;
    }

    /**
     * The marker's own block passes unconditionally: the author put the marker there, and the
     * marker block itself is not air, so asking {@code canStand} about it would always say no.
     */
    private static boolean fits(Spot spot, double width, BlockPos marker,
                                Predicate<BlockPos> canStand, List<Spot> taken) {
        for (BlockPos cell : footprint(spot, width, marker.getY())) {
            if (!cell.equals(marker) && !canStand.test(cell)) {
                return false;
            }
        }
        for (Spot other : taken) {
            if (spot.overlaps(other)) {
                return false;
            }
        }
        return true;
    }

    /** Never 0, like {@link #seedFor}, and different for each pot of one marker's group. */
    static long lootSeed(BlockPos marker, int index) {
        long seed = marker.asLong() * 31L + index;
        return seed == 0L ? 1L : seed;
    }

    /**
     * Whether a pot can stand in a cell, judged from the processed piece: empty itself, with a
     * floor under it that is solid on top.
     *
     * <p>An absent cell is NOT free. The list holds every block the template authored, air
     * included (only structure blocks are stripped), so absence means {@code structure_void} or
     * outside the piece -- places a pot would stand in whatever the world already holds there.
     * The processed state is read rather than the level because nothing has been written yet (see
     * the class note), and it already carries decoration's cobwebs and anything else placed in the
     * cell before this processor.</p>
     */
    private static boolean standsFree(BlockState cell, BlockState below) {
        return cell != null && cell.isAir()
                && below != null
                && below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
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
