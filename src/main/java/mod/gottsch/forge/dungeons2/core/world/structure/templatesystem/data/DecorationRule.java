/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * One decoration behaviour of
 * {@link mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.DecorationProcessor}:
 * how often it fires, and what it may place.
 *
 * <p>Unlike the aging chains, {@code probability} here is <strong>absolute</strong> &mdash;
 * one roll per candidate position, no conditional composition with anything else.</p>
 *
 * <p>A rule is <em>inactive</em> unless both a non-zero probability and a non-empty palette
 * are given, so every behaviour is off until a datapack turns it on. That's deliberate:
 * these palettes name blocks from other mods, and a motif that doesn't want a behaviour
 * shouldn't have to name blocks to disable it.</p>
 *
 * @author Mark Gottschling on Jul 28, 2026
 */
public record DecorationRule(float probability, List<Block> blocks) {

    public static final DecorationRule NONE = new DecorationRule(0.0F, List.of());

    public static final Codec<DecorationRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("probability", 0.0F).forGetter(DecorationRule::probability),
            BuiltInRegistries.BLOCK.byNameCodec().listOf()
                    .optionalFieldOf("blocks", List.of()).forGetter(DecorationRule::blocks)
    ).apply(instance, DecorationRule::new));

    public DecorationRule {
        blocks = List.copyOf(blocks);
    }

    public boolean isActive() {
        return probability > 0.0F && !blocks.isEmpty();
    }

    /** Uniform pick. Draws from {@code random} even for a single-entry palette, so the
     *  number of draws at a position doesn't depend on how the palette was authored. */
    public Block pick(RandomSource random) {
        return blocks.get(random.nextInt(blocks.size()));
    }
}
