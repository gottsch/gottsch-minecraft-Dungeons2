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
import mod.gottsch.forge.dungeons2.core.config.pillar.GridPillarLayout;
import mod.gottsch.forge.dungeons2.core.config.pillar.PillarLayoutPattern;
import mod.gottsch.forge.dungeons2.core.config.pillar.PillarLayoutRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link RoomScheme}'s {@code pillars} slot: <strong>free-standing columns standing in the room's
 * interior</strong>, not attached to any wall.
 *
 * <h2>Why this is a new element slot and not another wall pattern</h2>
 * <p>Every other pattern in this package draws on a <em>surface</em> &mdash; the wall ring, the
 * ceiling plane, the floor plane &mdash; and composes with its neighbours because a surface pattern
 * is sparse. A free-standing pillar is not on any of those planes: it occupies interior cells,
 * through the room's whole height, in the volume {@code RoomVolumeGenerator} clears. No ordered list
 * of existing sparse patterns can draw one, which is the test this package applies before adding
 * geometry &mdash; and the first time since floors that it has come back "no".</p>
 *
 * <p>This is Step 5 of the wall/ceiling pattern plan, and the reason that plan made
 * {@code RoomVolumeGenerator} own the interior air fill back in Step 0: hollowing first is what lets
 * an interior feature simply run afterwards and win the cells it needs.</p>
 *
 * <h2>Why an ordered list from day one</h2>
 * <p>The {@code wall} slot shipped as a single typed entry and had to be migrated to a list the
 * moment a second type arrived &mdash; 17 slots across 6 files, plus a required {@code patterns} key
 * to stop unmigrated packs degrading silently. The bet paid off immediately: {@code colonnade} and
 * {@code quartet} both landed the same day and cost no migration at all. A list also lets a great
 * hall carry a colonnade <em>and</em> a central quartet.</p>
 *
 * <p>{@code patterns} is <strong>required</strong>, for the same load-bearing reason it is on
 * {@link WallPatternEntry}: an optional key would let a slot with no patterns decode cleanly, and a
 * room whose authored columns silently vanished is indistinguishable in game from one that never had
 * any.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record PillarPatternEntry(List<PillarEntry> patterns, SizeGate gate) {

    /** An ungated treatment -- drawn whenever its scheme is rolled. */
    public PillarPatternEntry(List<PillarEntry> patterns) {
        this(patterns, SizeGate.UNBOUNDED);
    }

    /** The even-grid layout: columns on a regular lattice across the interior. */
    public static final String GRID = "grid";

    /**
     * Two rows running the length of the room with a clear aisle between them. The one layout with
     * an <em>axis</em>; see {@code ColonnadePillarPatternProvider}.
     */
    public static final String COLONNADE = "colonnade";

    /**
     * Four columns marking a square at the room's centre. Structurally a grid capped at two columns
     * per axis; what keeps it distinct is that its square does not grow with the room. See
     * {@code QuartetPillarPatternProvider}.
     */
    public static final String QUARTET = "quartet";

    /**
     * Exactly one column, at the room's centre, however large the room is. The only layout whose
     * COUNT is the guarantee rather than its arrangement. See {@code CentrePillarLayout}.
     */
    public static final String CENTRE = "centre";

    /**
     * One layout. {@code type} is a plain string discriminator, the same idiom the floor, wall and
     * ceiling slots use; an unrecognized value draws nothing, the same graceful degradation they
     * give.
     *
     * <ul>
     *   <li>{@code "grid"} &mdash; columns on an even lattice, {@code spacing} apart, centred on the
     *       interior and kept {@code inset} cells clear of the wall.</li>
     *   <li>{@code "colonnade"} &mdash; two rows along the room's longer axis, {@code spacing} apart
     *       and {@code inset} from the walls they run beside, leaving a clear aisle between them.
     *       Draws nothing in a room too narrow to have an aisle at all.</li>
     *   <li>{@code "quartet"} &mdash; four columns marking a square of side {@code spacing} at the
     *       room's centre, shrunk to fit if the room cannot carry it at {@code inset}. Unlike the
     *       grid the square does not grow with the room.</li>
     *   <li>{@code "centre"} (or {@code "center"}) &mdash; exactly one column at the room's centre.
     *       Takes {@code inset} only, as a veto: too small a room draws nothing. The grid and the
     *       quartet both COLLAPSE to a single column in a small enough room, which is why this
     *       exists &mdash; there a lone pier is incidental, here it is the contract.</li>
     * </ul>
     *
     * <p>{@code spacing} and {@code inset} carry the same meaning on all of them: {@code inset} is
     * "how far in from the edge" and {@code spacing} is "how far apart the columns are". The grid
     * applies both to two axes; the colonnade applies {@code spacing} along its length only, since
     * its width is two rows by definition; the quartet uses {@code spacing} as the side of its
     * square and never repeats. <strong>{@code centre} is the one that declines {@code spacing}
     * outright</strong> &mdash; a lone column has nothing to be spaced from, so the field would be
     * a silent no-op and the closed schema rejects it instead.</p>
     *
     * <p><strong>{@code spacing} is also the knob that keeps the layouts apart.</strong> A quartet
     * authored at the grid's own spacing lands on the grid's own footprint in any room small enough
     * for the grid to produce two columns per axis; authored wider, it does not. That is a better
     * lever than a size gate, which removes the overlap only by removing most of the rooms.</p>
     *
     * <h2>The blocks</h2>
     * <p>{@code block} is the shaft and is <strong>required</strong> &mdash; there is no default
     * material for a column, the same rule every other pattern type follows. {@code base_block} and
     * {@code cap_block} default to {@code block}, and {@code base_properties}/{@code cap_properties}
     * default to {@code properties}.</p>
     *
     * <h2>{@code thickness} &mdash; how many cells across the shaft is</h2>
     * <p>A {@code thickness} of 2 draws a 2x2 pier, 3 a 3x3, and the default 1 is the single-cell
     * column every layout drew before this existed. It lives HERE rather than on a layout because
     * it is <strong>orthogonal to arrangement</strong>: a thick column makes as much sense in a
     * {@code grid} or a {@code colonnade} as it does at the {@code centre}, and putting it on the
     * layout would mean implementing it four times and watching the four drift. It sits beside
     * {@code base_block} and the property maps for the same reason those do &mdash; per-column
     * detail that every layout inherits for free.</p>
     *
     * <p><strong>An even thickness cannot be centred in an odd room</strong>, and rooms are
     * odd-sided by convention. A 2-wide pier in a 15-wide interior takes cells 7-8, half a cell off
     * the room's true centre; the shaft leans toward +x/+z, since odd thicknesses centre exactly on
     * the layout's cell and even ones have to lean somewhere. Invisible in most rooms, and NOT
     * invisible against a {@code cross} or {@code spokes} floor pattern, which are centred.</p>
     *
     * <p>A thick column is placed <strong>whole or not at all</strong>. If any of its cells falls
     * outside the interior, on a doorway approach, or in a pit, the entire column is skipped &mdash;
     * the rule a single-cell column already follows at a doorway, for the reason stated on
     * {@code BasicPillarGenerator}: a missing column reads as a room, a truncated one reads as a
     * bug. So a thickness the room cannot carry costs the column, never a half pier against a
     * wall.</p>
     *
     * <p>The per-row property maps are here for the same reason they are on a wall strip and
     * <em>not</em> on a course: a column's plinth and capital are typically the same block at
     * <strong>opposite</strong> values of a vertical property, so one shared map cannot describe a
     * column at all. For {@code dungeonblocks}' pillar blocks that means {@code base=up} on the row
     * standing on the floor and {@code base=down} at the capital &mdash; which reads backwards and
     * was authored inverted the first time, so it is pinned by a test rather than left to memory.</p>
     */
    public record PillarEntry(PillarLayoutPattern layout, String block,
                              Optional<String> baseBlock, Optional<String> capBlock,
                              Map<String, String> properties,
                              Optional<Map<String, String>> baseProperties,
                              Optional<Map<String, String>> capProperties,
                              int thickness,
                              SizeGate gate) {

        /** A single-cell shaft: what every column was before {@code thickness} existed. */
        public static final int DEFAULT_THICKNESS = 1;

        /** The shape before {@code thickness}: a single-cell shaft, however it is otherwise built. */
        public PillarEntry(PillarLayoutPattern layout, String block,
                           Optional<String> baseBlock, Optional<String> capBlock,
                           Map<String, String> properties,
                           Optional<Map<String, String>> baseProperties,
                           Optional<Map<String, String>> capProperties,
                           SizeGate gate) {
            this(layout, block, baseBlock, capBlock, properties, baseProperties, capProperties,
                    DEFAULT_THICKNESS, gate);
        }

        /** A plain ungated column of one block, at the default rhythm. */
        public PillarEntry(PillarLayoutPattern layout, String block) {
            this(layout, block, Optional.empty(), Optional.empty(),
                    Map.of(), Optional.empty(), Optional.empty(), DEFAULT_THICKNESS,
                    SizeGate.UNBOUNDED);
        }

        /** The base block, falling back to {@link #block} when unauthored. */
        public String baseBlockOrBase() {
            return baseBlock.orElse(block);
        }

        /** The cap block, falling back to {@link #block} when unauthored. */
        public String capBlockOrBase() {
            return capBlock.orElse(block);
        }

        /** The base row's properties, falling back to {@link #properties} when unauthored. */
        public Map<String, String> basePropertiesOrBase() {
            return baseProperties.orElse(properties);
        }

        /** See {@link #basePropertiesOrBase}. */
        public Map<String, String> capPropertiesOrBase() {
            return capProperties.orElse(properties);
        }

        /**
         * Whether this entry is the grid layout. Nothing in the mod calls this -- it was dead when
         * the layouts moved to a registry -- but it is now a type test rather than a string
         * comparison, which is the shape a caller would want if one ever appears.
         */
        public boolean isGrid() {
            return layout instanceof GridPillarLayout;
        }

        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<PillarEntry> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                // `type` + `config`, dispatched over the layout registry -- see
                // PillarLayoutRegistry. An unregistered id is a LOAD ERROR, not a skipped pattern.
                PillarLayoutRegistry.MAP_CODEC.forGetter(PillarEntry::layout),
                // Required, not an Optional that validate() rejects later: unlike a wall pattern
                // there is no type here that draws from anything other than a single block, so the
                // codec can say so directly.
                Codecs.BLOCK_ID.fieldOf("block").forGetter(PillarEntry::block),
                Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID, "base_block").forGetter(PillarEntry::baseBlock),
                Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID, "cap_block").forGetter(PillarEntry::capBlock),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "properties", Map.of()).forGetter(PillarEntry::properties),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "base_properties").forGetter(PillarEntry::baseProperties),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "cap_properties").forGetter(PillarEntry::capProperties),
                // Named `thickness` and NOT `size`, deliberately: this entry already carries a
                // SizeGate whose minSize/maxSize are about the ROOM. A bare `size` beside them
                // would read as a third opinion on the same quantity.
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "thickness",
                        DEFAULT_THICKNESS).forGetter(PillarEntry::thickness),
                SizeGate.MAP_CODEC.forGetter(PillarEntry::gate)
        ).apply(instance, PillarEntry::new)));
    }

    // Codecs.closed -- see RoomScheme.CODEC.
        /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<PillarPatternEntry> MAP_CODEC =
            RecordCodecBuilder.<PillarPatternEntry>mapCodec(instance -> instance.group(
                    PillarEntry.CODEC.listOf().fieldOf("patterns")
                            .forGetter(PillarPatternEntry::patterns),
                    SizeGate.MAP_CODEC.forGetter(PillarPatternEntry::gate)
            ).apply(instance, PillarPatternEntry::new)).flatXmap(PillarPatternEntry::validate, PillarPatternEntry::validate);

    public static final Codec<PillarPatternEntry> CODEC = Codecs.closed(MAP_CODEC);

    /**
     * Rejects an inverted per-entry gate, the same check the wall and ceiling slots make. A gate that
     * fits no room makes its layout draw nothing, anywhere, which at generation time is
     * indistinguishable from content that merely never came up.
     */
    private static DataResult<PillarPatternEntry> validate(PillarPatternEntry entry) {
        for (PillarEntry pattern : entry.patterns()) {
            DataResult<SizeGate> gate = pattern.gate()
                    // The layout's class name, since `type` is no longer a field on the entry --
                    // the registry id would be better still, but a reverse lookup here would drag
                    // the registry into a validate() that runs during its own class init.
                    .validate("pillar pattern '"
                            + pattern.layout().getClass().getSimpleName() + "'");
            if (gate.error().isPresent()) {
                return DataResult.error(() -> gate.error().orElseThrow().message());
            }
        }
        return DataResult.success(entry);
    }

    /**
     * This treatment with only the layouts a room of these dimensions actually draws. Same shape and
     * same rule as {@code WallPatternEntry#forRoom} and {@code CeilingPatternEntry#forRoom}:
     * returns {@code this} when nothing gated out.
     */
    public PillarPatternEntry forRoom(int width, int depth, int height) {
        List<PillarEntry> fitting = new ArrayList<>(patterns.size());
        for (PillarEntry pattern : patterns) {
            if (pattern.gate().fits(width, depth, height)) {
                fitting.add(pattern);
            }
        }
        return fitting.size() == patterns.size() ? this : new PillarPatternEntry(fitting, gate);
    }
}
