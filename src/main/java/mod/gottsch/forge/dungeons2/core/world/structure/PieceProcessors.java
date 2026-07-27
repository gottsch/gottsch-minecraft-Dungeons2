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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

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
 * <h2>Chunk-safety: which processors are safe here</h2>
 * <p>A procedural piece re-runs and re-renders <em>once per chunk it overlaps</em>,
 * so a block in a chunk seam is processed twice in two separate passes and MUST
 * resolve identically both times. Two consequences:</p>
 * <ul>
 *   <li><strong>Per-block, position-keyed processors are safe.</strong> Vanilla's
 *       {@code RuleProcessor} seeds {@code RandomSource.create(Mth.getSeed(pos))}
 *       from the block's absolute world position, so it is already deterministic
 *       per position and identical across the seam. This is the same property the
 *       procedural-side {@code BlockSubstitutor} hand-rolled a positional hash for.</li>
 *   <li><strong>Whole-list processors are NOT safe.</strong> Anything that counts,
 *       caps, or otherwise decides from the block list as a whole (a processor
 *       overriding {@code finalizeProcessing}, e.g. {@code CappedProcessor}) sees
 *       only the current chunk's slice of the piece, and would make a different
 *       decision per chunk. Don't put those in a dungeon processor list.</li>
 * </ul>
 *
 * <p>The caller must clip to the chunk box <em>before</em> calling
 * {@link #process} &mdash; not after. {@code RuleProcessor} reads the existing
 * world block at each position ({@code location_predicate}), and reading outside
 * the current {@code WorldGenRegion} during worldgen is illegal.</p>
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
     * Runs every processor in {@code list} over {@code relativeInfos}, returning the
     * surviving blocks at absolute world positions.
     *
     * @param origin        world position {@code relativeInfos} are relative to;
     *                      vanilla offsets each info by this and hands it to the
     *                      processors as the block's world position.
     * @param relativeInfos blocks in {@code origin}-relative space, already clipped
     *                      to the chunk box (see the class doc &mdash; clipping after
     *                      processing is not an option).
     */
    public static List<StructureTemplate.StructureBlockInfo> process(
            WorldGenLevel level, BlockPos origin,
            List<StructureTemplate.StructureBlockInfo> relativeInfos,
            StructureProcessorList list) {

        // Rotation/mirror stay at their NONE defaults: procedural pieces are always
        // built NORTH-oriented, so calculateRelativePosition is the identity and each
        // info's world position is exactly origin + its relative position.
        StructurePlaceSettings settings = new StructurePlaceSettings();
        list.list().forEach(settings::addProcessor);
        return StructureTemplate.processBlockInfos(level, origin, origin, settings, relativeInfos, null);
    }
}
