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
 * <p><strong>Precedence: this COMPOSES with a scheme's own {@code wall} slot</strong> rather than
 * losing to it &mdash; the band draws first, the scheme draws on top, and where they claim the same
 * cell the scheme wins. Unlike {@code FloorConfig}'s and {@code CeilingConfig}'s, which a scheme
 * replaces outright. A wall is a stack of horizontal bands at different anchors, so both tiers fit;
 * a floor or ceiling is one surface, so they would fight. Resolved in one place,
 * {@code WallPatternSelector#providerFor}, which carries the measurement that decided it.</p>
 *
 * <p>Note a stratum replaces this section <em>whole</em> ({@code MotifConfig#forFloor} is
 * {@code orElse} per section), so a band that authors {@code wall} at all must restate the
 * {@code pattern} it wants alongside it &mdash; it does not inherit the motif's.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record WallConfig(String wall, Optional<WallPatternEntry> pattern) {

    /**
     * This section with any {@code $role} in its {@code pattern} resolved. #65 phase 5, and the
     * third and last section to need one &mdash; the floor's and ceiling's came in phases 3 and 4.
     *
     * <p>{@code wall} itself is untouched: a shell field, still on {@code Codecs.BLOCK_ID}, and
     * phase 7's business if it is ever worth converting at all.</p>
     */
    public WallConfig withRoles(java.util.function.UnaryOperator<String> resolver) {
        Optional<WallPatternEntry> resolved = pattern.map(entry -> entry.withRoles(resolver));
        return resolved.equals(pattern) ? this : new WallConfig(wall, resolved);
    }

    /** An undressed wall &mdash; the shape every motif had before {@code pattern} existed. */
    public WallConfig(String wall) {
        this(wall, Optional.empty());
    }

    public static final WallConfig DEFAULT = new WallConfig("minecraft:stone_bricks");

    public static final Codec<WallConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codecs.BLOCK_ID.fieldOf("wall").forGetter(WallConfig::wall),
            // strictOptionalFieldOf: a malformed pattern is a load error, not silently the same as
            // an absent one. See Codecs and #31.
            Codecs.strictOptionalFieldOf(WallPatternEntry.CODEC, "pattern")
                    .forGetter(WallConfig::pattern)
    ).apply(instance, WallConfig::new));

    public BlockState wallState() {
        return BlockStateCodec.block(wall, Blocks.STONE_BRICKS);
    }
}
