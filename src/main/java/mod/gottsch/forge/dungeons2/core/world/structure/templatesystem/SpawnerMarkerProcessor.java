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
import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import mod.gottsch.forge.dungeons2.core.util.VanillaSpawnerNbt;
import mod.gottsch.forge.gottschcore.mobset.MobSetDataRegistry;
import mod.gottsch.forge.gottschcore.mobset.WeightedMob;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Swaps the authored {@code dungeons2:spawner_marker} block for Dungeons2's invisible mob-set
 * spawner, with the mob set attached. Backlog #10, Dungeons2 side.
 *
 * <h2>Why a marker BLOCK, and not the {@code d2:spawner} DATA marker the README used to document</h2>
 * <p>The first version of this matched a DATA structure block, on the reasoning that a processor
 * sees every block in a template. <strong>That is true of a raw
 * {@code StructureTemplate.placeInWorld} and false of a jigsaw pool element</strong>, which is the
 * only way Dungeons2 places authored content: {@code SinglePoolElement.getSettings} installs
 * {@code BlockIgnoreProcessor.STRUCTURE_BLOCK} <em>before</em> appending the pool's own processors,
 * and that returns {@code null} for a structure block &mdash; <em>removing</em> it from the
 * placement list rather than replacing it. The pool's processors then receive a list it is already
 * absent from.</p>
 *
 * <p>The symptom was a good one to remember: the marked cell showed neither a spawner nor a visible
 * structure block, but <em>the terrain the dungeon was carved out of</em> (a coal ore, as reported),
 * because nothing ever wrote that cell. {@code JigsawStripsStructureBlocksTest} pins the mechanism.
 * Village Dungeons keys its own spawner processor on marker blocks; that looked like a stylistic
 * choice and is in fact forced.</p>
 *
 * <h2>The trade the block form makes</h2>
 * <p>A DATA marker carried a free-text string, so it could name its own mob set per cell. A block
 * cannot. {@code marker_block} is therefore a codec field: a motif wanting a second set registers a
 * second marker block and adds a second processor entry naming it, which is pure data on this side.
 * Until someone needs that, one marker means one set per motif.</p>
 *
 * <h2>What this does NOT do</h2>
 * <p>It does not validate that the named mob set exists. {@code MobSetDataRegistry} is populated
 * from datapacks at reload time and a processor runs during worldgen, so "not yet loaded" and "does
 * not exist" are indistinguishable here; the block entity resolves the name when it fires. The
 * shipped sets are swept by {@code ShippedMobSetsTest} instead, which is where a typo becomes a
 * build failure.</p>
 *
 * <p>Implements {@link LevelIndependentProcessor} because it reads nothing but the block it was
 * handed &mdash; see {@code PieceProcessors} for why that split matters at a chunk seam. In
 * practice it never fires on a procedurally-built piece, since only an authored template contains
 * the marker.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
public class SpawnerMarkerProcessor extends StructureProcessor implements LevelIndependentProcessor {

    /** The authoring marker block, as documented in {@code structures/README.md}. */
    public static final ResourceLocation DEFAULT_MARKER_BLOCK =
            new ResourceLocation(Dungeons.MOD_ID, "spawner_marker");

    private static final String MOB_SET_NAME = "mobSetName";
    private static final String MIN_MOBS = "minMobs";
    private static final String MAX_MOBS = "maxMobs";
    private static final String PROXIMITY = "proximity";

    private final ResourceLocation mobSet;
    private final ResourceLocation markerBlock;
    private final double proximity;
    private final int minMobs;
    private final int maxMobs;
    private final SpawnerConfig.Kind kind;

    /** The proximity form, which is what every marker authored before vanilla spawners meant. */
    public SpawnerMarkerProcessor(ResourceLocation mobSet, ResourceLocation markerBlock, double proximity,
                                  int minMobs, int maxMobs) {
        this(mobSet, markerBlock, proximity, minMobs, maxMobs, SpawnerConfig.Kind.PROXIMITY);
    }

    public SpawnerMarkerProcessor(ResourceLocation mobSet, ResourceLocation markerBlock, double proximity,
                                  int minMobs, int maxMobs, SpawnerConfig.Kind kind) {
        this.mobSet = mobSet;
        this.markerBlock = markerBlock;
        this.proximity = proximity;
        this.minMobs = minMobs;
        this.maxMobs = maxMobs;
        this.kind = kind;
    }

    /**
     * {@code mob_set} is required on purpose: a spawner with no set is a block that does nothing,
     * and defaulting it would make that failure silent. The tuning knobs default, because they have
     * defensible values and most authors will never set them.
     */
    public static Codec<SpawnerMarkerProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        Codec<SpawnerMarkerProcessor> codec = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("mob_set").forGetter(p -> p.mobSet),
                ResourceLocation.CODEC.optionalFieldOf("marker_block", DEFAULT_MARKER_BLOCK)
                        .forGetter(p -> p.markerBlock),
                Codec.DOUBLE.optionalFieldOf("proximity", 8.0D).forGetter(p -> p.proximity),
                Codec.INT.optionalFieldOf("min_mobs", 1).forGetter(p -> p.minMobs),
                Codec.INT.optionalFieldOf("max_mobs", 3).forGetter(p -> p.maxMobs),
                // Same default and the same reason as the scheme slot's: every marker authored
                // before vanilla spawners existed means the ambush block.
                SpawnerConfig.Kind.CODEC.optionalFieldOf("type", SpawnerConfig.Kind.PROXIMITY)
                        .forGetter(p -> p.kind)
        ).apply(instance, SpawnerMarkerProcessor::new));
        return codec;
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos piecePos,
                                                             BlockPos relativePos,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current,
                                                             StructurePlaceSettings settings) {
        if (!isSpawnerMarker(current)) {
            return current;
        }
        // Diagnostic, because every failure downstream of here is invisible: the block this
        // produces cannot be seen, and a spawner that never fires looks exactly like a spawner
        // that was never placed. One line per conversion, at the position it happened.
        //
        //   grep "D2-SPAWNER" run/logs/dungeons2.log
        //
        // Absent => the marker was never matched (wrong block id, or the template did not place).
        // Present but no mobs => look in run/logs/gottschcore.log instead: the block entity's own
        // "proximity met" / "self-destructing" lines are GottschCore's, and that file has its own
        // [logging] level in config/gottschcore-common.toml.
        Dungeons.LOGGER.debug("[D2-SPAWNER] {} -> {} at {} (set {})",
                markerBlock, kind.getSerializedName(), current.pos(), mobSet);

        if (kind == SpawnerConfig.Kind.VANILLA) {
            CompoundTag vanilla = vanillaSpawnerTag(settings.getRandom(current.pos()));
            if (vanilla == null) {
                // No resolvable mobs, so there is nothing to put in the cage. Leave the marker
                // in place rather than emit an empty spawner: vanilla's own default is a pig, and
                // an unconverted marker is at least visibly wrong to whoever authored it.
                Dungeons.LOGGER.warn("[D2-SPAWNER] mob set {} resolved to no usable mobs at {};"
                        + " leaving the marker unconverted", mobSet, current.pos());
                return current;
            }
            return new StructureTemplate.StructureBlockInfo(current.pos(),
                    Blocks.SPAWNER.defaultBlockState(), vanilla);
        }

        // The block lookup is the ONLY part of this that needs a populated Forge registry, which is
        // why everything either side of it is separately callable -- see SpawnerMarkerProcessorTest.
        return new StructureTemplate.StructureBlockInfo(current.pos(),
                DungeonsBlocks.MOB_SET_SPAWNER.get().defaultBlockState(), spawnerTag());
    }

    /** Matches {@code RoomSpawnerGenerator}, so both routes name the same block entity. */
    static final String VANILLA_SPAWNER_ENTITY = "minecraft:mob_spawner";

    /**
     * The tag for a vanilla cage drawing from this processor mob set, or {@code null} when the set
     * resolves to nothing vanilla could spawn.
     *
     * <h2>Here the two routes DO share code, unlike the proximity pair</h2>
     * <p>The proximity spawner builds the same tag by two different encodings on the two routes and
     * cannot share an implementation, which is the whole reason {@code SpawnerTagParityTest} exists.
     * The vanilla tag has no such split: {@code VanillaSpawnerNbt} emits SNBT, this side parses it
     * and the procedural side posts it through {@code BlockEntityData}. One builder, so there is
     * nothing here that can drift out of step.</p>
     *
     * <p>Unlike the floor index, the mob draw here CAN be seeded properly &mdash;
     * {@code settings.getRandom(pos)} is position-derived, so the same template in the same place
     * shows the same mob.</p>
     */
    CompoundTag vanillaSpawnerTag(RandomSource random) {
        List<WeightedMob> mobs = MobSetDataRegistry.get(mobSet)
                .map(VanillaSpawnerNbt::usableMobs)
                .orElseGet(List::of);
        if (mobs.isEmpty()) {
            return null;
        }
        int total = mobs.stream().mapToInt(WeightedMob::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        String shown = mobs.get(mobs.size() - 1).id().toString();
        for (WeightedMob mob : mobs) {
            roll -= mob.weight();
            if (roll < 0) {
                shown = mob.id().toString();
                break;
            }
        }
        int spawnCount = minMobs + (maxMobs > minMobs ? random.nextInt(maxMobs - minMobs + 1) : 0);

        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", VANILLA_SPAWNER_ENTITY);
            tag.put(VanillaSpawnerNbt.SPAWN_DATA,
                    TagParser.parseTag(VanillaSpawnerNbt.spawnData(shown)));
            // SpawnPotentials is a LIST, and TagParser only parses a compound -- so it is wrapped
            // and unwrapped, the same trick DungeonPiece#parseNbtValue uses for the other route.
            tag.put(VanillaSpawnerNbt.SPAWN_POTENTIALS,
                    TagParser.parseTag("{v:" + VanillaSpawnerNbt.spawnPotentials(mobs) + "}").get("v"));
            VanillaSpawnerNbt.tuning(spawnCount).forEach((k, v) -> tag.putInt(k, Integer.parseInt(v)));
            return tag;
        } catch (Exception malformed) {
            Dungeons.LOGGER.error("[D2-SPAWNER] could not build vanilla spawner tag for set {}: {}",
                    mobSet, malformed.toString());
            return null;
        }
    }

    /**
     * Whether this block is the authored marker.
     *
     * <p>Compared by <strong>registry id</strong> rather than by {@code state().is(block)}: this is
     * reachable without a populated Forge registry, which is what lets the test cover it, and it
     * makes {@code marker_block} a genuine datapack knob rather than a constant with a codec in
     * front of it.</p>
     */
    boolean isSpawnerMarker(StructureTemplate.StructureBlockInfo info) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(info.state().getBlock());
        return markerBlock.equals(id);
    }

    /** The block-entity tag a marker becomes. Pure: no registry, no level. */
    CompoundTag spawnerTag() {
        CompoundTag tag = new CompoundTag();
        // The block-entity type's registry id, which is what vanilla's placeInWorld loads against.
        tag.putString("id", new ResourceLocation(Dungeons.MOD_ID, "mob_set_spawner").toString());
        tag.putString(MOB_SET_NAME, mobSet.toString());
        tag.putInt(MIN_MOBS, minMobs);
        tag.putInt(MAX_MOBS, maxMobs);
        tag.putDouble(PROXIMITY, proximity);
        return tag;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return mod.gottsch.forge.dungeons2.core.setup.Registration.SPAWNER_PROCESSOR.get();
    }
}
