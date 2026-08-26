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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.world.level.block.Block;

/**
 * Shared helpers for the {@link FloorPattern} implementations.
 *
 * <h2>Block ids stay strings, and an unresolvable one still degrades</h2>
 * <p>The registry pilot changed what happens to an unknown <em>pattern type</em> (now a load
 * error). It deliberately did not change what happens to an unknown <em>block id</em>, which still
 * degrades the entry to plain floor. Those are separate policies and only the first was decided.
 * </p>
 *
 * <p>What the restructure <em>did</em> fix for free is the absent case: a pattern's blocks are now
 * declared {@code fieldOf} on that pattern's own codec, so leaving one out is a load error rather
 * than a silent degrade. Under the flat record that was impossible &mdash; every slot had to be
 * optional, because every other pattern's slots were absent by design.</p>
 */
final class FloorPatterns {

    private FloorPatterns() {}

    /** The block, or {@code null} when the id names nothing registered. */
    static Block block(String id) {
        return BlockStateCodec.blockOrNull(id);
    }

    /** True when every id resolves; the caller degrades the whole entry to plain if not. */
    static boolean allResolve(Block... blocks) {
        for (Block block : blocks) {
            if (block == null) {
                return false;
            }
        }
        return true;
    }
}
