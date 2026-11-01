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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.WornPathFloorPatternProvider;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

/**
 * A worn track joining the room's doors: {@code path_block} down the lines people walk, fading out
 * at the edges of the band.
 *
 * <h2>The one pattern that is generated from the room's own plan</h2>
 * <p>Every other floor pattern is decoration an author places. This one is a CONSEQUENCE &mdash; it
 * is drawn from where the maze actually opened this room's doors, so no two rooms get the same one
 * and none of them had to be authored. That is the whole appeal, and it is only possible because a
 * floor generator is handed the {@code RoomData} rather than just its size (see the provider).</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code path_block} &mdash; required, the material the traffic exposes. Packed mud under mud
 *       brick, gravel or dirt under stone.</li>
 *   <li>{@code width} &mdash; the band's full width in cells, default
 *       {@value WornPathFloorPatternProvider#DEFAULT_WIDTH}. An even width is legal and lands
 *       half a cell off centre, the same discrete-grid rule the rest of this package follows.</li>
 *   <li>{@code centre_probability} / {@code edge_probability} &mdash; the chance of path ON the
 *       centre line and at the outer edge of {@code width}, defaults 1.0 and 0.35. Both name the
 *       same material, like the gradient patterns' pair. A {@code width} of 1 has no edge, so
 *       {@code edge_probability} does not apply to it.</li>
 *   <li>{@code routing} &mdash; see {@link PathRouting}. Default {@code auto}.</li>
 * </ul>
 *
 * <h2>Where to list it</h2>
 * <p>It is a SPARSE pattern and an overlay-capable one, so the useful form is
 * {@code composite: [gradient, path]} &mdash; the path wins the cells it crosses and the gradient
 * keeps the rest. Listed alone it draws over the motif's plain floor base.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public record WornPathFloorPattern(String pathBlock, int width, double centreProbability,
                                   double edgeProbability, PathRouting routing)
        implements FloorPattern {

    public static final String NAME = "path";

    /**
     * How the doors are joined up.
     *
     * <p>{@code pairs} is the truthful shape and {@code star} is the legible one, and which is which
     * depends entirely on the door count &mdash; hence {@code auto}, which switches at
     * {@value WornPathFloorPatternProvider#PAIRS_LIMIT} doors. Four doors is six pairs, and six lines
     * across one floor is not a path any more; four spokes through the middle is. Both are authorable
     * outright, because a room whose shape justifies one over the other is exactly the kind of thing
     * a scheme exists to say.</p>
     */
    public enum PathRouting implements StringRepresentable {
        /** Pairs up to {@value WornPathFloorPatternProvider#PAIRS_LIMIT} doors, a star above. */
        AUTO("auto"),
        /** Every door to every other door, however many that is. */
        PAIRS("pairs"),
        /** Every door to the middle of the room. */
        STAR("star");

        private final String name;

        PathRouting(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Failing, like every other enum in this schema: the set is closed and tiny. */
        public static final Codec<PathRouting> CODEC = StringRepresentable.fromEnum(PathRouting::values);
    }

    public static final MapCodec<WornPathFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("path_block").forGetter(WornPathFloorPattern::pathBlock),
                    // Bounded above because a band wider than the rooms it is drawn in is a repaint
                    // with extra steps -- the widest shipped room is 19 across including its walls.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 9), "width",
                                    WornPathFloorPatternProvider.DEFAULT_WIDTH)
                            .forGetter(WornPathFloorPattern::width),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D), "centre_probability",
                                    WornPathFloorPatternProvider.DEFAULT_CENTRE_PROBABILITY)
                            .forGetter(WornPathFloorPattern::centreProbability),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D), "edge_probability",
                                    WornPathFloorPatternProvider.DEFAULT_EDGE_PROBABILITY)
                            .forGetter(WornPathFloorPattern::edgeProbability),
                    Codecs.strictOptionalFieldOf(PathRouting.CODEC, "routing", PathRouting.AUTO)
                            .forGetter(WornPathFloorPattern::routing)
            ).apply(instance, WornPathFloorPattern::new)));

    /** See {@link FloorPattern#withRoles}. */
    @Override
    public FloorPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedPathBlock = Codecs.resolveRole(pathBlock, resolver);
        if (resolvedPathBlock.equals(pathBlock)) {
            return this;
        }
        return new WornPathFloorPattern(resolvedPathBlock, width, centreProbability, edgeProbability,
                routing);
    }

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block path = FloorPatterns.block(pathBlock);
        // The package rule: an unresolvable material degrades the whole entry to plain floor rather
        // than drawing the path in a guessed block.
        return path == null
                ? PlainFloorPattern.INSTANCE.generator(config)
                : new WornPathFloorPatternProvider(width, centreProbability, edgeProbability, routing,
                        path, config.baseState());
    }
}
