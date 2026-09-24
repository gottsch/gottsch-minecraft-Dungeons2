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
package mod.gottsch.forge.dungeons2.core.config.pit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.GratePitShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;
import net.minecraft.util.StringRepresentable;

import java.util.Map;

/**
 * A grated sump: a square of grate set flush in the floor over a sheer cavity, dry or flooded. The
 * oculus turned upside down &mdash; where the oculus hides a lamp behind a grille overhead, this
 * shows what is under the floor through one underfoot.
 *
 * <h2>Why a pit shape and not a floor pattern</h2>
 * <p>A floor pattern writes one plane at the walking row and cannot dig. A grate over nothing is a
 * floor tile; the cavity is the feature, and the pit slot is the one slot allowed below the walking
 * plane &mdash; with the {@code sinkOffset} clamp and the lining that keep it from opening into the
 * terrain or the room below. The cost is the pit slot's rule: <strong>it cannot share a scheme with
 * a platform</strong>, both want the centre.</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code grate_block} &mdash; required, laid at the walking plane over every dug cell. It must
 *       be something you can stand on and see through; {@code dungeonblocks:dark_iron_grate} is the
 *       intended one.</li>
 *   <li>{@code grate_properties} &mdash; applied to the grate, except {@code waterlogged}, which the
 *       liquid decides (below).</li>
 *   <li>{@code size} &mdash; the grate's side in cells, centred; 2 and 3 are the intended reads.
 *       Shrinks to keep a walkable ring round it, and yields nothing in a room too small for one.</li>
 *   <li>{@code depth} &mdash; how far the cavity drops, from {@value #MIN_DEPTH}: at 1 the grate would
 *       sit directly on the pit floor with nothing under it. Clamped to {@code sinkOffset} like every
 *       pit.</li>
 *   <li>{@code liquid} &mdash; {@code none}, {@code water} or {@code lava}, filling the cavity.</li>
 *   <li>{@code offset_x}/{@code offset_z} &mdash; shift off centre, kept inside the walkable ring,
 *       as {@code hazard}'s are.</li>
 * </ul>
 *
 * <h2>Water logs the grate; lava cannot</h2>
 * <p>With water the grate itself is waterlogged, so the surface stands level with the floor and the
 * sump reads as brimming. Minecraft has no lava-logging, so with lava the surface sits one row down
 * and glows up through the bars. With none the grate is forced dry: dungeonblocks' default states
 * are waterlogged, and an unset flag would flood a dry sump's lid.</p>
 *
 * <p><strong>Lava and fire.</strong> Lava's random tick lights fire only in AIR above it, and its
 * search stops at the first block that blocks motion. The heavy grate has a full collision box, so
 * the lava is sealed from the room. A grate without collision (a trapdoor left open, a carpet) would
 * not seal it, and in a room dressed in wood that is a fire.</p>
 *
 * @author Mark Gottschling on Sep 22, 2026
 */
public record GratePitShape(String grateBlock, Map<String, String> grateProperties, int size,
                            int depth, Liquid liquid, int offsetX, int offsetZ)
        implements PitShapePattern {

    public static final String NAME = "grate";

    public static final int DEFAULT_SIZE = 3;

    /** Two: the grate, and one row of cavity under it. */
    public static final int MIN_DEPTH = 2;

    public static final int DEFAULT_DEPTH = 2;

    public enum Liquid implements StringRepresentable {
        NONE("none"),
        WATER("water"),
        LAVA("lava");

        private final String name;

        Liquid(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Failing, like every other enum in this schema: the set is closed and tiny. */
        public static final Codec<Liquid> CODEC = StringRepresentable.fromEnum(Liquid::values);
    }

    /** A dry 3x3 grate over a two-deep cavity. */
    public GratePitShape(String grateBlock) {
        this(grateBlock, Map.of(), DEFAULT_SIZE, DEFAULT_DEPTH, Liquid.NONE, 0, 0);
    }

    public static final MapCodec<GratePitShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("grate_block")
                            .forGetter(GratePitShape::grateBlock),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "grate_properties", Map.of()).forGetter(GratePitShape::grateProperties),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "size",
                            DEFAULT_SIZE).forGetter(GratePitShape::size),
                    Codecs.strictOptionalFieldOf(Codec.intRange(MIN_DEPTH, 24), "depth",
                            DEFAULT_DEPTH).forGetter(GratePitShape::depth),
                    Codecs.strictOptionalFieldOf(Liquid.CODEC, "liquid", Liquid.NONE)
                            .forGetter(GratePitShape::liquid),
                    Codecs.strictOptionalFieldOf(Codec.intRange(-16, 16), "offset_x", 0)
                            .forGetter(GratePitShape::offsetX),
                    Codecs.strictOptionalFieldOf(Codec.intRange(-16, 16), "offset_z", 0)
                            .forGetter(GratePitShape::offsetZ)
            ).apply(instance, GratePitShape::new)));

    /** See {@link PitShapePattern#withRoles}. */
    @Override
    public PitShapePattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolved = Codecs.resolveRole(grateBlock, resolver);
        if (resolved.equals(grateBlock)) {
            return this;
        }
        return new GratePitShape(resolved, grateProperties, size, depth, liquid, offsetX, offsetZ);
    }

    @Override
    public MapCodec<? extends PitShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPitShapeProvider provider() {
        return new GratePitShapeProvider(grateBlock, grateProperties, size, depth, liquid,
                offsetX, offsetZ);
    }
}
