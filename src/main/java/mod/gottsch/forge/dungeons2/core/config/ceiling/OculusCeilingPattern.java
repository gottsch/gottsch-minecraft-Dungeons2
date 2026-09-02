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
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector.Layer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An <strong>oculus</strong>: a lit shaft rising over the middle of the room. Backlog #77.
 *
 * <h2>It is not a skylight, and that is the design</h2>
 * <p>No daylight (Mark). A shaft to the surface is a different claim entirely and this is a dungeon.
 * What is built instead is a <strong>lamp at the top of the shaft with a grate under it</strong>: the
 * light passes through the grate, and what a player sees looking up is a lit grille with the source
 * hidden behind it, and a well of light on the floor. That reads as something that was built and lit,
 * not as a hole to the sky.</p>
 *
 * <h2>It is #68's machinery with the material swapped for air</h2>
 * <p>The rising vault proved a ceiling pattern can excavate upward into the floor's own unspent
 * budget ({@code CeilingSurface#emitRaised} clears the column beneath a raised layer before writing
 * it). An oculus is that, with nothing in the column: two raised layers, one for the cap and one for
 * the grate, and the excavation falls out.</p>
 *
 * <p>This entry exists rather than leaving it hand-stacked for {@code vaulted}'s reason: two layers
 * whose rises must stay one apart, in an order that silently matters, is a lot of ways to get a
 * dark hole instead of a lamp.</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code cap_block} &mdash; required, the lamp at the top of the shaft. Glowstone is the
 *       shipped choice. Required because the cap layer is what excavates: an oculus with nothing at
 *       the top is not a dark shaft, it is no shaft at all.</li>
 *   <li>{@code grate_block} &mdash; optional, one row below the cap, hiding it. It must be a block
 *       <strong>light passes through</strong> &mdash; a grate, a trapdoor, bars. Nothing here can
 *       check that, and a solid one gives a dark shaft with a lamp sealed above it.</li>
 *   <li>{@code size} &mdash; the shaft's side in cells, centred. Named {@code size} and not
 *       {@code radius} to match {@code centre}, which measures the same thing the same way; the
 *       backlog entry said radius and one convention is worth more than either word.</li>
 *   <li>{@code depth} &mdash; how far the shaft rises, from {@value #MIN_DEPTH}. Two is the minimum
 *       for a reason, not a taste: at one the grate would land in the ceiling PLANE, which is a flush
 *       layer, and flush layers are written before the raised ones excavate &mdash; the grate would
 *       be cleared away again by its own shaft.</li>
 *   <li>{@code properties} &mdash; applied to both states, as everywhere else.</li>
 * </ul>
 *
 * <h2>What it does NOT decide, and the shallow-room degrade</h2>
 * <p>How far it may actually rise: every raised layer is clamped at render time to the rows this
 * floor has left above this room ({@code BasicCeilingGenerator#withRiseBudget}). A room with one row
 * to spare gets the cap and the grate at the same height, so the grate wins and the oculus reads as
 * an unlit grille set in the ceiling; with none it flattens into the ceiling plane and reads the
 * same. Both are a dark grate rather than a hole into rock, which is the right way for this to fail.
 * A scheme that wants the light reliably belongs in a motif whose floors have budget to spare.</p>
 *
 * <h2>The shaft's sides are raw rock</h2>
 * <p>Deliberately not lined, and the same as every {@code vaulted} step: the excavation clears its
 * own cells and the ones beside them were never the pattern's to write. A shaft two or three rows
 * tall over a grate shows almost none of it. Lining would need a layer that writes a ring
 * <em>without</em> excavating it, which the raised-layer contract cannot express &mdash; a
 * {@code SurfacePlan} cell means "clear below me and write me here".</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record OculusCeilingPattern(String capBlock, Optional<String> grateBlock, int size,
                                   int depth, Map<String, String> properties)
        implements CeilingPattern {

    public static final String NAME = "oculus";

    /** Three cells across: wide enough to read as a shaft rather than as a chimney flue. */
    public static final int DEFAULT_SIZE = 3;

    /** Two rows: the cap, and the grate one row under it. See the class doc on why not one. */
    public static final int MIN_DEPTH = 2;

    /** Two: as shallow as an oculus can be, and affordable in the worst shipped spare budget. */
    public static final int DEFAULT_DEPTH = 2;

    /** A plain lit shaft with no grate. */
    public OculusCeilingPattern(String capBlock) {
        this(capBlock, Optional.empty(), DEFAULT_SIZE, DEFAULT_DEPTH, Map.of());
    }

    /** The shipped form: a lamp with a grate under it. */
    public OculusCeilingPattern(String capBlock, String grateBlock) {
        this(capBlock, Optional.of(grateBlock), DEFAULT_SIZE, DEFAULT_DEPTH, Map.of());
    }

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedCap = Codecs.resolveRole(capBlock, resolver);
        Optional<String> resolvedGrate = Codecs.resolveRole(grateBlock, resolver);
        if (resolvedCap.equals(capBlock) && resolvedGrate.equals(grateBlock)) {
            return this;
        }
        return new OculusCeilingPattern(resolvedCap, resolvedGrate, size, depth, properties);
    }

    public static final MapCodec<OculusCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("cap_block")
                            .forGetter(OculusCeilingPattern::capBlock),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "grate_block")
                            .forGetter(OculusCeilingPattern::grateBlock),
                    // From 1: a one-cell oculus is a lamp recess, which is a real and useful thing.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "size",
                            DEFAULT_SIZE).forGetter(OculusCeilingPattern::size),
                    // Bounded above by MAX_RISE like every other rise, and below by MIN_DEPTH for
                    // the ordering reason in the class doc -- at 1 the grate is a flush layer and
                    // its own shaft erases it.
                    Codecs.strictOptionalFieldOf(
                                    Codec.intRange(MIN_DEPTH, CeilingPatternEntry.MAX_RISE), "depth",
                                    DEFAULT_DEPTH)
                            .forGetter(OculusCeilingPattern::depth),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(OculusCeilingPattern::properties)
            ).apply(instance, OculusCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    /**
     * The cap first, then the grate one row below it.
     *
     * <p><strong>That order is load-bearing.</strong> Raised layers are emitted in the order they
     * are added here, and each one clears every row from the ceiling plane up to its own before
     * writing itself. Cap first means the grate's shorter excavation reopens the column beneath it
     * and leaves the cap alone. Grate first and the cap's taller excavation would clear the grate
     * away again, leaving a bare lit shaft &mdash; the same "author the steps in ascending order"
     * rule {@code vaulted} depends on, which is why both are written as one entry rather than left
     * to a {@code patterns} list.</p>
     *
     * <p>{@code depth} is measured from where the entry sits, which is normally the ceiling plane.
     * An entry that also carries a {@code rise} lifts the whole shaft before it starts.</p>
     */
    @Override
    public void addLayers(int depth, List<Layer> out) {
        BlockState cap = CeilingPattern.state(capBlock, properties);
        if (cap == null) {
            return;
        }
        out.add(new Layer(depth - this.depth, new CentreSurfacePatternProvider(size, cap)));
        grateBlock.map(id -> CeilingPattern.state(id, properties))
                .ifPresent(grate -> out.add(new Layer(depth - this.depth + 1,
                        new CentreSurfacePatternProvider(size, grate))));
    }
}
