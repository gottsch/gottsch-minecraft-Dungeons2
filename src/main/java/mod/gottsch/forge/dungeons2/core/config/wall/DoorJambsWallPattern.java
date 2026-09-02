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
package mod.gottsch.forge.dungeons2.core.config.wall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.DoorJambsWallPatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;

/**
 * A jamb up each side of every doorway on the wall, with an optional lintel across the top (#72).
 *
 * <h2>The first pattern the MAZE positions</h2>
 * <p>Every other wall pattern is drawn where the author says: a course at a row, pilasters at a
 * spacing, a diamond on a grid. This one is drawn wherever the maze happened to open a door, which
 * is different in every room and cannot be authored at all. One line in a scheme frames every
 * opening in every room the scheme dresses.</p>
 *
 * <p>It is also the reason [#72] exists: a wall pattern is handed a size, a facing and a random and
 * nothing else, so until the run could tell a pattern which of its columns are an opening, this was
 * unwritable. See {@code IDoorAwarePatternProvider}.</p>
 *
 * <h2>The fields</h2>
 * <ul>
 *   <li>{@code block} &mdash; required, the jamb itself, drawn full height beside the opening.</li>
 *   <li>{@code base_block} / {@code cap_block} &mdash; optional distinct blocks on the jamb's lowest
 *       and highest rows, exactly as {@code pilasters} uses them. Absent, the jamb is one material
 *       top to bottom.</li>
 *   <li>{@code lintel_block} &mdash; optional, drawn across the opening on the row above the door
 *       itself. Absent, that row stays whatever the wall put there.</li>
 * </ul>
 *
 * <h2>Where to list it</h2>
 * <p>Sparse, like every wall pattern but {@code gradient}, so it composes: list it AFTER the band's
 * courses and it frames the doorway over them. It is a natural companion to a {@code plain} scheme,
 * where it is the only thing distinguishing one wall from another.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public record DoorJambsWallPattern(String block, Optional<String> baseBlock,
                                   Optional<String> capBlock, Optional<String> lintelBlock,
                                   Map<String, String> properties) implements WallPattern {

    public static final String NAME = "door_jambs";

    /** A plain jamb of one material, no lintel. */
    public DoorJambsWallPattern(String block) {
        this(block, Optional.empty(), Optional.empty(), Optional.empty(), Map.of());
    }

    public static final MapCodec<DoorJambsWallPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(DoorJambsWallPattern::block),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "base_block")
                            .forGetter(DoorJambsWallPattern::baseBlock),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "cap_block")
                            .forGetter(DoorJambsWallPattern::capBlock),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "lintel_block")
                            .forGetter(DoorJambsWallPattern::lintelBlock),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(DoorJambsWallPattern::properties)
            ).apply(instance, DoorJambsWallPattern::new)));

    /** See {@link WallPattern#withRoles}. */
    @Override
    public WallPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        Optional<String> resolvedBase = Codecs.resolveRole(baseBlock, resolver);
        Optional<String> resolvedCap = Codecs.resolveRole(capBlock, resolver);
        Optional<String> resolvedLintel = Codecs.resolveRole(lintelBlock, resolver);
        if (resolvedBlock.equals(block) && resolvedBase.equals(baseBlock)
                && resolvedCap.equals(capBlock) && resolvedLintel.equals(lintelBlock)) {
            return this;
        }
        return new DoorJambsWallPattern(resolvedBlock, resolvedBase, resolvedCap, resolvedLintel,
                properties);
    }

    @Override
    public MapCodec<? extends WallPattern> codec() {
        return CODEC;
    }

    @Override
    public ISurfacePatternProvider provider() {
        BlockState jamb = WallPattern.state(block, properties);
        if (jamb == null) {
            return null;
        }
        // The three optional slots fall back to the jamb rather than dropping the pattern: a typo in
        // the cap should not delete the frame it was decorating, which is the same call
        // BorderCeilingPattern makes for its corner block. The lintel is the exception -- absent or
        // unresolvable, the wall's own block stays over the door, which is what a plain doorway is.
        return new DoorJambsWallPatternProvider(jamb,
                baseBlock.map(id -> WallPattern.state(id, properties)).orElse(jamb),
                capBlock.map(id -> WallPattern.state(id, properties)).orElse(jamb),
                lintelBlock.map(id -> WallPattern.state(id, properties)).orElse(null));
    }
}
