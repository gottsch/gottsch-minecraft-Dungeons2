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
 * The room wall section of a {@link MotifConfig}.
 *
 * <p>Only one block: the pre-merge {@code block_provider} schema also carried {@code corner} and
 * {@code top_corner} here, but {@code BasicWallGenerator} never read either &mdash; it only ever
 * queried {@code WallPattern.WALL} &mdash; so they were dead data and are not carried forward.
 * Add them back alongside the generator code that actually places them.</p>
 *
 * <h2>{@code pattern} &mdash; what this motif or stratum dresses its walls with</h2>
 * <p>The counterpart to {@code FloorConfig}'s, added for the same reason and a day later: a
 * <strong>stratum</strong> is a {@link WallConfig}, not a scheme, so without this a depth band
 * could say "my walls are mud brick" but never "my walls are coursed". That left the mud band a
 * repainted classic room rather than a different depth, since only its floor could be dressed.</p>
 *
 * <p><strong>Precedence: a scheme's own {@code wall} slot wins.</strong> A room that asked for
 * pilasters asked for them at every depth; this is the default underneath, not an override on top.
 * Resolved in one place, {@code WallPatternSelector#providerFor}.</p>
 *
 * <p>Note a stratum replaces this section <em>whole</em> ({@code MotifConfig#forFloor} is
 * {@code orElse} per section), so a band that authors {@code wall} at all must restate the
 * {@code pattern} it wants alongside it &mdash; it does not inherit the motif's.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record WallConfig(String wall, Optional<WallPatternEntry> pattern) {

    /** An undressed wall &mdash; the shape every motif had before {@code pattern} existed. */
    public WallConfig(String wall) {
        this(wall, Optional.empty());
    }

    public static final WallConfig DEFAULT = new WallConfig("minecraft:stone_bricks");

    public static final Codec<WallConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("wall").forGetter(WallConfig::wall),
            // strictOptionalFieldOf: a malformed pattern is a load error, not silently the same as
            // an absent one. See Codecs and #31.
            Codecs.strictOptionalFieldOf(WallPatternEntry.CODEC, "pattern")
                    .forGetter(WallConfig::pattern)
    ).apply(instance, WallConfig::new));

    public BlockState wallState() {
        return BlockStateCodec.block(wall, Blocks.STONE_BRICKS);
    }
}
