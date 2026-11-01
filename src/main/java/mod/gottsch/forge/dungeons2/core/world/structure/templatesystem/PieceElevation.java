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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * How high up its own piece a block sits &mdash; the gate that lets weathering fall off with
 * height, so a ruin loses its top and keeps its footings.
 *
 * <h2>Why this is not another {@link PieceSurface} value</h2>
 * <p>It was proposed as one (Mark, 2026-09-18: "we have the [floor] and [any] tags &mdash; we'd just
 * have more tag rules"), and the shape is right but it cannot live there.
 * {@link PieceSurfaceMap#classify} returns exactly ONE primary surface per block, and rules test
 * against that one value, so a block already classified {@code WALL} could never also match an
 * {@code ELEVATION1}. The rule a ruin needs to state is "a WALL block NEAR THE TOP" &mdash; two
 * facts about one block &mdash; so elevation is a second, orthogonal gate on
 * {@link SurfaceAgingRule} instead. A rule may state either, both, or neither.</p>
 *
 * <h2>Bands are relative to the piece, not to the world</h2>
 * <p>{@link Bands} is built from the Y extent of the piece's OWN blocks, so the same rule reads the
 * same way on a two-storey entrance and on a squat one, and nothing here depends on sea level, on
 * the terrain the entrance landed on, or on {@code floorIndex}. Thirds, by fraction of the piece's
 * height: the arithmetic is deliberately blunt because the rates authored against it are what
 * actually shape the ruin.</p>
 *
 * <h2>A band gate forces whole-piece processing</h2>
 * <p>The extent cannot be known from one block, so {@link SurfaceAgingProcessor} treats a non-ANY
 * elevation the way it already treats {@code WALL}/{@code CEILING}/{@code JOIST}: the rule runs in
 * {@code finalizeProcessing}, over the whole block list. That list is the whole piece even when the
 * structure is generating one chunk at a time &mdash; vanilla's {@code processBlockInfos} hands
 * over the entire palette and clips only when it writes.</p>
 *
 * <p><strong>Ordering consequence, and it matters when authoring.</strong> Every
 * {@code processBlock} pass in a list runs before any {@code finalizeProcessing} pass, so a banded
 * rule sees what the {@code minecraft:rule} processors already produced, not the block the template
 * shipped. Key a banded rule on the DECAYED ids as well as the authored one &mdash; see the collapse
 * section of {@code classic_entrance_weathering.json}, which is keyed that way on purpose.</p>
 */
public enum PieceElevation implements StringRepresentable {
    /** Every block, whatever its height. The default, so existing rules keep their meaning. */
    ANY("any") {
        @Override
        public boolean matches(PieceElevation band) {
            return true;
        }
    },
    /** The bottom third: footings, doorsills, the courses a ruin keeps. */
    BASE("base"),
    /** The middle third. */
    MIDDLE("middle"),
    /** The top third: parapets, upper courses, the first thing to come down. */
    CROWN("crown");

    private final String name;

    PieceElevation(String name) {
        this.name = name;
    }

    public boolean matches(PieceElevation band) {
        return band == this;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static final Codec<PieceElevation> CODEC =
            StringRepresentable.fromEnum(PieceElevation::values);

    /**
     * The piece's own Y extent, and the classification of a position within it.
     *
     * <p>Built from EVERY cell the piece writes, air included, which is the template's authored
     * footprint. Skipping air was tried first and is a feedback bug: the bands run in the
     * whole-piece phase, so by then earlier passes have already punched holes in the building, and
     * measuring only what survived lets the extent shrink as the ruin deepens. The crown then
     * creeps down into what used to be the middle and the collapse eats itself, course by course.
     * {@code SurfaceAgingProcessorTest#holesDoNotShortenThePiece} pins it.</p>
     *
     * <p>Authored air counts as part of the building, then. A template with a tall empty column
     * above its roof inside its own bounding box will put its real top in the middle band rather
     * than the crown &mdash; that is an authoring consideration, and a predictable one, which a
     * decay-dependent extent would not be. {@code structure_void} is not in the list at all, so
     * cells the template declines to own never count.</p>
     */
    public record Bands(int minY, int maxY) {

        /** A piece that writes no cells at all; everything in it reads as {@link #BASE}. */
        public static final Bands EMPTY = new Bands(0, -1);

        public static Bands of(List<StructureTemplate.StructureBlockInfo> blocks) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (StructureTemplate.StructureBlockInfo info : blocks) {
                int y = info.pos().getY();
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
            return min > max ? EMPTY : new Bands(min, max);
        }

        public PieceElevation classify(BlockPos pos) {
            int span = maxY - minY;
            if (span <= 0) {
                return BASE;
            }
            // x3 rather than a double divide: thirds of the span, integer arithmetic, no rounding
            // argument to have later.
            int third = (pos.getY() - minY) * 3;
            if (third < span) {
                return BASE;
            }
            return third < span * 2 ? MIDDLE : CROWN;
        }
    }
}
