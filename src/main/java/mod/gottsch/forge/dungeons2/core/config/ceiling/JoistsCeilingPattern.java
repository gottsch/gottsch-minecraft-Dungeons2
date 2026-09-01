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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector.Layer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CeilingSurface;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.JoistSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parallel beams crossing the ceiling one way, optionally resting on end brackets.
 *
 * <p><strong>The only type that draws two layers from one authored pattern:</strong> the beams at
 * the authored projection, and the brackets one row <em>below</em> them, because a bracket carries
 * its beam from underneath rather than standing in its row. Splitting them here rather than inside
 * the provider is what lets {@code LayeredSurfacePatternProvider}'s depth grouping do the work.</p>
 *
 * <p>An absent {@code bracket_block} means the beams run bare, and a bracket that will not resolve
 * is the same answer &mdash; a typo in the trim should not delete the beams it was decorating.</p>
 */
public record JoistsCeilingPattern(String block, int spacing, Optional<String> bracketBlock,
                                   SurfaceOrient orient, Map<String, String> properties)
        implements CeilingPattern {

    public static final String NAME = "joists";

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        Optional<String> resolvedBracketBlock = Codecs.resolveRole(bracketBlock, resolver);
        if (resolvedBlock.equals(block)
                && resolvedBracketBlock.equals(bracketBlock)) {
            return this;
        }
        return new JoistsCeilingPattern(resolvedBlock, spacing, resolvedBracketBlock, orient, properties);
    }


    /** Bare beams of one block at the default rhythm -- no bracket, unoriented. */
    public JoistsCeilingPattern(String block) {
        this(block, JoistSurfacePatternProvider.DEFAULT_SPACING, Optional.empty(),
                SurfaceOrient.NONE, Map.of());
    }

    public static final MapCodec<JoistsCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(JoistsCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spacing",
                                    JoistSurfacePatternProvider.DEFAULT_SPACING)
                            .forGetter(JoistsCeilingPattern::spacing),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "bracket_block")
                            .forGetter(JoistsCeilingPattern::bracketBlock),
                    Codecs.strictOptionalFieldOf(SurfaceOrient.CODEC, "orient", SurfaceOrient.NONE)
                            .forGetter(JoistsCeilingPattern::orient),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(JoistsCeilingPattern::properties)
            ).apply(instance, JoistsCeilingPattern::new)));

    /**
     * <strong>The half of the old {@code orient} validation that survives</strong>, checked from
     * {@code CeilingPatternEntry.validate} because that is where the codec is a {@link Codec} and
     * can carry a {@code flatXmap}. The other half -- "orient is meaningless on a type with no
     * direction" -- is now enforced by the schema, because {@code coffers} and {@code centre}
     * simply do not declare the field. This one cannot be: it is a relationship between two fields
     * of <em>this</em> type, and only a check can see it.
     *
     * <p>{@code orient} turns the end BRACKET. The beams themselves take their axis from the run,
     * so an oriented joists with nothing to turn is an authoring mistake that would otherwise do
     * nothing at all, silently.</p>
     */
    public boolean orientsNothing() {
        return orient != SurfaceOrient.NONE && bracketBlock.isEmpty();
    }

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state == null) {
            return;
        }
        out.add(new Layer(projection, JoistSurfacePatternProvider.beams(spacing, state)));

        BlockState bracket = bracketBlock.map(id -> CeilingPattern.state(id, properties)).orElse(null);
        if (bracket != null) {
            out.add(new Layer(projection + 1, JoistSurfacePatternProvider.brackets(spacing, bracket,
                    orient, CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION)));
        }
    }
}
