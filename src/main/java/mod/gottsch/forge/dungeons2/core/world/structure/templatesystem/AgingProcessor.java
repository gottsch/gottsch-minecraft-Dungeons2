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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data.AgingRule;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data.AgingStage;
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

/**
 * Ages blocks along multi-stage decay chains, <strong>carrying the source block's
 * state properties onto the replacement</strong>.
 *
 * <p>Both of those are things vanilla's {@code minecraft:rule} processor cannot do:</p>
 * <ul>
 *   <li>A {@code ProcessorRule} emits one fixed {@code output_state} and <em>drops</em>
 *       the input's properties. That restricts vanilla weathering to plain full cubes:
 *       aging stairs, walls or slabs would need every facing / half / shape combination
 *       enumerated as its own rule. This processor copies every property the source and
 *       replacement share, so {@code stone_brick_stairs -> mossy_stone_brick_stairs}
 *       keeps its facing, half, shape and waterlogging with no per-block rules.</li>
 *   <li>A rule is a single step. Here a rule is a <em>chain</em>
 *       ({@code stone_bricks -> cracked -> cobblestone -> gravel}) where each stage is
 *       only reachable if the stage before it was, giving graduated decay from one entry.</li>
 * </ul>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>{@code agings} caps how many stages of a chain may be applied (default 1, i.e.
 *       only each chain's first stage). Think of it as how many rounds of decay the
 *       structure has been through.</li>
 *   <li>Several rules may share a source block, acting as alternative chains (a mossy
 *       chain and a cracked chain, say). They are tried in order and the first chain
 *       that decays at all wins, so &mdash; exactly like consecutive vanilla rules
 *       &mdash; a later chain's authored probability is <strong>conditional</strong> on
 *       the earlier ones having missed.</li>
 *   <li>Blocks with no rule, and blocks whose every chain misses, pass through untouched.</li>
 * </ul>
 *
 * <h2>Chunk-safety</h2>
 * <p>The random is derived from the block's absolute world position
 * ({@code RandomSource.create(Mth.getSeed(pos))}), the same way vanilla's
 * {@code RuleProcessor} does it, rather than from {@code settings.getRandom(...)}.
 * Deriving it here rather than trusting the settings means the result is identical no
 * matter who calls us &mdash; which is what makes this safe for
 * {@link PieceProcessors}, where a procedural piece is re-processed once per chunk it
 * overlaps and a block on a chunk seam must resolve the same way in both passes.</p>
 *
 * <p>{@code processBlock} also never touches the {@code LevelReader}, hence
 * {@link LevelIndependentProcessor}. That puts it in {@code PieceProcessors}' unclipped
 * pass alongside {@link DecorationProcessor}, so decoration sees what aging did &mdash;
 * cobwebs in a gap a crumbled stair left, growth on dirt aging produced &mdash; exactly as
 * it would for a jigsaw prefab, where vanilla runs both from one list.</p>
 *
 * @author Mark Gottschling on Jul 27, 2026
 */
public class AgingProcessor extends StructureProcessor implements LevelIndependentProcessor {

    /**
     * Registry name, so the {@code processor_type} authored in a processor_list JSON
     * and the name registered in {@link Registration} can be asserted equal by a test
     * instead of being two independent string literals.
     */
    public static final String NAME = "aging";

    public static final Codec<AgingProcessor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("agings", 1).forGetter(processor -> processor.agings),
            AgingRule.CODEC.listOf().fieldOf("rules").forGetter(processor -> processor.rules)
    ).apply(instance, AgingProcessor::new));

    private final int agings;
    private final List<AgingRule> rules;
    /** Rules indexed by source block, preserving authored order, for an O(1) miss. */
    private final Map<Block, List<AgingRule>> rulesByBlock;

    public AgingProcessor(int agings, List<AgingRule> rules) {
        this.agings = agings;
        this.rules = List.copyOf(rules);
        this.rulesByBlock = new HashMap<>();
        for (AgingRule rule : this.rules) {
            rulesByBlock.computeIfAbsent(rule.block(), block -> new ArrayList<>()).add(rule);
        }
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level, BlockPos origin, BlockPos pos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings) {

        List<AgingRule> candidates = rulesByBlock.get(current.state().getBlock());
        if (candidates == null) {
            return current;
        }

        RandomSource random = RandomSource.create(Mth.getSeed(current.pos()));
        Block aged = null;
        for (AgingRule rule : candidates) {
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
     * Walks {@code rule}'s chain as far as the rolls allow, returning the deepest stage
     * reached, or {@code null} if even the first stage missed.
     */
    private Block decay(AgingRule rule, RandomSource random) {
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
     * Copies every property {@code from} and {@code to} have in common. This is what
     * lets one rule age a whole family of shaped blocks: a stair keeps its facing /
     * half / shape / waterlogged, a wall its five connection states, a pillar its axis
     * &mdash; with no per-block-class special cases.
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

    /** Generic helper: {@link Property} is only usable at a concrete value type. */
    private static <T extends Comparable<T>> BlockState copyProperty(
            BlockState to, BlockState from, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return Registration.AGING_PROCESSOR.get();
    }
}
