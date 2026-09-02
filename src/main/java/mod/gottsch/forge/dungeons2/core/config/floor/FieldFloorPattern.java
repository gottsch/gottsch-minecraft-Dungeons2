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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FieldFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import net.minecraft.world.level.block.Block;

/**
 * Every cell at or inside {@code inset}, in one material &mdash; the filled counterpart of
 * {@link BorderFloorPattern}'s ring. Backlog #75.
 *
 * <p>The ceiling has had this since #68, where a filled area is what a RISING vault is built from.
 * The floor never did, so nothing could fill the panel a {@code border} frames: the nearest were a
 * {@link CrossFloorPattern} (a plus, not a panel) and a hand-sized {@code centre}, neither of which
 * follows the room's size.</p>
 *
 * <p><strong>{@code inset} is measured the way {@code border}'s is</strong>, from the room edge, so
 * the two are directly comparable &mdash; and a field at inset {@code n} therefore covers the ring
 * at inset {@code n} as well as its interior. To fill the panel a border at inset 2 frames, author
 * the field at inset <strong>3</strong>, listed after the border in a {@code composite}. See
 * {@link FieldFloorPatternProvider}.</p>
 *
 * <p>One block and no {@code properties} map, unlike the ceiling's: a floor cell is looked at from
 * above and every orientation of a full block looks the same from there. A treatment that wants two
 * materials wants {@code checkerboard} or {@code speckle}, both of which already exist and both of
 * which compose over this.</p>
 */
public record FieldFloorPattern(String block, int inset) implements FloorPattern {

    public static final String NAME = "field";

    /** The whole floor in one material. Useful under a border, and as a composite's base layer. */
    public FieldFloorPattern(String block) {
        this(block, 0);
    }

    /** See {@link FloorPattern#withRoles}. */
    @Override
    public FloorPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        if (resolvedBlock.equals(block)) {
            return this;
        }
        return new FieldFloorPattern(resolvedBlock, inset);
    }

    public static final MapCodec<FieldFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(FieldFloorPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                                    FieldFloorPatternProvider.DEFAULT_INSET)
                            .forGetter(FieldFloorPattern::inset)
            ).apply(instance, FieldFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block field = FloorPatterns.block(block);
        return FloorPatterns.allResolve(field)
                ? new FieldFloorPatternProvider(inset, field, config.baseState())
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
