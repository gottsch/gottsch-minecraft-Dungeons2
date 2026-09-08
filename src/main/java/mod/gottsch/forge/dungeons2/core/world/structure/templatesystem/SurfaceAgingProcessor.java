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
import net.minecraft.world.level.ServerLevelAccessor;
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
import java.util.Set;
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
 * front of every mod depending on GottschCore, almost all of which have no use for it (Gottsch,
 * 2026-08-26). It is Dungeons2's own, and only Dungeons2's lists name it.</p>
 *
 * <p><strong>It started as the mud band's alone and is now the classic motif's too</strong>
 * ({@code classic_weathering.json}, migrated 2026-09-04). That migration was a type change and
 * nothing else: this is a strict superset of {@code dungeons2:aging} &mdash; the same chains, plus
 * a {@code surface} on each rule &mdash; so a file whose every rule says {@code any} behaves
 * identically, down to the roll. It was done ahead of any rule wanting a surface so the weathering
 * pass would not need a second format change halfway through. {@code dungeons2:aging} is still
 * registered and still used by the boss and entrance lists.</p>
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
 * passes. This is a {@link LevelIndependentProcessor}: it reads nothing but the blocks it was
 * handed, so {@code PieceProcessors} gives it the whole piece unclipped, in authored order,
 * alongside {@code dungeons2:decoration}.</p>
 *
 * <h2>2026-09-04, backlog #15: which phase it ages in depends on its rules</h2>
 * <p>{@link PieceSurface} grew {@code wall}, {@code ceiling} and {@code joist}, none of which a
 * per-block gate can answer &mdash; they are questions about a cell's neighbours, and
 * {@code processBlock} sees one block at a time. So a processor whose rules ask a geometric
 * question decides in {@link #finalizeProcessing}, which is handed the whole list, classifying it
 * once through {@link PieceSurfaceMap}.</p>
 *
 * <p><strong>A processor whose rules do not ask one stays in {@code processBlock}, exactly where it
 * always was.</strong> That is not an optimisation, it is the compatibility guarantee: deciding in
 * the later phase costs something real, and a list that cannot use what it buys should not pay it.
 * {@code any}, {@code floor} and {@code above_floor} are answerable from Y, so
 * {@link PieceSurfaceMap#byHeight} answers them with nothing allocated and nothing scanned. Every
 * rule shipped today is one of those three.</p>
 *
 * <p>The choice is per <em>processor</em>, never per rule, so authored order still decides which of
 * two rules on the same block gets first refusal. Splitting one processor's rules across two phases
 * would have silently reordered them.</p>
 *
 * <h2>What the later phase costs: it can be fed, but it can only feed forwards</h2>
 * <p>Vanilla runs every {@code processBlock} before any {@code finalizeProcessing}, so:</p>
 * <ul>
 *   <li>Two {@code surface_aging} entries still chain in list order &mdash; <strong>multi-step
 *       aging across processors is intact</strong>, whichever phase they are in.</li>
 *   <li>A {@code minecraft:rule} entry <em>before</em> one still feeds it. So does one after it,
 *       which is the misleading half.</li>
 *   <li>A {@code minecraft:rule} entry can <strong>never</strong> be fed by a geometric
 *       {@code surface_aging}, however the file is ordered. It would read as a second stage and not
 *       be one.</li>
 * </ul>
 *
 * <p>{@code ProcessorPhaseOrderTest} pins all three;
 * {@code StratumWeatheringListTest.aSurfaceAgedListCarriesNoRuleProcessor} refuses the combination
 * in a shipped file rather than leaving it to be found in a world. Express the second stage as
 * another {@code surface_aging} entry instead.</p>
 *
 * <p><strong>Decoration was never at risk</strong>, which is why the phase split needed no change in
 * GottschCore. {@code PieceProcessors} requires decoration to see what aging did &mdash; cobwebs in
 * the gap a crumbled stair left, growth on the dirt aging produced &mdash; and
 * {@code dungeons2:decoration} has always worked in {@code finalizeProcessing} too (it has no
 * {@code processBlock} at all). Aging reaches it from either phase. The standing requirement is
 * unchanged: <strong>this entry must precede {@code dungeons2:decoration} in the file</strong>.</p>
 *
 * <p>Output is untouched where no rule names a new surface: the roll is still seeded from the
 * block's own world position and the rules are still walked in authored order.</p>
 */
public class SurfaceAgingProcessor extends StructureProcessor implements LevelIndependentProcessor {

    private final Supplier<StructureProcessorType<?>> type;
    private final int agings;
    private final List<SurfaceAgingRule> rules;
    /** Rules indexed by source block, preserving authored order, for an O(1) miss. */
    private final Map<Block, List<SurfaceAgingRule>> rulesByBlock;
    /**
     * Whether any rule asks a question the piece's own geometry has to answer. Decided once at
     * construction, because a processor is built from the datapack and then reused for every piece
     * of every dungeon: a list that only names {@code floor} / {@code above_floor} / {@code any}
     * never pays for a scan it cannot use.
     */
    private final boolean needsTheWholePiece;

    public SurfaceAgingProcessor(Supplier<StructureProcessorType<?>> type, int agings,
                                 List<SurfaceAgingRule> rules) {
        this.type = type;
        this.agings = agings;
        this.rules = List.copyOf(rules);
        this.rulesByBlock = new HashMap<>();
        for (SurfaceAgingRule rule : this.rules) {
            rulesByBlock.computeIfAbsent(rule.block(), block -> new ArrayList<>()).add(rule);
        }
        this.needsTheWholePiece = this.rules.stream().anyMatch(rule -> GEOMETRIC.contains(rule.surface()));
    }

    /** The surfaces {@link PieceSurfaceMap} has to scan the piece to tell apart. */
    private static final Set<PieceSurface> GEOMETRIC =
            Set.of(PieceSurface.WALL, PieceSurface.CEILING, PieceSurface.JOIST);

    /** See {@code Registration} for the codec/type registration idiom. */
    public static Codec<SurfaceAgingProcessor> codec(Supplier<StructureProcessorType<?>> type) {
        return RecordCodecBuilder.create(instance -> instance.group(
                StrictCodecs.strictOptionalFieldOf(Codec.INT, "agings", 1)
                        .forGetter(processor -> processor.agings),
                SurfaceAgingRule.CODEC.listOf().fieldOf("rules")
                        .forGetter(processor -> processor.rules)
        ).apply(instance, (agings, rules) -> new SurfaceAgingProcessor(type, agings, rules)));
    }

    /**
     * Ages one block, when this processor's rules can be answered from Y alone.
     *
     * <p>Which is to say: unless a rule names {@code wall}, {@code ceiling} or {@code joist}, this
     * processor stays exactly where it always was, and so keeps the one thing the later phase
     * cannot offer &mdash; a {@code minecraft:rule} entry after it in the file still sees what it
     * did. See {@link #finalizeProcessing} for the trade, and {@code ProcessorPhaseOrderTest} for
     * the phase rules it turns on.</p>
     */
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level, BlockPos piecePos, BlockPos structurePos,
            StructureTemplate.StructureBlockInfo original,
            StructureTemplate.StructureBlockInfo current,
            StructurePlaceSettings settings) {

        return needsTheWholePiece ? current : age(current, piecePos, null);
    }

    /**
     * Ages the piece, classifying it once via {@link PieceSurfaceMap} and then walking every block
     * exactly as the per-block version does.
     *
     * <p>Only for a processor whose rules need the piece's geometry, since {@code processBlock}
     * cannot answer a question about a cell's neighbours. The map is built from
     * {@code processedBlocks} <em>as handed over</em>, i.e. the architecture before any of it
     * decays, so a cell's surface never depends on what the rolls did to its neighbours &mdash; a
     * wall that crumbles to a gap does not turn the course above it into a joist.</p>
     *
     * <p><strong>What deciding here costs, and why it is opt-in.</strong> Vanilla runs every
     * {@code processBlock} before any {@code finalizeProcessing}, so a processor that decides here
     * can be <em>fed</em> by anything but can only <em>feed</em> another finalize-phase processor.
     * Two {@code surface_aging} entries still chain, so multi-step aging across processors is
     * intact; what is out of reach is a {@code minecraft:rule} authored after this one, which would
     * read as a second stage and silently not be one. That is why only a list actually asking a
     * geometric question pays for it, and why
     * {@code StratumWeatheringListTest.aSurfaceAgedListCarriesNoRuleProcessor} refuses the
     * combination rather than leaving it to be discovered in a world.</p>
     */
    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level, BlockPos piecePos, BlockPos relativePos,
            List<StructureTemplate.StructureBlockInfo> originalBlocks,
            List<StructureTemplate.StructureBlockInfo> processedBlocks,
            StructurePlaceSettings settings) {

        if (!needsTheWholePiece || rulesByBlock.isEmpty()) {
            return processedBlocks;
        }

        PieceSurfaceMap surfaces = PieceSurfaceMap.of(piecePos, processedBlocks);
        List<StructureTemplate.StructureBlockInfo> aged = new ArrayList<>(processedBlocks.size());
        for (StructureTemplate.StructureBlockInfo info : processedBlocks) {
            aged.add(age(info, piecePos, surfaces));
        }
        return aged;
    }

    /**
     * One block's decay, or {@code current} unchanged if no rule of its surface takes.
     *
     * @param surfaces the piece's classification, or {@code null} when this processor's rules can
     *                 be answered from Y alone and no piece was scanned
     */
    private StructureTemplate.StructureBlockInfo age(
            StructureTemplate.StructureBlockInfo current, BlockPos piecePos,
            PieceSurfaceMap surfaces) {

        List<SurfaceAgingRule> candidates = rulesByBlock.get(current.state().getBlock());
        if (candidates == null) {
            return current;
        }

        // `current.pos()` is already in world space, which is what the map is keyed on. Rotation
        // and mirroring are horizontal, so neither disturbs the surface.
        PieceSurface primary = surfaces != null
                ? surfaces.classify(current.pos())
                : PieceSurfaceMap.byHeight(piecePos, current.pos());

        RandomSource random = RandomSource.create(Mth.getSeed(current.pos()));
        Block aged = null;
        for (SurfaceAgingRule rule : candidates) {
            if (!rule.surface().matches(primary)) {
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
