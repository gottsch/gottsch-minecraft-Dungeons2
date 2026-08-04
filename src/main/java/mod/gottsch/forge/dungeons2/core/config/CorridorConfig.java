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

import java.util.Optional;

/**
 * The corridor section of a {@link MotifConfig}: its own floor pair and ceiling, distinct from the
 * room's ({@link FloorConfig}/{@link CeilingConfig}) so corridors can read as rougher passages than
 * the rooms they join.
 *
 * <p>Corridors deliberately have no decorative pattern list &mdash; a border ring or checkerboard
 * needs a room-sized rectangle, and a corridor is a 1-3 cell wide run. The
 * {@code floor}/{@code alternateFloor} pair is rolled per cell at the same 45/55 split
 * {@code BasicFloorGenerator} uses for rooms. Corridor <em>walls</em> come from
 * {@link WallConfig}, shared with rooms.</p>
 *
 * <p>{@code height} is the corridor's wall height in blocks: the column runs
 * {@code floorY .. floorY + height - 1}, with the floor at the bottom, the ceiling at the top and
 * {@code height - 2} rows of air between. It is motif-wide &mdash; per-floor variation is a later
 * step and wants a weighted roll, not a second scalar here.</p>
 *
 * <p>{@code profile} shapes the top of that column. {@code flat} is a single ceiling row;
 * {@code arched} adds a haunch row of {@code archBlock} stairs immediately below it, angled into
 * the walls, so the ceiling springs from the wall rather than meeting it square. See
 * {@link Profile}.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record CorridorConfig(String floor, String alternateFloor, String ceiling, int height,
                             Profile profile, Optional<String> archBlock, Optional<Integer> narrowHeight) {

    /** The flat form: the fields that predate profiles, with no arch. */
    public CorridorConfig(String floor, String alternateFloor, String ceiling, int height) {
        this(floor, alternateFloor, ceiling, height, Profile.FLAT, Optional.empty(), Optional.empty());
    }

    /** The pre-narrowHeight form. */
    public CorridorConfig(String floor, String alternateFloor, String ceiling, int height,
                          Profile profile, Optional<String> archBlock) {
        this(floor, alternateFloor, ceiling, height, profile, archBlock, Optional.empty());
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
                    DEFAULT_HEIGHT, Profile.FLAT, Optional.empty(), Optional.empty());

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
                            .forGetter(CorridorConfig::narrowHeight)
            ).apply(instance, CorridorConfig::new)).flatXmap(CorridorConfig::validate, CorridorConfig::validate);

    /**
     * The two cross-field rules, which no single field's codec can express.
     *
     * <p>Both fail rather than degrade. An arch that quietly falls back to flat because the motif
     * was one block too short is a dungeon that generates fine and simply isn't what was authored
     * &mdash; indistinguishable, in game, from the feature not working. And an {@code arched}
     * profile with no {@code archBlock} must not invent stone brick stairs for a deepslate motif:
     * that is the silent-fallthrough the whole config was rebuilt to make impossible, and it is the
     * same rule that makes a {@code door} section missing its {@code lintel} a load error.</p>
     */
    private static DataResult<CorridorConfig> validate(CorridorConfig config) {
        if (config.profile == Profile.ARCHED && config.height < MIN_ARCHED_HEIGHT) {
            return DataResult.error(() -> "corridor: profile 'arched' needs a height of at least "
                    + MIN_ARCHED_HEIGHT + " (got " + config.height + ") -- the haunch row would land on the "
                    + "doorway's lintel and block it");
        }
        if (config.profile == Profile.ARCHED && config.archBlock.isEmpty()) {
            return DataResult.error(() ->
                    "corridor: profile 'arched' requires an 'archBlock' (the stairs the haunch is built from)");
        }
        if (config.narrowHeight.isPresent() && config.narrowHeight.get() > config.height) {
            return DataResult.error(() -> "corridor: narrowHeight " + config.narrowHeight.get()
                    + " is above height " + config.height + " -- a 1-wide stretch cannot be taller than the "
                    + "corridor it is part of");
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
        if (profile != Profile.ARCHED || archBlock.isEmpty()) {
            return null;
        }
        return BlockStateCodec.block(archBlock.get(), ceilingState().getBlock());
    }

    public boolean isArched() {
        return profile == Profile.ARCHED;
    }
}
