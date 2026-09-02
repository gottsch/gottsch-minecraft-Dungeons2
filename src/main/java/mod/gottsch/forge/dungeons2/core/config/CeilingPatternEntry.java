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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.JoistSurfacePatternProvider;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CeilingPatternRegistry;
import mod.gottsch.forge.dungeons2.core.config.ceiling.JoistsCeilingPattern;

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

    /** See {@code CeilingPattern#withRoles}. Returns {@code this} when no pattern named a role. */
    public CeilingPatternEntry withRoles(java.util.function.UnaryOperator<String> resolver) {
        List<SurfacePatternEntry> resolved = null;
        for (int i = 0; i < patterns.size(); i++) {
            SurfacePatternEntry entry = patterns.get(i);
            SurfacePatternEntry mapped = entry.withRoles(resolver);
            if (mapped == entry) {
                if (resolved != null) {
                    resolved.add(entry);
                }
                continue;
            }
            if (resolved == null) {
                resolved = new java.util.ArrayList<>(patterns.subList(0, i));
            }
            resolved.add(mapped);
        }
        return resolved == null ? this : new CeilingPatternEntry(List.copyOf(resolved), gate);
    }

    /** An ungated treatment -- drawn whenever its scheme is rolled. */
    public CeilingPatternEntry(List<SurfacePatternEntry> patterns) {
        this(patterns, SizeGate.UNBOUNDED);
    }

    // Codecs.closed -- see RoomScheme.CODEC.
        /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<CeilingPatternEntry> MAP_CODEC =
            RecordCodecBuilder.<CeilingPatternEntry>mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(SurfacePatternEntry.CODEC.listOf(), "patterns", List.of())
                            .forGetter(CeilingPatternEntry::patterns),
                    SizeGate.MAP_CODEC.forGetter(CeilingPatternEntry::gate)
            ).apply(instance, CeilingPatternEntry::new)).flatXmap(CeilingPatternEntry::validate, CeilingPatternEntry::validate);

    public static final Codec<CeilingPatternEntry> CODEC = Codecs.closed(MAP_CODEC);

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
     * Rejects an {@code orient} on a pattern type that cannot apply one, and an {@code orient} that
     * has nothing to turn.
     *
     * <p>{@code border} and {@code joists} orient, because each has a direction to orient <em>to</em>
     * &mdash; the ring's outward edge, and the wall a bracket rests on. A {@code coffers} rib is a
     * line with open room on both sides, and a {@code centre} boss is a solid block with no edge at
     * all. Neither has a defensible answer, so neither invents one. Note a joist's own beam cells are
     * a rib by that same argument: {@code orient} there turns the <strong>bracket</strong>, which is
     * why an oriented {@code joists} with no {@code bracket_block} is rejected too rather than
     * silently doing nothing.</p>
     *
     * <p>Failing the load rather than ignoring the field is the same rule the strict codecs in this
     * package follow. An ignored {@code orient} would produce a ceiling that is exactly as correct as
     * it was before the author wrote the line &mdash; a silent nothing, and the hardest kind of
     * authoring mistake to see, since the pattern itself still draws.</p>
     */
    private static DataResult<CeilingPatternEntry> validate(CeilingPatternEntry entry) {
        for (SurfacePatternEntry entryPattern : entry.patterns()) {
            // TWO OF THE THREE RULES THAT USED TO BE HERE ARE GONE, and neither was deleted --
            // both became impossible to author. "orient on a type with no direction to face" and
            // "bracket_block on something that is not a joists" were only expressible because every
            // ceiling type shared one flat record; now `orient` is declared by border and joists
            // alone and `bracketBlock` by joists alone, so either is a stray key and the closed
            // schema rejects it with a better message than these checks gave.
            //
            // This one survives because it is a relationship between two fields of the SAME type,
            // which no schema can see: orient turns the end BRACKET, so an oriented joists with no
            // bracketBlock has nothing to turn and would silently do nothing.
            if (entryPattern.pattern() instanceof JoistsCeilingPattern joists
                    && joists.orientsNothing()) {
                return DataResult.error(() -> "ceiling pattern 'joists': orient turns the end"
                        + " bracket, and this entry has no bracket_block to turn; the beams"
                        + " themselves take their axis from the run");
            }
            // #68. A layer hangs or it rises; it cannot do both, and a `depth` that silently picked
            // one would hide the contradiction in the one place an author cannot see it -- the
            // authored file says 2 and 3 and the ceiling shows one of them. Rejecting is also what
            // keeps `depth()`'s sign convention an implementation detail rather than a rule an
            // author has to know.
            if (entryPattern.projection() > 0 && entryPattern.rise() > 0) {
                return DataResult.error(() -> "ceiling pattern '"
                        + entryPattern.pattern().getClass().getSimpleName() + "': projection "
                        + entryPattern.projection() + " hangs the layer below the ceiling and rise "
                        + entryPattern.rise() + " raises it above; a layer can only do one. Drop"
                        + " whichever one you did not mean");
            }
            // An inverted per-entry gate fits no room, so the pattern silently never draws --
            // indistinguishable at generation time from one that merely never came up, which is
            // exactly what SizeGate#validate exists to turn into a load error.
            DataResult<SizeGate> gate = entryPattern.gate().validate("ceiling pattern '"
                    + entryPattern.pattern().getClass().getSimpleName() + "'");
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
     *       {@code block} for the edges and {@code corner_block} for the four corners, plus
     *       {@code inset} (default {@value BorderSurfacePatternProvider#DEFAULT_INSET}).</li>
     *   <li>{@code "coffers"} &mdash; a lattice of ribs dividing the ceiling into panels. Uses
     *       {@code block} and {@code spacing} (default
     *       {@value GridSurfacePatternProvider#DEFAULT_SPACING}).</li>
     *   <li>{@code "centre"} &mdash; a square boss at the middle. Uses {@code block} and
     *       {@code size} (default {@value CentreSurfacePatternProvider#DEFAULT_SIZE}).</li>
     *   <li>{@code "joists"} &mdash; parallel beams (rafters) crossing the room's <em>shorter</em>
     *       axis, reading as the floor above rather than as masonry. Uses {@code block},
     *       {@code spacing} (default {@value JoistSurfacePatternProvider#DEFAULT_SPACING}), and an
     *       optional {@code bracket_block} carrying each run's two ends <strong>from the row
     *       below</strong> &mdash; so a bracketed entry occupies two rows, not one. The block is
     *       <strong>not assumed to be timber</strong> &mdash; stone beams are equally legitimate,
     *       and are the ones that weather today.</li>
     * </ul>
     *
     * <p>{@code block} is required by every type: there is deliberately no Java-side default for a
     * pattern's material, so an absent, malformed or unregistered id skips that pattern rather than
     * substituting a guess &mdash; the same rule the floor and wall patterns follow.
     * {@code corner_block} is the one exception, and not really one: when absent it falls back to
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
     * leaves two interior rows, so a projecting ceiling wants a {@code min_height}.</p>
     *
     * <h2>orient and properties &mdash; what makes a stepped vault a vault</h2>
     * <p>{@code properties} applies author-named block properties to both {@code block} and
     * {@code corner_block}, exactly as a wall course's does and for the same reason: they are one
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
    /**
     * One authored treatment: the {@link CeilingPattern} itself, the depth it hangs at, and its
     * gate.
     *
     * <h2>What this used to be</h2>
     * <p>Eleven fields for four types with near-disjoint needs, plus a {@code type} string the
     * selector switched over: {@code corner_block} meant nothing to {@code coffers},
     * {@code bracket_block} nothing to {@code centre}, {@code size} nothing outside {@code centre}.
     * Each was a silent no-op. Every one of those fields now lives on the type that reads it.</p>
     *
     * <p><strong>{@code projection} stayed.</strong> It positions the pattern within the ceiling's
     * stack rather than describing the pattern's own shape, and the bracket layer is authored at
     * {@code projection + 1}, which is a fact about the stack rather than about joists.</p>
     */
    public record SurfacePatternEntry(CeilingPattern pattern, int projection, int rise, SizeGate gate) {

        /** See {@code CeilingPattern#withRoles}. */
        public SurfacePatternEntry withRoles(java.util.function.UnaryOperator<String> resolver) {
            CeilingPattern resolved = pattern.withRoles(resolver);
            return resolved == pattern ? this
                    : new SurfacePatternEntry(resolved, projection, rise, gate);
        }

        /** An ungated treatment drawn flush in the ceiling plane. */
        public SurfacePatternEntry(CeilingPattern pattern) {
            this(pattern, 0, 0, SizeGate.UNBOUNDED);
        }

        /** A treatment hanging at {@code projection}, ungated. The shape before {@code rise}. */
        public SurfacePatternEntry(CeilingPattern pattern, int projection, SizeGate gate) {
            this(pattern, projection, 0, gate);
        }

        /**
         * Where this layer sits relative to the ceiling plane, as ONE signed number: positive hangs
         * below it, negative rises above it, 0 is flush.
         *
         * <p>The two authored fields are separate because they mean opposite things to an author and
         * a sign is easy to mistype, but everything downstream of here wants one axis &mdash;
         * {@code CeilingSurface} already writes at {@code ceilingY - depth}, and a bracketed joists
         * layer is authored one row BELOW its beam, which is {@code depth + 1} whichever side of the
         * plane the beam is on.</p>
         */
        public int depth() {
            return rise > 0 ? -rise : projection;
        }

        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<SurfacePatternEntry> CODEC = Codecs.closed(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        // `type` + `config`, dispatched over the ceiling pattern registry. An
                        // unregistered id is a LOAD ERROR, not a skipped pattern.
                        CeilingPatternRegistry.MAP_CODEC.forGetter(SurfacePatternEntry::pattern),
                        Codecs.strictOptionalFieldOf(Codec.intRange(0, MAX_PROJECTION),
                                "projection", 0).forGetter(SurfacePatternEntry::projection),
                        // #68. Bounded by MAX_RISE here; bounded again, and for real, by the floor's
                        // own spare budget at render time -- see BasicCeilingGenerator.
                        Codecs.strictOptionalFieldOf(Codec.intRange(0, MAX_RISE),
                                "rise", 0).forGetter(SurfacePatternEntry::rise),
                        SizeGate.MAP_CODEC.forGetter(SurfacePatternEntry::gate)
                ).apply(instance, SurfacePatternEntry::new)));
    }

    /**
     * How deep a ceiling treatment may hang below the ceiling plane. Backlog #28b.
     *
     * <h2>Why this is not {@code WallPatternEntry.MAX_PROJECTION}</h2>
     * <p>It was, until 2026-08-31, and the sharing was an accident of one constant rather than a
     * decision. That constant is 2 and its javadoc justifies the number entirely in wall-trim
     * terms: "a projection eats room interior, and anything past one cell stops reading as trim and
     * starts reading as a ledge &mdash; which is a different feature with its own support and
     * headroom questions."</p>
     *
     * <p><strong>Not one clause of that applies to a ceiling.</strong> A ring hanging from the
     * ceiling eats headroom at the room's EDGE, where nobody walks, and a deep one reads as a
     * <em>dome</em> &mdash; which is the feature rather than a different one. So the cap was a wall
     * constraint a ceiling inherited by sharing a field.</p>
     *
     * <h2>Where 4 comes from</h2>
     * <p>Derived from the shipped clearance rather than picked. {@code vaulted_hall} gates itself at
     * {@code min_height} 7 &mdash; a 5-row interior &mdash; and its two-step vault leaves 3 rows of
     * clear perimeter headroom. The tallest room a vault scheme can be gated to is 9 (the
     * {@code max_long_side} 11 band; see {@code room_height_bands}), a 7-row interior, where a
     * <em>four</em>-step vault leaves those same 3 rows. So 4 is the deepest step count that never
     * asks for headroom the shipped scheme does not already spend.</p>
     *
     * <h2>Nothing checks it against the room, and that is unchanged</h2>
     * <p>This is a schema bound, not a fit check. A four-step vault authored on a scheme with no
     * {@code min_height} will draw in a 5-high room and come down to the floor at the perimeter.
     * That was equally true of the two-step one, and the author's tool is the same as it was: the
     * scheme's {@code min_height} gate. See {@code VaultedHallSchemeTest}, which walks the perimeter
     * of the shipped scheme rather than trusting the bound.</p>
     *
     * <h2>What is still out of scope</h2>
     * <p>A genuinely curved multi-radius vault. The profile needs distinct rows per radius, and the
     * eye reads a three-step corbel as curved anyway &mdash; steps are the right primitive here and
     * raising this number does not change that.</p>
     */
    public static final int MAX_PROJECTION = 4;

    /**
     * How far a ceiling treatment may rise ABOVE the ceiling plane, into the floor's own spare
     * budget. Backlog #68.
     *
     * <h2>The mirror of {@code projection}, and the opposite trade</h2>
     * <p>A projecting vault buys its shape out of the room's headroom: the perimeter drops and the
     * player's clearance drops with it, which is why {@code MAX_PROJECTION} is derived from what the
     * shipped schemes can afford to spend. A rising vault spends nothing the player can feel. It
     * reaches up into rock the floor already owns and did not excavate &mdash; a room is 5 to 10 high
     * inside a {@code ceiling_budget} of 15, so there are 5 to 10 blocks of stone sitting above every
     * procedural room's ceiling right now &mdash; and it gives the headroom back rather than taking
     * it. That is what makes a hall feel like a hall instead of a corridor with a pattern on the
     * lid.</p>
     *
     * <h2>Where 6 comes from</h2>
     * <p>The narrowest spare budget a shipped room can have. {@code DungeonStackPlanner} caps a
     * procedural room at 10 and the shipped {@code ceiling_budget} is 15, so the WORST case is 5 rows
     * of spare stone and the best is 10. Six is one step past the worst case on purpose: the clamp
     * is at render time and per room (see {@code BasicCeilingGenerator#withRiseBudget}), so an
     * authored 6 draws in full in the rooms that can afford it and clamps in the ones that cannot,
     * which is the behaviour a vault wants. A bound at 5 would instead deny the taller rooms a step
     * they have the budget for.</p>
     *
     * <p>Like {@link #MAX_PROJECTION} this is a SCHEMA bound and not a fit check &mdash; the fit
     * check is the render-time clamp, which is the only place the room's actual height is known.</p>
     */
    public static final int MAX_RISE = 6;

    /** A ring following the surface's edge: the first orientable type. */
    public static final String BORDER = "border";

    /** Parallel beams crossing the surface one way: the second, and the only one with a bracket. */
    public static final String JOISTS = "joists";
}
