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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The room floor <em>materials</em> of a {@link MotifConfig}: the plain {@code base}/{@code
 * alternateBase} pair that {@code BasicFloorGenerator} rolls per cell at 45/55, and that every
 * decorative floor pattern draws its unmarked cells from.
 *
 * <p>Decoration is <em>not</em> here. This record held a weighted {@code patterns} list until the
 * scheme migration moved that roll up to {@link MotifConfig#schemes}, so that a room's floor,
 * walls and ceiling are chosen together rather than independently &mdash; see {@link RoomScheme}.
 * The split this leaves is a clean one and worth keeping to: the element sections of a motif config
 * say what the motif is <em>made of</em>, and the scheme list says how a room is <em>dressed</em>.
 * </p>
 *
 * <p>Setting {@code base} and {@code alternateBase} to the <em>same</em> block makes the floor
 * uniform before weathering, which is how {@code classic} ships: the weathering processor list
 * already produces graduated stone_bricks &rarr; cracked/mossy &rarr; cobblestone &rarr; dirt
 * &rarr; gravel variation, and pre-baking a second block here both duplicated that and skipped the
 * deeper decay stages (the aging chains are keyed on the source block).</p>
 *
 * <h2>{@code pattern} &mdash; the one piece of dressing that does live here</h2>
 * <p>The split above says decoration belongs to the scheme, and it still does. {@code pattern} is
 * the exception, and it earns it: a <strong>stratum</strong> ({@code strataByFloorIndex}) is a
 * {@link FloorConfig}, not a scheme, so without this field a depth band can say "my floors are
 * these two blocks" but never "my floors are speckled cobble". That is the whole of what the mud
 * band needs &mdash; cobblestone paving with mud showing through &mdash; and it is a property of
 * the <em>depth</em>, not of any one room's dressing.</p>
 *
 * <p><strong>Precedence: a scheme's own {@code floor} slot wins.</strong> A room that asked for a
 * mosaic asked for it at every depth; this is the default underneath, not an override on top. See
 * {@code FloorPatternSelector#generatorFor}, which is the single place the two are resolved.</p>
 *
 * <p>The field holds a {@link FloorPatternEntry} &mdash; the same record a scheme's floor slot
 * holds &mdash; deliberately. When pattern providers move to a registry and that record is
 * restructured around a {@code Codec.dispatch}, both call sites migrate together and there is no
 * second format change for anything authored against this field in the meantime. {@code weight}
 * and {@code gate} are meaningless here (there is nothing to weigh it against, and a band is not
 * size-gated); they are harmless, and factoring them out for this one case would cost more than it
 * saves &mdash; the same call already made for a nested {@code generators} entry.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record FloorConfig(String base, String alternateBase, Optional<FloorPatternEntry> pattern) {

    /** An undressed floor &mdash; the shape every motif had before {@code pattern} existed. */
    public FloorConfig(String base, String alternateBase) {
        this(base, alternateBase, Optional.empty());
    }

    /** Plain stone_bricks &mdash; the always-plain fallback. */
    public static final FloorConfig DEFAULT = new FloorConfig(
            "minecraft:stone_bricks", "minecraft:stone_bricks");

    public static final Codec<FloorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("base").forGetter(FloorConfig::base),
            Codec.STRING.fieldOf("alternateBase").forGetter(FloorConfig::alternateBase),
            // strictOptionalFieldOf, not DFU's own: a malformed pattern must be a load error, not
            // silently the same as an absent one. See Codecs and #31 -- a band that quietly lost
            // its paving would look exactly like a band that never asked for any.
            Codecs.strictOptionalFieldOf(FloorPatternEntry.CODEC, "pattern")
                    .forGetter(FloorConfig::pattern)
    ).apply(instance, FloorConfig::new));

    public BlockState baseState() {
        return BlockStateCodec.block(base, Blocks.STONE_BRICKS);
    }

    public BlockState alternateBaseState() {
        return BlockStateCodec.block(alternateBase, Blocks.STONE_BRICKS);
    }
}
