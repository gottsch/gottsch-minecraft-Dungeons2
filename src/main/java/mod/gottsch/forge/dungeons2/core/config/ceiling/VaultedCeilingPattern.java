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
package mod.gottsch.forge.dungeons2.core.config.ceiling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector.Layer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CeilingSurface;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.FieldSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A stepped vault that RISES above the ceiling plane, as one entry: {@code steps} corbelled courses
 * climbing into the floor's unspent budget, with an optional springing course at the lip and an
 * optional distinct crown (#68).
 *
 * <h2>This is the type; {@code field} is the primitive</h2>
 * <p>A rising vault is expressible as a stack of {@link FieldCeilingPattern} entries at increasing
 * {@code inset} and {@code rise} &mdash; that is exactly what this expands to, and the first cut of
 * #68 shipped only that. It is the wrong thing to make an author write. Three or four near-identical
 * blocks per vault, with two numbers that must stay in step and an ORDER that silently matters (a
 * step authored out of sequence roofs the one above it), is a lot of ways to get a dome wrong. Here
 * the steps are one number.</p>
 *
 * <p>{@code field} stays registered and public, because it is genuinely useful on its own &mdash; an
 * inner panel of a second material, flush &mdash; and because a vault that wants irregular steps
 * (two rows here, one there) can still be hand-stacked from it. This type is the common case, not a
 * replacement for the primitive.</p>
 *
 * <h2>Why there is no {@code direction: down}</h2>
 * <p>A hanging vault is not this shape upside down. It is drawn as RINGS, because only its perimeter
 * moves and the ceiling behind each ring stays where it is; a rising vault moves the whole interior,
 * which is an area (see {@link FieldSurfacePatternProvider}). Sharing one type between them would
 * mean one flag switching the geometry class, so the hanging form stays what it already is: a list
 * of {@code border} entries at increasing {@code projection}, as {@code vaulted_hall} authors it.</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code block} &mdash; required, the material of every step.</li>
 *   <li>{@code steps} &mdash; how many courses climb, default
 *       {@value #DEFAULT_STEPS}. {@code steps} &times; {@code step_height} may not exceed
 *       {@link CeilingPatternEntry#MAX_RISE}; that is a load error rather than a clamp, because a
 *       vault quietly drawing shallower than authored is exactly the kind of thing nobody notices.</li>
 *   <li>{@code step_height} &mdash; rows per step, default 1. 2 gives a steeper dome in half the
 *       courses.</li>
 *   <li>{@code step_inset} &mdash; cells each step draws in from the last, default 1. 2 gives a
 *       shallower dome, which is what a large room wants.</li>
 *   <li>{@code springing_block} &mdash; optional course in the ceiling PLANE at the vault's lip.
 *       This is what makes the first step read as springing off the wall rather than as a shelf.
 *       <strong>Use a solid block, not stairs.</strong> Stairs belong on a HANGING vault, where the
 *       viewer sees their face and their underside; a rising vault's lip sits in the ceiling plane
 *       with the room's air below it, so the only face ever visible is the bottom one &mdash; a plain
 *       full square on a stair, which reads as a full block at best and as a backwards one at worst
 *       (Mark, in game 2026-09-01). Accent the lip by MATERIAL rather than by shape.</li>
 *   <li>{@code crown_block} &mdash; optional distinct material for the topmost step. Absent, the
 *       crown is {@code block}.</li>
 *   <li>{@code orient} &mdash; turns the springing course only, default
 *       {@code inward} (solid mass toward the middle, stepping up into the vault). Meaningless
 *       without {@code springing_block}, and rejected in that case for the same reason an oriented
 *       joists with no bracket is.</li>
 *   <li>{@code properties} &mdash; applied to every state this places, as everywhere else.</li>
 * </ul>
 *
 * <h2>What it does NOT decide</h2>
 * <p>How far it may actually rise. Every step is clamped at render time to the rows this floor has
 * left above this room ({@code BasicCeilingGenerator#withRiseBudget}), so a four-step vault in a room
 * with two rows to spare comes out as a two-step one with a flat top rather than as a hole. The
 * schema bound above is about what is authorable; the room decides what is drawn.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public record VaultedCeilingPattern(String block, int steps, int stepHeight, int stepInset,
                                    Optional<String> springingBlock, Optional<String> crownBlock,
                                    SurfaceOrient orient, Map<String, String> properties)
        implements CeilingPattern {

    public static final String NAME = "vaulted";

    /** Three courses: enough to read as a dome, and affordable in the worst shipped spare budget. */
    public static final int DEFAULT_STEPS = 3;

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        Optional<String> resolvedSpringing = Codecs.resolveRole(springingBlock, resolver);
        Optional<String> resolvedCrown = Codecs.resolveRole(crownBlock, resolver);
        if (resolvedBlock.equals(block) && resolvedSpringing.equals(springingBlock)
                && resolvedCrown.equals(crownBlock)) {
            return this;
        }
        return new VaultedCeilingPattern(resolvedBlock, steps, stepHeight, stepInset,
                resolvedSpringing, resolvedCrown, orient, properties);
    }

    /** A plain three-step vault of one material, no springing course. */
    public VaultedCeilingPattern(String block) {
        this(block, DEFAULT_STEPS, 1, 1, Optional.empty(), Optional.empty(), SurfaceOrient.INWARD,
                Map.of());
    }

    public static final MapCodec<VaultedCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.<VaultedCeilingPattern>mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(VaultedCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, CeilingPatternEntry.MAX_RISE),
                            "steps", DEFAULT_STEPS).forGetter(VaultedCeilingPattern::steps),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, CeilingPatternEntry.MAX_RISE),
                            "step_height", 1).forGetter(VaultedCeilingPattern::stepHeight),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE),
                            "step_inset", 1).forGetter(VaultedCeilingPattern::stepInset),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "springing_block")
                            .forGetter(VaultedCeilingPattern::springingBlock),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "crown_block")
                            .forGetter(VaultedCeilingPattern::crownBlock),
                    Codecs.strictOptionalFieldOf(SurfaceOrient.CODEC, "orient", SurfaceOrient.INWARD)
                            .forGetter(VaultedCeilingPattern::orient),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(VaultedCeilingPattern::properties)
            ).apply(instance, VaultedCeilingPattern::new)).flatXmap(
                    VaultedCeilingPattern::validate, VaultedCeilingPattern::validate));

    /**
     * The two rules no field range can express, because both are relationships between fields.
     *
     * <p>The height one is a load error rather than a clamp for the reason the class doc gives: the
     * render-time clamp exists to fit a vault to a ROOM, which varies; an authored vault that could
     * never draw in full in any room is a mistake in the file, and clamping it would hide that.</p>
     */
    private static DataResult<VaultedCeilingPattern> validate(VaultedCeilingPattern pattern) {
        int total = pattern.steps() * pattern.stepHeight();
        if (total > CeilingPatternEntry.MAX_RISE) {
            return DataResult.error(() -> "ceiling pattern 'vaulted': " + pattern.steps()
                    + " steps of " + pattern.stepHeight() + " rows rises " + total
                    + " above the ceiling, past the maximum of " + CeilingPatternEntry.MAX_RISE
                    + ". Lower steps or step_height");
        }
        // Same rule, and the same reasoning, as an oriented joists with no bracket to turn: the
        // fields are areas and have no edge to face, so orient here turns the springing course
        // alone. Without one it is a line that silently does nothing.
        if (pattern.orient() != SurfaceOrient.NONE && pattern.springingBlock().isEmpty()
                && pattern.orientWasAuthored()) {
            return DataResult.error(() -> "ceiling pattern 'vaulted': orient turns the springing"
                    + " course, and this entry has no springing_block to turn; the steps themselves"
                    + " are areas and have no edge to face");
        }
        return DataResult.success(pattern);
    }

    /**
     * Whether {@code orient} can be blamed on the author.
     *
     * <p>The default is {@code inward} rather than {@code none} because it is the correct value for
     * the directional blocks that do end up here, and defaulting to {@code none} would make every
     * such entry carry the line. Most springing courses should be solid blocks, which {@code orient}
     * does not touch at all &mdash; see {@code springing_block} in the class doc. That default must not then fail the load of a perfectly good vault with no
     * springing course, so only a value the author could not have got by default is rejected. The
     * check is deliberately weak in one direction: an author who writes {@code "orient": "inward"}
     * with no springing block gets no error, because nothing distinguishes it from the default.</p>
     */
    private boolean orientWasAuthored() {
        return orient != SurfaceOrient.INWARD;
    }

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    /**
     * Expands to the layers a hand-stacked vault would have been written as: the springing course at
     * the entry's own depth, then one field per step, each further in and further up.
     *
     * <p>Emitted in ASCENDING order, which is not cosmetic &mdash; each step's excavation reopens the
     * roof the step below it laid over the same cells, so the highest step is what survives. Reverse
     * this loop and the vault comes out as a flat ceiling with a lump in it.</p>
     *
     * <p>{@code depth} is where the entry sits, and the steps are measured from it. That is normally
     * 0, the ceiling plane. An entry that also carries a {@code rise} lifts the whole vault before it
     * starts stepping, which is legal and occasionally what a very tall room wants.</p>
     */
    @Override
    public void addLayers(int depth, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state == null) {
            return;
        }
        springingBlock.map(id -> CeilingPattern.state(id, properties))
                .ifPresent(springing -> out.add(new Layer(depth,
                        new BorderSurfacePatternProvider(0, springing, springing, orient,
                                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION))));

        BlockState crown = crownBlock.map(id -> CeilingPattern.state(id, properties)).orElse(state);
        for (int step = 1; step <= steps; step++) {
            BlockState material = step == steps && crown != null ? crown : state;
            out.add(new Layer(depth - step * stepHeight,
                    new FieldSurfacePatternProvider(step * stepInset, material)));
        }
    }
}
