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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The corridor section of a {@link MotifConfig}: its own floor pair and ceiling, distinct from the
 * room's ({@link FloorConfig}/{@link CeilingConfig}) so corridors can read as rougher passages than
 * the rooms they join.
 *
 * <p>Corridors deliberately have no <em>floor or ceiling</em> pattern list &mdash; a border ring or
 * checkerboard needs a room-sized rectangle, and a corridor is a 1-3 cell wide run. The
 * {@code floor}/{@code alternateFloor} pair is rolled per cell at the same 45/55 split
 * {@code BasicFloorGenerator} uses for rooms. Corridor <em>walls</em> come from
 * {@link WallConfig}, shared with rooms, and can carry {@code courses} &mdash; horizontal bands are
 * the one wall treatment that needs no rectangle, since a band sits at a constant row and simply
 * runs along whatever the wall does.</p>
 *
 * <p>{@code height} is the corridor's wall height in blocks: the column runs
 * {@code floorY .. floorY + height - 1}, with the floor at the bottom, the ceiling at the top and
 * {@code height - 2} rows of air between. These fields are the motif's <strong>baseline</strong>;
 * a motif that authors {@code styles} rolls one of those per floor instead, and this becomes the
 * fallback. See {@link CorridorStyle}.</p>
 *
 * <p>{@code profile} shapes the top of that column. {@code flat} is a single ceiling row;
 * {@code arched} adds a haunch row of {@code archBlock} stairs immediately below it, angled into
 * the walls, so the ceiling springs from the wall rather than meeting it square. See
 * {@link Profile}.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record CorridorConfig(String floor, String alternateFloor, String ceiling, int height,
                             Profile profile, Optional<String> archBlock, Optional<Integer> narrowHeight,
                             List<CorridorStyle> styles, List<WallPatternEntry.CourseEntry> courses) {

    /** The pre-courses form. */
    public CorridorConfig(String floor, String alternateFloor, String ceiling, int height,
                          Profile profile, Optional<String> archBlock, Optional<Integer> narrowHeight,
                          List<CorridorStyle> styles) {
        this(floor, alternateFloor, ceiling, height, profile, archBlock, narrowHeight, styles, List.of());
    }

    /** The flat form: the fields that predate profiles, with no arch. */
    public CorridorConfig(String floor, String alternateFloor, String ceiling, int height) {
        this(floor, alternateFloor, ceiling, height, Profile.FLAT, Optional.empty(), Optional.empty());
    }

    /** The pre-narrowHeight form. */
    public CorridorConfig(String floor, String alternateFloor, String ceiling, int height,
                          Profile profile, Optional<String> archBlock) {
        this(floor, alternateFloor, ceiling, height, profile, archBlock, Optional.empty());
    }

    /** The pre-styles form: one motif-wide geometry, which is still the common case. */
    public CorridorConfig(String floor, String alternateFloor, String ceiling, int height,
                          Profile profile, Optional<String> archBlock, Optional<Integer> narrowHeight) {
        this(floor, alternateFloor, ceiling, height, profile, archBlock, narrowHeight, List.of());
    }

    /**
     * The shape of the corridor's ceiling.
     *
     * <p>{@code ARCHED} puts a stair at {@code floorY + height - 2} in every corridor cell that has
     * a wall on one side and open corridor on the other, turned so its mass sits against the wall
     * and its cut-away opens over the passage. Cells with walls on <em>both</em> sides get no
     * haunch at all &mdash; that is a 1-wide corridor, and arching it from both sides would brick
     * it up. The crown row above is unchanged, which is why an arch costs no extra height beyond
     * the row it borrows.</p>
     */
    public enum Profile implements StringRepresentable {
        FLAT("flat"),
        ARCHED("arched");

        private final String name;

        Profile(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Failing rather than lenient, same reasoning as {@code WallPatternEntry.CourseAnchor}. */
        public static final Codec<Profile> CODEC = StringRepresentable.fromEnum(Profile::values);
    }

    /**
     * The historical hardcoded corridor height (was {@code DungeonCorridorPiece.CORRIDOR_WALL_HEIGHT}),
     * so a motif that authors no {@code height} generates exactly what it did before.
     */
    public static final int DEFAULT_HEIGHT = 5;

    /**
     * 5 is the floor of the range because the door column is fixed: {@code BasicDoorGenerator} writes
     * sill / lower / upper / lintel at {@code floorY .. floorY+3}, so anything shorter would put the
     * corridor ceiling inside the doorway.
     *
     * <p>8 is the cap because a floor's slab is {@code DungeonStackPlanner.DEFAULT_FLOOR_HEIGHT} = 10
     * and the arched profile still has to fit its crown above the wall. An over-tall value is a
     * <em>load error</em> rather than a silent clamp &mdash; same rule as {@code maxHeight} on
     * schemes &mdash; which is why this uses {@link Codecs#strictOptionalFieldOf} and not DFU's
     * {@code optionalFieldOf}, which would swallow the range failure and hand back the default.</p>
     */
    public static final int MIN_HEIGHT = 5;
    public static final int MAX_HEIGHT = 8;

    /**
     * The shortest corridor an arch fits in. The haunch row is {@code height - 2}, and it has to
     * clear the door column's fixed {@code floorY .. floorY+3} &mdash; at height 5 the haunch would
     * land on the lintel row itself and stair-block the doorway.
     */
    public static final int MIN_ARCHED_HEIGHT = 6;

    /**
     * The ceiling height for a cell that is only one cell wide, which is 15% of corridor cells at
     * the shipped settings (measured across 40 MEDIUM dungeons at {@code corridorWidth} 3).
     *
     * <p><strong>Defaults to no drop</strong>, i.e. {@code height}. Dropping it is opt-in, and that
     * default was chosen the hard way: a narrow cell reads as a slot canyon at full height, so this
     * originally defaulted to {@code height - 1} &mdash; but corridor width fluctuates cell by cell
     * after dilation, so a per-cell drop made the ceiling staircase up and down along every run,
     * which looked considerably worse than the problem it solved. Authoring it is still useful for a
     * motif whose corridors are uniformly narrow; what does not work is applying it per cell to
     * corridors that pinch and widen. Doing it per <em>run</em> is the unbuilt version of this idea.</p>
     */
    public int narrowCellHeight() {
        return Math.max(MIN_HEIGHT, narrowHeight.orElse(height));
    }

    public static final CorridorConfig DEFAULT =
            new CorridorConfig("minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks",
                    DEFAULT_HEIGHT, Profile.FLAT, Optional.empty(), Optional.empty(), List.of(), List.of());

    /**
     * This motif's own geometry as a {@link CorridorStyle}, so the generator has exactly one shape to
     * read whether or not the motif authors {@code styles}. Named {@link CorridorStyle#BASELINE}
     * (the empty string), which the style codec forbids an authored style from taking.
     */
    public CorridorStyle baseline() {
        return new CorridorStyle(CorridorStyle.BASELINE, CorridorStyle.DEFAULT_WEIGHT,
                height, profile, archBlock, narrowHeight, courses);
    }

    /**
     * The style a corridor stamped with {@code name} should be built from.
     *
     * <p>Falls back to {@link #baseline()} for an unknown name rather than failing, and this one
     * <em>should</em> be lenient where the rest of this config is strict: the name arrives from a
     * saved piece, not from a datapack. A world generated before a motif's styles were renamed would
     * otherwise crash on chunk load, and the corridor's authoritative dimension &mdash; its wall
     * height &mdash; rides on the piece itself, so a fallback here costs the profile, not the shape
     * of the hole in the ground.</p>
     */
    public CorridorStyle styleFor(String name) {
        if (name == null || name.isEmpty()) {
            return baseline();
        }
        for (CorridorStyle style : styles) {
            if (style.name().equals(name)) {
                return style;
            }
        }
        return baseline();
    }

    /**
     * The styles to roll among for a floor: the authored list, or a single-entry list holding the
     * baseline when the motif authored none. Never empty, so the caller needs no special case.
     */
    public List<CorridorStyle> rollableStyles() {
        return styles.isEmpty() ? List.of(baseline()) : styles;
    }

    public static final Codec<CorridorConfig> CODEC = RecordCodecBuilder.<CorridorConfig>create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("floor").forGetter(CorridorConfig::floor),
                    Codec.STRING.fieldOf("alternateFloor").forGetter(CorridorConfig::alternateFloor),
                    Codec.STRING.fieldOf("ceiling").forGetter(CorridorConfig::ceiling),
                    Codecs.strictOptionalFieldOf(Codec.intRange(MIN_HEIGHT, MAX_HEIGHT), "height", DEFAULT_HEIGHT)
                            .forGetter(CorridorConfig::height),
                    Codecs.strictOptionalFieldOf(Profile.CODEC, "profile", Profile.FLAT)
                            .forGetter(CorridorConfig::profile),
                    Codecs.strictOptionalFieldOf(Codec.STRING, "archBlock")
                            .forGetter(CorridorConfig::archBlock),
                    Codecs.strictOptionalFieldOf(Codec.intRange(MIN_HEIGHT, MAX_HEIGHT), "narrowHeight")
                            .forGetter(CorridorConfig::narrowHeight),
                    Codecs.strictOptionalFieldOf(CorridorStyle.CODEC.listOf(), "styles", List.of())
                            .forGetter(CorridorConfig::styles),
                    Codecs.strictOptionalFieldOf(WallPatternEntry.CourseEntry.CODEC.listOf(), "courses",
                                    List.of())
                            .forGetter(CorridorConfig::courses)
            ).apply(instance, CorridorConfig::new)).flatXmap(CorridorConfig::validate, CorridorConfig::validate);

    /**
     * The cross-field rules, which no single field's codec can express.
     *
     * <p>All of them fail rather than degrade. An arch that quietly falls back to flat because the
     * motif was one block too short is a dungeon that generates fine and simply isn't what was
     * authored &mdash; indistinguishable, in game, from the feature not working. And an
     * {@code arched} profile with no {@code archBlock} must not invent stone brick stairs for a
     * deepslate motif: that is the silent-fallthrough the whole config was rebuilt to make
     * impossible, and it is the same rule that makes a {@code door} section missing its
     * {@code lintel} a load error.</p>
     *
     * <p>The three geometry rules live on {@link CorridorStyle#geometryError} because each authored
     * style has to answer to them too. Duplicate style names are rejected here for the same reason:
     * a corridor stores only its style's name, so two styles sharing one would make which geometry a
     * corridor gets depend on list order &mdash; and it would silently be whichever came first.</p>
     */
    private static DataResult<CorridorConfig> validate(CorridorConfig config) {
        String error = CorridorStyle.geometryError(
                config.height, config.profile, config.archBlock, config.narrowHeight, "corridor");
        if (error == null) {
            error = CorridorStyle.coursesError(config.courses, "corridor");
        }
        if (error != null) {
            final String message = error;
            return DataResult.error(() -> message);
        }
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (CorridorStyle style : config.styles) {
            if (!seen.add(style.name())) {
                duplicates.add(style.name());
            }
        }
        if (!duplicates.isEmpty()) {
            return DataResult.error(() -> "corridor: duplicate style name(s) " + duplicates
                    + " -- a corridor stores only its style's name, so a duplicate makes its geometry "
                    + "depend on the order of the styles list");
        }
        return DataResult.success(config);
    }

    public BlockState floorState() {
        return BlockStateCodec.block(floor, Blocks.STONE_BRICKS);
    }

    public BlockState alternateFloorState() {
        return BlockStateCodec.block(alternateFloor, Blocks.STONE_BRICKS);
    }

    public BlockState ceilingState() {
        return BlockStateCodec.block(ceiling, Blocks.STONE_BRICKS);
    }

    /**
     * The haunch stair's base state, or {@code null} when this corridor isn't arched. Falls back to
     * the ceiling block if the authored id doesn't resolve &mdash; a haunch has to be *something*
     * solid, and a hole in the ceiling is worse than a square one.
     */
    public BlockState archState() {
        return archStateFor(baseline());
    }

    /**
     * The same, for one rolled {@link CorridorStyle}. The arch block is per style; the ceiling block
     * it falls back to is not &mdash; a motif's corridors are made of one material whatever shape
     * they take, which is the same materials-vs-decoration split {@code MotifConfig} documents.
     */
    public BlockState archStateFor(CorridorStyle style) {
        if (style.profile() != Profile.ARCHED || style.archBlock().isEmpty()) {
            return null;
        }
        return BlockStateCodec.block(style.archBlock().get(), ceilingState().getBlock());
    }

    public boolean isArched() {
        return profile == Profile.ARCHED;
    }
}
