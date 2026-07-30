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

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Runs a vanilla {@code minecraft:worldgen/processor_list} over a
 * <em>procedural</em> piece's blocks, so procedurally-built rooms / corridors /
 * doors are decorated by exactly the same datapack file as the jigsaw prefabs
 * placed alongside them (the pool JSONs point their {@code "processors"} field at
 * the same list).
 *
 * <p>No template is involved: {@link StructureTemplate#processBlockInfos} is
 * {@code public static} and takes a {@code ServerLevelAccessor} (which
 * {@link WorldGenLevel} is), so a plain list of
 * {@link StructureTemplate.StructureBlockInfo}s built from our own
 * {@code BlockPlacement}s can be fed straight through vanilla's processing loop.
 * The {@code template} argument is nullable &mdash; vanilla's own deprecated
 * overload passes {@code null} for it.</p>
 *
 * <h2>Chunk-safety: the two passes</h2>
 * <p>A procedural piece re-runs and re-renders <em>once per chunk it overlaps</em>,
 * so a block in a chunk seam is processed twice in two separate passes and MUST
 * resolve identically both times. The motif's processor list is therefore split on
 * one question &mdash; <em>does this processor read the level?</em> &mdash; answered by
 * {@link LevelIndependentProcessor}:</p>
 * <ol>
 *   <li><strong>Level-independent pass &mdash; unclipped.</strong> Processors marked
 *       {@link LevelIndependentProcessor} get the whole piece, in authored order. Being
 *       unclipped is <em>required</em> for a neighbour-aware processor (a neighbour map
 *       built from one chunk's slice is missing everything across the seam) and
 *       <em>beneficial</em> for a per-block one, since it keeps it in the same pass and
 *       so in the same relative order as the rest.</li>
 *   <li><strong>Level-reading pass &mdash; clipped.</strong> Everything else. Vanilla's
 *       {@code RuleProcessor} calls {@code level.getBlockState} for its
 *       {@code location_predicate}, and reading outside the current
 *       {@code WorldGenRegion} during worldgen is illegal, so these only ever see
 *       blocks inside the chunk box. They survive the double pass because each seeds
 *       its random from the block's absolute world position ({@code Mth.getSeed(pos)})
 *       &mdash; the whole reason vanilla's own processors can be reused here at all.</li>
 * </ol>
 *
 * <p>Splitting on "reads the level" rather than "is neighbour-aware" is deliberate: it
 * keeps {@code dungeons2:aging} and {@code dungeons2:decoration} together in pass 1, in
 * the order the datapack authored them, so decoration sees what aging did &mdash;
 * cobwebs in a gap a crumbled stair left, growth on dirt aging produced. That is what a
 * jigsaw prefab gets from vanilla's single unsplit list.</p>
 *
 * <p>What still differs from a prefab is where the level-reading processors land: vanilla
 * runs them <em>before</em> any {@code finalizeProcessing}, this runs them after. For the
 * shipped list that is invisible, because its {@code minecraft:rule} entries only swap one
 * full cube for another and so never change what decoration keys off (air, solidity,
 * block identity).</p>
 *
 * <p>An unmarked processor that decides from the whole block list (e.g.
 * {@code minecraft:capped}, which counts across it) belongs in neither pass and must not
 * be put in a dungeon processor list &mdash; it would land in the clipped pass and cap per
 * chunk. {@code WeatheringProcessorListTest} enforces that on the shipped file.</p>
 *
 * @author Mark Gottschling on Jul 26, 2026
 */
public final class PieceProcessors {

    /**
     * Processor lists are named per motif: {@code dungeons2:<motif>_weathering}.
     * A motif with no such list generates undecorated &mdash; the same graceful
     * degradation an absent template pool already has.
     */
    private static final String WEATHERING_SUFFIX = "_weathering";

    private PieceProcessors() {}

    /** The processor list for {@code motifValue}, or empty when none is registered. */
    public static Optional<StructureProcessorList> weatheringList(WorldGenLevel level, String motifValue) {
        if (motifValue == null || motifValue.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation id = new ResourceLocation(Dungeons.MOD_ID,
                motifValue.trim().toLowerCase(Locale.ROOT) + WEATHERING_SUFFIX);
        Registry<StructureProcessorList> registry =
                level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
        return registry.getOptional(ResourceKey.create(Registries.PROCESSOR_LIST, id));
    }

    /**
     * Runs the motif's processor list over a procedural piece's blocks, in the two
     * passes described in the class doc, and returns the surviving blocks at absolute
     * world positions, clipped to {@code chunkBox}.
     *
     * <p>A motif with no processor list still round-trips through here: the result is
     * {@code relativeInfos} offset to world space and clipped, i.e. an undecorated
     * piece.</p>
     *
     * @param origin        world position {@code relativeInfos} are relative to;
     *                      vanilla offsets each info by this and hands it to the
     *                      processors as the block's world position.
     * @param chunkBox      the chunk box {@code postProcess} was handed. Clipping
     *                      happens <em>between</em> the two passes &mdash; see the class
     *                      doc; neither "clip everything first" nor "clip at the end"
     *                      is an option.
     * @param relativeInfos the piece's blocks in {@code origin}-relative space,
     *                      <strong>unclipped</strong>.
     */
    public static List<StructureTemplate.StructureBlockInfo> decorate(
            WorldGenLevel level, BlockPos origin, BoundingBox chunkBox,
            List<StructureTemplate.StructureBlockInfo> relativeInfos, String motifValue) {

        List<StructureProcessor> levelIndependent = new ArrayList<>();
        List<StructureProcessor> levelReading = new ArrayList<>();
        for (StructureProcessor processor : weatheringList(level, motifValue)
                .map(StructureProcessorList::list).orElse(List.of())) {
            (processor instanceof LevelIndependentProcessor ? levelIndependent : levelReading)
                    .add(processor);
        }

        // Pass 1, over the WHOLE piece.
        List<StructureTemplate.StructureBlockInfo> decorated = levelIndependent.isEmpty()
                ? toWorld(relativeInfos, origin)
                : process(level, origin, relativeInfos, levelIndependent);

        // Clip, and go back to origin-relative so pass 2 can offset them again.
        List<StructureTemplate.StructureBlockInfo> clipped = new ArrayList<>(decorated.size());
        for (StructureTemplate.StructureBlockInfo info : decorated) {
            if (chunkBox.isInside(info.pos())) {
                clipped.add(new StructureTemplate.StructureBlockInfo(
                        info.pos().subtract(origin), info.state(), info.nbt()));
            }
        }

        // Pass 2, over this chunk's slice only.
        return levelReading.isEmpty()
                ? toWorld(clipped, origin)
                : process(level, origin, clipped, levelReading);
    }

    /** One pass of vanilla's processing loop, with no template involved. */
    private static List<StructureTemplate.StructureBlockInfo> process(
            WorldGenLevel level, BlockPos origin,
            List<StructureTemplate.StructureBlockInfo> relativeInfos,
            List<StructureProcessor> processors) {

        // Rotation/mirror stay at their NONE defaults: procedural pieces are always
        // built NORTH-oriented, so calculateRelativePosition is the identity and each
        // info's world position is exactly origin + its relative position.
        StructurePlaceSettings settings = new StructurePlaceSettings();
        processors.forEach(settings::addProcessor);
        return StructureTemplate.processBlockInfos(level, origin, origin, settings, relativeInfos, null);
    }

    /**
     * Offsets {@code infos} from origin-relative to world space &mdash; the same
     * translation {@link StructureTemplate#processBlockInfos} applies, for the passes
     * with no processor to run and so no call to make.
     */
    private static List<StructureTemplate.StructureBlockInfo> toWorld(
            List<StructureTemplate.StructureBlockInfo> infos, BlockPos origin) {
        List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>(infos.size());
        for (StructureTemplate.StructureBlockInfo info : infos) {
            out.add(new StructureTemplate.StructureBlockInfo(
                    info.pos().offset(origin), info.state(), info.nbt()));
        }
        return out;
    }
}
