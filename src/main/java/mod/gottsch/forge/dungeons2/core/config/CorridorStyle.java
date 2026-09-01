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
import mod.gottsch.forge.dungeons2.core.config.CorridorConfig.Profile;

import java.util.List;
import java.util.Optional;

/**
 * One named corridor geometry, rolled <strong>once per floor</strong>: every corridor on a floor
 * shares a style, and the next floor down rolls again.
 *
 * <p>A style is the whole geometry set &mdash; height, profile, arch block, narrow-cell ceiling
 * &mdash; not height alone. Bundling them is the point: an 8-high arched passage and a 5-high flat
 * one are two different <em>kinds</em> of corridor, and varying height while leaving the profile
 * pinned motif-wide gives the tall floors a squat arch that was tuned for the short ones. It is also
 * what §5.4's courses will attach to, so a floor's courses can answer to its height rather than
 * being authored against a height the floor might not have.</p>
 *
 * <h2>Relationship to the baseline</h2>
 * <p>{@link CorridorConfig}'s own {@code height}/{@code profile}/{@code arch_block}/
 * {@code narrow_height} remain the motif's <em>baseline</em> style, used when a motif authors no
 * {@code styles} list at all (every existing motif) and as the fallback for a
 * {@code CorridorData.getStyleName()} that no longer resolves. A motif that does author
 * styles is choosing among them exclusively &mdash; the baseline does not join the roll, because a
 * silently-participating unnamed entry is exactly the kind of invisible extra that the config
 * rebuild set out to remove.</p>
 *
 * <h2>Why the name travels and the geometry does not</h2>
 * <p>Only {@code name} is carried on {@code CorridorData} (and so through piece NBT); the blocks are
 * re-resolved from the datapack at render time. Baking block ids into save data would freeze a
 * motif's materials at generation time, and the one field that genuinely cannot be re-resolved
 * &mdash; the wall height, which sizes the piece's bounding box at construction &mdash; is already
 * carried separately as {@code CorridorData.wallHeight} and stays authoritative over anything this
 * style says. See {@code BasicCorridorGenerator}.</p>
 *
 * @author Mark Gottschling on Aug 04, 2026
 */
public record CorridorStyle(String name, int weight, int height, Profile profile,
                            Optional<String> archBlock, Optional<Integer> narrowHeight,
                            List<WallPatternEntry.CourseEntry> courses) {

    /** The pre-courses form. */
    public CorridorStyle(String name, int weight, int height, Profile profile,
                         Optional<String> archBlock, Optional<Integer> narrowHeight) {
        this(name, weight, height, profile, archBlock, narrowHeight, List.of());
    }

    /** An unweighted style is as likely as any other single-weight style. */
    public static final int DEFAULT_WEIGHT = 1;

    /**
     * The name a corridor carries when its motif authored no styles. Never matches an authored
     * style: {@link #CODEC} rejects a blank name, so this cannot be shadowed.
     */
    public static final String BASELINE = "";

    /**
     * The ceiling height for a 1-cell-wide cell in this style. Same contract (and same
     * hard-won "defaults to no drop") as {@link CorridorConfig#narrowCellHeight()}.
     */
    public int narrowCellHeight() {
        return Math.max(CorridorConfig.MIN_HEIGHT, narrowHeight.orElse(height));
    }

    public boolean isArched() {
        return profile == Profile.ARCHED;
    }

    /**
     * This style's {@code courses} with their roles resolved. #65 phase 5.
     *
     * <p>A corridor holds a bare {@code List<CourseEntry>} rather than a whole
     * {@link WallPatternEntry}, which is the shape none of the earlier phases had to deal with: the
     * record being converted is nested one level shallower here than anywhere else. Missing it would
     * have left a role in a corridor course decoding cleanly and drawing nothing.</p>
     *
     * <p>{@code arch_block} came in phase 7 with the rest of the shell materials.</p>
     */
    public CorridorStyle withRoles(java.util.function.UnaryOperator<String> resolver) {
        List<WallPatternEntry.CourseEntry> resolved =
                WallPatternEntry.CourseEntry.withRoles(courses, resolver);
        Optional<String> resolvedArch = Codecs.resolveRole(archBlock, resolver);
        return resolved == courses && resolvedArch.equals(archBlock) ? this
                : new CorridorStyle(name, weight, height, profile, resolvedArch, narrowHeight,
                        resolved);
    }

    public static final Codec<CorridorStyle> CODEC = RecordCodecBuilder.<CorridorStyle>create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("name").forGetter(CorridorStyle::name),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", DEFAULT_WEIGHT)
                            .forGetter(CorridorStyle::weight),
                    Codecs.strictOptionalFieldOf(
                                    Codec.intRange(CorridorConfig.MIN_HEIGHT, CorridorConfig.MAX_HEIGHT),
                                    "height", CorridorConfig.DEFAULT_HEIGHT)
                            .forGetter(CorridorStyle::height),
                    Codecs.strictOptionalFieldOf(Profile.CODEC, "profile", Profile.FLAT)
                            .forGetter(CorridorStyle::profile),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "arch_block")
                            .forGetter(CorridorStyle::archBlock),
                    Codecs.strictOptionalFieldOf(
                                    Codec.intRange(CorridorConfig.MIN_HEIGHT, CorridorConfig.MAX_HEIGHT),
                                    "narrow_height")
                            .forGetter(CorridorStyle::narrowHeight),
                    Codecs.strictOptionalFieldOf(WallPatternEntry.CourseEntry.CODEC.listOf(), "courses",
                                    List.of())
                            .forGetter(CorridorStyle::courses)
            ).apply(instance, CorridorStyle::new)).flatXmap(CorridorStyle::validate, CorridorStyle::validate);

    private static DataResult<CorridorStyle> validate(CorridorStyle style) {
        if (style.name == null || style.name.isBlank()) {
            return DataResult.error(() ->
                    "corridor style: 'name' must not be blank -- it is what a generated corridor stores "
                            + "to find its geometry again");
        }
        String label = "corridor style '" + style.name + "'";
        String error = geometryError(style.height, style.profile, style.archBlock, style.narrowHeight, label);
        if (error == null) {
            error = coursesError(style.courses, label);
        }
        if (error == null) {
            return DataResult.success(style);
        }
        final String message = error;
        return DataResult.error(() -> message);
    }

    /**
     * The three parts of a room's {@code CourseEntry} that do not transfer to a corridor. Each is a
     * <strong>load error</strong> rather than a silent drop, because all three fail invisibly: the
     * course still draws, just not the way it was authored.
     *
     * <p>Returns {@code null} when the list is legal, or the message to fail loading with.</p>
     */
    static String coursesError(List<WallPatternEntry.CourseEntry> courses, String label) {
        for (WallPatternEntry.CourseEntry course : courses) {
            // A room is a rectangle with four runs and two of them own the corner columns
            // (CoursesWallPatternProvider.ownsCorners). A corridor's wall is an arbitrary polyline
            // -- it has no four corners to own, so there is nothing for this to mean.
            if (course.cornerBlock().isPresent()) {
                return label + ": a corridor course cannot take 'corner_block' -- corner ownership is a "
                        + "rule about a rectangle's four runs, and a corridor wall winds";
            }
            // Deferred rather than rejected on principle: a projecting course would occupy the
            // corridor cell in front of the wall, which is exactly where an arched profile puts its
            // haunch, and in a 1-wide corridor it is the passage itself.
            if (course.projection() != 0) {
                return label + ": a corridor course cannot project (got " + course.projection()
                        + ") -- the cell it would project into is the passage, and on an arched "
                        + "profile it is where the haunch goes";
            }
            // minSize/minHeight/maxSize/maxHeight gate on a ROOM's dimensions. A corridor has none.
            if (!course.gate().isUnbounded()) {
                return label + ": a corridor course cannot carry size gates -- they gate on a room's "
                        + "width/depth/height, and a corridor has no such dimensions";
            }
        }
        return null;
    }

    /**
     * The three cross-field geometry rules, shared verbatim with {@link CorridorConfig} so a style
     * cannot express a shape the baseline is forbidden from expressing. Returns {@code null} when
     * the geometry is legal, or the message to fail loading with.
     *
     * <p>All three fail rather than degrade, for the reasons {@code CorridorConfig.validate}
     * documents: a silently-flattened arch is indistinguishable in game from the feature not
     * working.</p>
     *
     * @param label how to name the offending section in the error, since this now serves both the
     *              {@code corridor} section itself and each entry of its {@code styles} list.
     */
    static String geometryError(int height, Profile profile, Optional<String> archBlock,
                                Optional<Integer> narrowHeight, String label) {
        if (profile == Profile.ARCHED && height < CorridorConfig.MIN_ARCHED_HEIGHT) {
            return label + ": profile 'arched' needs a height of at least "
                    + CorridorConfig.MIN_ARCHED_HEIGHT + " (got " + height + ") -- the haunch row would "
                    + "land on the doorway's lintel and block it";
        }
        if (profile == Profile.ARCHED && archBlock.isEmpty()) {
            return label + ": profile 'arched' requires an 'arch_block' (the stairs the haunch is built from)";
        }
        if (narrowHeight.isPresent() && narrowHeight.get() > height) {
            return label + ": narrow_height " + narrowHeight.get() + " is above height " + height
                    + " -- a 1-wide stretch cannot be taller than the corridor it is part of";
        }
        return null;
    }
}
