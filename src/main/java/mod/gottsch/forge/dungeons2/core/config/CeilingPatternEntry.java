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
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<CeilingPatternEntry> CODEC = Codecs.closed(
            RecordCodecBuilder.<CeilingPatternEntry>mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(SurfacePatternEntry.CODEC.listOf(), "patterns", List.of())
                            .forGetter(CeilingPatternEntry::patterns),
                    SizeGate.MAP_CODEC.forGetter(CeilingPatternEntry::gate)
            ).apply(instance, CeilingPatternEntry::new)))
            .flatXmap(CeilingPatternEntry::validate, CeilingPatternEntry::validate);

    /**
     * This treatment with only the patterns a room of these dimensions actually draws.
     *
     * <p>The ceiling's counterpart to {@code WallPatternEntry#forRoom}, and the last list in the
     * schema to get one &mdash; wall courses, every element slot and the scheme itself could all be
     * gated entry by entry, and {@code ceiling.patterns} arbitrarily could not.</p>
     *
     * <p>Returns {@code this} when nothing gated out, so the common case allocates nothing and stays
     * {@code ==}-identical for callers that care.</p>
     */
    public CeilingPatternEntry forRoom(int width, int depth, int height) {
        List<SurfacePatternEntry> fitting = patterns.stream()
                .filter(pattern -> pattern.gate().fits(width, depth, height))
                .toList();
        return fitting.size() == patterns.size() ? this : new CeilingPatternEntry(fitting, gate);
    }

    /**
     * Rejects an {@code orient} on a pattern type that cannot apply one.
     *
     * <p>Only {@code border} orients, because only a ring has an outward direction to orient
     * <em>to</em>: a {@code coffers} rib is a line with open room on both sides, and a {@code centre}
     * boss is a solid block with no edge at all. Neither has a defensible answer, so neither invents
     * one.</p>
     *
     * <p>Failing the load rather than ignoring the field is the same rule the strict codecs in this
     * package follow. An ignored {@code orient} would produce a ceiling that is exactly as correct as
     * it was before the author wrote the line &mdash; a silent nothing, and the hardest kind of
     * authoring mistake to see, since the pattern itself still draws.</p>
     */
    private static DataResult<CeilingPatternEntry> validate(CeilingPatternEntry entry) {
        for (SurfacePatternEntry pattern : entry.patterns()) {
            if (pattern.orient() != SurfaceOrient.NONE && !pattern.orientable()) {
                return DataResult.error(() -> "ceiling pattern '" + pattern.type()
                        + "': orient is only meaningful on a 'border', which has an outward"
                        + " direction to face; this type has none");
            }
            // An inverted per-entry gate fits no room, so the pattern silently never draws --
            // indistinguishable at generation time from one that merely never came up, which is
            // exactly what SizeGate#validate exists to turn into a load error.
            DataResult<SizeGate> gate = pattern.gate()
                    .validate("ceiling pattern '" + pattern.type() + "'");
            if (gate.error().isPresent()) {
                return DataResult.error(() -> gate.error().orElseThrow().message());
            }
        }
        return DataResult.success(entry);
    }

    /**
     * Which way a pattern turns a block that has a {@code facing} property &mdash; the ceiling's
     * counterpart to a wall course's {@code CourseOrient}.
     *
     * <p>Named for the ring rather than for the room's walls, because a ring at {@code inset: 2} has
     * no wall to be toward: {@code toward_wall} would be a lie at every inset but zero. Outward means
     * a directional block's solid mass sits on the ring's outer side &mdash; for stairs, the vault
     * springing off the perimeter and stepping down into the room.</p>
     *
     * <p><strong>Authors of {@code dungeonblocks} trim:</strong> its directional mouldings are
     * modelled facing-inverted relative to vanilla stairs, so the same visual result needs the
     * opposite value here, exactly as it does for a wall course. See {@code CourseOrient}'s note.</p>
     */
    public enum SurfaceOrient implements StringRepresentable {
        /** Leave {@code facing} alone -- for full cubes, or when {@code properties} sets it. */
        NONE("none"),
        /** Solid mass toward the ring's outer edge: a springing, a soffit leaning on the wall. */
        OUTWARD("outward"),
        /** Solid mass toward the ring's middle -- an inverted cove, stepping up to the centre. */
        INWARD("inward");

        private final String name;

        SurfaceOrient(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Failing, for the same reason {@code CourseAnchor.CODEC} is: the set is closed and tiny. */
        public static final Codec<SurfaceOrient> CODEC = StringRepresentable.fromEnum(SurfaceOrient::values);
    }

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
     *
     * <h2>orient and properties &mdash; what makes a stepped vault a vault</h2>
     * <p>{@code properties} applies author-named block properties to both {@code block} and
     * {@code cornerBlock}, exactly as a wall course's does and for the same reason: they are one
     * block family, and a corner stair that missed its {@code half=top} is a very quiet defect. It
     * applies to every pattern type, since it says nothing about geometry.</p>
     *
     * <p>{@code orient} ({@link SurfaceOrient}) is <strong>{@code border} only</strong>, and a
     * load error elsewhere &mdash; see {@link CeilingPatternEntry#validate}. Together the two turn a
     * ring of full cubes into a ring of stairs springing off the room's edge, which is the
     * difference between a stepped vault and blocky corbelling. A ring at {@code inset: 0},
     * {@code projection: 2} with a second at {@code inset: 1}, {@code projection: 1} is a two-step
     * vault: perimeter dropped twice, then once, with the centre field left at full height.</p>
     *
     * <h2>Per-entry gating</h2>
     * <p>The four flat {@link SizeGate} fields work here exactly as they do on a wall course, and
     * for the same reason: a ceiling is routinely <em>partly</em> size-dependent. A coffered lattice
     * belongs on every ceiling in the dungeon, while the boss at its centre is a lonely dot in a
     * small room and a projecting ring needs headroom a 5-high room does not have. With only a slot
     * gate that is two schemes again &mdash; the duplication element gates exist to remove &mdash;
     * or a lattice that vanishes from small rooms for no reason.</p>
     *
     * <p>All patterns gating out leaves an empty list, which the selector already renders as a plain
     * ceiling.</p>
     */
    public record SurfacePatternEntry(String type, Optional<String> block, Optional<String> cornerBlock,
                                      int inset, int spacing, int size, int projection,
                                      SurfaceOrient orient, Map<String, String> properties,
                                      SizeGate gate) {

        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<SurfacePatternEntry> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(SurfacePatternEntry::type),
                Codecs.strictOptionalFieldOf(Codec.STRING, "block").forGetter(SurfacePatternEntry::block),
                Codecs.strictOptionalFieldOf(Codec.STRING, "cornerBlock")
                        .forGetter(SurfacePatternEntry::cornerBlock),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                        BorderSurfacePatternProvider.DEFAULT_INSET).forGetter(SurfacePatternEntry::inset),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spacing",
                        GridSurfacePatternProvider.DEFAULT_SPACING).forGetter(SurfacePatternEntry::spacing),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "size",
                        CentreSurfacePatternProvider.DEFAULT_SIZE).forGetter(SurfacePatternEntry::size),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, WallPatternEntry.MAX_PROJECTION),
                        "projection", 0).forGetter(SurfacePatternEntry::projection),
                Codecs.strictOptionalFieldOf(SurfaceOrient.CODEC, "orient", SurfaceOrient.NONE)
                        .forGetter(SurfacePatternEntry::orient),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "properties", Map.of()).forGetter(SurfacePatternEntry::properties),
                SizeGate.MAP_CODEC.forGetter(SurfacePatternEntry::gate)
        ).apply(instance, SurfacePatternEntry::new)));

        /** Convenience for tests and simple entries: type plus its one required block, drawn flush. */
        public SurfacePatternEntry(String type, String block) {
            this(type, Optional.of(block), Optional.empty(),
                    BorderSurfacePatternProvider.DEFAULT_INSET,
                    GridSurfacePatternProvider.DEFAULT_SPACING,
                    CentreSurfacePatternProvider.DEFAULT_SIZE, 0);
        }

        /** The shape before {@code orient}/{@code properties} existed: an unoriented plain pattern. */
        public SurfacePatternEntry(String type, Optional<String> block, Optional<String> cornerBlock,
                                   int inset, int spacing, int size, int projection) {
            this(type, block, cornerBlock, inset, spacing, size, projection,
                    SurfaceOrient.NONE, Map.of());
        }

        /** An ungated treatment -- drawn whenever its scheme is rolled. The shape before gates. */
        public SurfacePatternEntry(String type, Optional<String> block, Optional<String> cornerBlock,
                                   int inset, int spacing, int size, int projection,
                                   SurfaceOrient orient, Map<String, String> properties) {
            this(type, block, cornerBlock, inset, spacing, size, projection, orient, properties,
                    SizeGate.UNBOUNDED);
        }

        /**
         * Whether this type has an outward direction for {@link #orient} to mean anything by. Only
         * {@code border} does; see {@link CeilingPatternEntry#validate}.
         */
        public boolean orientable() {
            return BORDER.equals(type().trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * The one orientable pattern type. Lower-cased and compared after trimming, matching how
     * {@code CeilingPatternSelector} dispatches, so validation and dispatch cannot disagree about
     * whether {@code " Border "} is a border.
     */
    public static final String BORDER = "border";
}
