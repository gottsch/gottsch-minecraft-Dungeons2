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
import mod.gottsch.forge.gottschcore.json.StrictCodecs;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingStage;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.LevelIndependentProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Multi-stage block aging, <strong>scoped to a surface</strong>: the same decay chains
 * {@code dungeons2:aging} runs, but each rule may name the {@link PieceSurface} it applies to.
 *
 * <h2>Why this exists at all</h2>
 * <p>The mud stratum wanted its floors to wear on their own schedule &mdash; cobble paving fretting
 * to rubble while the mud-brick walls above it crumble differently. GottschCore's
 * {@code AgingProcessor} cannot express that: it matches on block <em>state</em> and has no idea
 * which surface a block belongs to. Keying on block identity alone was tried on paper and rejected,
 * because a rule on {@code minecraft:cobblestone} works only while cobblestone happens to be
 * floor-only &mdash; an all-cobble fortified room on the same band would have its walls eaten by
 * the floor rule, silently, long after the rule was written.</p>
 *
 * <h2>Why it is Dungeons2's and not GottschCore's</h2>
 * <p>Adding a {@code surface} field to the shared {@code AgingProcessor} would put the concept in
 * front of every motif and every stratum, almost all of which have no use for it (Gottsch,
 * 2026-08-26). Only the mud band wants surface-scoped decay, so only the mud band's processor list
 * names this. Nothing else loads it, so nothing else can be broken by it.</p>
 *
 * <h2>IT MUST OWN A LIST'S AGING ENTIRELY</h2>
 * <p><strong>Do not put this alongside a {@code dungeons2:aging} entry in the same file.</strong> A
 * processor list is chained &mdash; each processor sees the previous one's output &mdash; so a
 * surface-gated processor added <em>next to</em> an ungated one is additive, not exclusive: the
 * ungated rules still run over the floor, and a cell can decay twice on two different schedules.
 * The gates only partition the piece if every rule in the file carries one, which is why
 * {@code classic_mud_weathering.json} has this and no {@code dungeons2:aging}.</p>
 *
 * <p>{@code StratumWeatheringListTest.aListNeverMixesGatedAndUngatedAging} pins that across every
 * shipped file, because the failure is invisible: a doubly-aged floor looks like a floor with a
 * slightly wrong decay rate.</p>
 *
 * <h2>Determinism across the chunk seam</h2>
 * <p>Like the processor it mirrors, the random is seeded from the block's absolute world position
 * ({@code Mth.getSeed}), so a piece spanning two chunks resolves every cell identically in both
 * passes. This is a {@link LevelIndependentProcessor}: it reads nothing but the block it was
 * handed, so {@code PieceProcessors} gives it the whole piece unclipped, in authored order,
 * alongside {@code dungeons2:decoration}.</p>
 */
public class SurfaceAgingProcessor extends StructureProcessor implements LevelIndependentProcessor {

    private final Supplier<StructureProcessorType<?>> type;
    private final int agings;
    private final List<SurfaceAgingRule> rules;
    /** Rules indexed by source block, preserving authored order, for an O(1) miss. */
    private final Map<Block, List<SurfaceAgingRule>> rulesByBlock;

    public SurfaceAgingProcessor(Supplier<StructureProcessorType<?>> type, int agings,
                                 List<SurfaceAgingRule> rules) {
        this.type = type;
        this.agings = agings;
        this.rules = List.copyOf(rules);
        this.rulesByBlock = new HashMap<>();
        for (SurfaceAgingRule rule : this.rules) {
            rulesByBlock.computeIfAbsent(rule.block(), block -> new ArrayList<>()).add(rule);
        }
    }

    /** See {@code Registration} for the codec/type registration idiom. */
    public static Codec<SurfaceAgingProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptionalFieldOf(Codec.INT, "agings", 1)
                        .forGetter(processor -> processor.agings),
                SurfaceAgingRule.CODEC.listOf().fieldOf("rules")
                        .forGetter(processor -> processor.rules)
        ).apply(instance, (agings, rules) -> new SurfaceAgingProcessor(type, agings, rules)));
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level, BlockPos piecePos, BlockPos structurePos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings) {

        List<SurfaceAgingRule> candidates = rulesByBlock.get(current.state().getBlock());
        if (candidates == null) {
            return current;
        }

        // `current.pos()` is already in world space; `piecePos` is the origin vanilla offset it by.
        // Rotation and mirroring are horizontal, so neither disturbs this.
        int relativeY = PieceSurface.relativeY(piecePos, current.pos());

        RandomSource random = RandomSource.create(Mth.getSeed(current.pos()));
        Block aged = null;
        for (SurfaceAgingRule rule : candidates) {
            if (!rule.surface().matches(relativeY)) {
                continue;
            }
            aged = decay(rule, random);
            if (aged != null) {
                // This chain took, so the alternatives for this block don't get a turn.
                break;
            }
        }
        if (aged == null) {
            return current;
        }

        BlockState agedState = carryProperties(current.state(), aged.defaultBlockState());
        return new StructureTemplate.StructureBlockInfo(current.pos(), agedState, current.nbt());
    }

    /**
     * Walks {@code rule}'s chain as far as the rolls allow, returning the deepest stage reached, or
     * {@code null} if even the first stage missed.
     */
    private Block decay(SurfaceAgingRule rule, RandomSource random) {
        Block deepest = null;
        int stages = Math.min(this.agings, rule.outputBlocks().size());
        for (int i = 0; i < stages; i++) {
            AgingStage stage = rule.outputBlocks().get(i);
            if (random.nextDouble() >= stage.probability()) {
                // Missed: the chain stops here and the last stage reached stands.
                break;
            }
            deepest = stage.block();
        }
        return deepest;
    }

    /**
     * Copies every property {@code from} and {@code to} have in common &mdash; what lets one rule
     * age a whole family of shaped blocks: a stair keeps its facing/half/shape/waterlogged, a wall
     * its five connection states, a pillar its axis, with no per-block special cases.
     */
    private static BlockState carryProperties(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property<?> property : from.getProperties()) {
            if (result.hasProperty(property)) {
                result = copyProperty(result, from, property);
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
            BlockState target, BlockState source, Property<T> property) {
        return target.setValue(property, source.getValue(property));
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return type.get();
    }
}
