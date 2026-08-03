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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;

import java.util.List;
import java.util.Optional;

/**
 * A {@link RoomScheme}'s {@code ceiling} slot: an <strong>ordered list</strong> of treatments laid
 * over the room's plain ceiling block, each one drawn on top of the last.
 *
 * <h2>Why a list, and why no "composite" type</h2>
 * <p>The floor needed a dedicated {@code "composite"} pattern type because its generators fill every
 * cell, so layering required a second entry point ({@code IFloorOverlayGenerator}) and a wrapper to
 * drive it. Surface patterns are sparse &mdash; a cell a pattern does not care about is left null
 * &mdash; so layering is just applying them in sequence, later non-null winning. That makes the
 * list itself the composition mechanism, and a wrapper type unnecessary. This is the shape the wall
 * slot is expected to grow into when it gains a second pattern type.</p>
 *
 * <p>Ordering is execution order, the same convention the {@code processor_list} files and
 * {@code CompositeFloorPatternProvider} use: put the broad fill first and the accents after, so a
 * {@code centre} boss lands on top of a {@code coffers} lattice rather than under it.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public record CeilingPatternEntry(List<SurfacePatternEntry> patterns, SizeGate gate) {

    /** An ungated treatment -- drawn whenever its scheme is rolled. */
    public CeilingPatternEntry(List<SurfacePatternEntry> patterns) {
        this(patterns, SizeGate.UNBOUNDED);
    }

    public static final Codec<CeilingPatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codecs.strictOptionalFieldOf(SurfacePatternEntry.CODEC.listOf(), "patterns", List.of())
                    .forGetter(CeilingPatternEntry::patterns),
            SizeGate.MAP_CODEC.forGetter(CeilingPatternEntry::gate)
    ).apply(instance, CeilingPatternEntry::new));

    /**
     * One treatment. {@code type} is a plain string discriminator, same as
     * {@link FloorPatternEntry}'s; an unrecognized value is skipped, the same graceful degradation
     * an unrecognized floor type gets.
     *
     * <ul>
     *   <li>{@code "border"} &mdash; a ring inset from the edge, reading as a soffit. Uses
     *       {@code block} for the edges and {@code cornerBlock} for the four corners, plus
     *       {@code inset} (default {@value BorderSurfacePatternProvider#DEFAULT_INSET}).</li>
     *   <li>{@code "coffers"} &mdash; a lattice of ribs dividing the ceiling into panels. Uses
     *       {@code block} and {@code spacing} (default
     *       {@value GridSurfacePatternProvider#DEFAULT_SPACING}).</li>
     *   <li>{@code "centre"} &mdash; a square boss at the middle. Uses {@code block} and
     *       {@code size} (default {@value CentreSurfacePatternProvider#DEFAULT_SIZE}).</li>
     * </ul>
     *
     * <p>{@code block} is required by every type: there is deliberately no Java-side default for a
     * pattern's material, so an absent, malformed or unregistered id skips that pattern rather than
     * substituting a guess &mdash; the same rule the floor and wall patterns follow.
     * {@code cornerBlock} is the one exception, and not really one: when absent it falls back to
     * {@code block}, which is another <em>authored</em> value rather than a guessed block, and gives
     * a uniform ring without repeating the id.</p>
     *
     * <h2>projection &mdash; the difference between a coffered ceiling and a coffered pattern</h2>
     * <p>{@code projection} (default 0) hangs the treatment below the ceiling plane: {@code 0} draws
     * it flush <em>in</em> the ceiling, {@code 1} puts it in the cell underneath. Same meaning and
     * same cap as a wall course's, and it matters most to {@code coffers} &mdash; ribs flush in the
     * ceiling are the same plane as the panels they are supposed to be dividing, so the lattice
     * reads as texture. Hung one cell down, the panels are genuinely recessed and it reads as
     * structure.</p>
     *
     * <p>Like a wall's, a projecting treatment is <strong>absent from the plane</strong>: the
     * ceiling behind it stays base block, which is exactly what a recessed panel is. The outer ring
     * is left to the walls' own trim &mdash; see {@code CeilingSurface#emitProjected}.</p>
     *
     * <p>Headroom is the scheme's problem, not this record's: a rib hanging into a 5-high room
     * leaves two interior rows, so a projecting ceiling wants a {@code minHeight}.</p>
     */
    public record SurfacePatternEntry(String type, Optional<String> block, Optional<String> cornerBlock,
                                      int inset, int spacing, int size, int projection) {

        public static final Codec<SurfacePatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(SurfacePatternEntry::type),
                Codec.STRING.optionalFieldOf("block").forGetter(SurfacePatternEntry::block),
                Codec.STRING.optionalFieldOf("cornerBlock").forGetter(SurfacePatternEntry::cornerBlock),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                        BorderSurfacePatternProvider.DEFAULT_INSET).forGetter(SurfacePatternEntry::inset),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spacing",
                        GridSurfacePatternProvider.DEFAULT_SPACING).forGetter(SurfacePatternEntry::spacing),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "size",
                        CentreSurfacePatternProvider.DEFAULT_SIZE).forGetter(SurfacePatternEntry::size),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, WallPatternEntry.MAX_PROJECTION),
                        "projection", 0).forGetter(SurfacePatternEntry::projection)
        ).apply(instance, SurfacePatternEntry::new));

        /** Convenience for tests and simple entries: type plus its one required block, drawn flush. */
        public SurfacePatternEntry(String type, String block) {
            this(type, Optional.of(block), Optional.empty(),
                    BorderSurfacePatternProvider.DEFAULT_INSET,
                    GridSurfacePatternProvider.DEFAULT_SPACING,
                    CentreSurfacePatternProvider.DEFAULT_SIZE, 0);
        }
    }
}
