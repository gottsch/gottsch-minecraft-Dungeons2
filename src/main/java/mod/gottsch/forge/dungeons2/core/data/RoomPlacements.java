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
package mod.gottsch.forge.dungeons2.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything one room render produces: {@link BlockPlacement}s and {@link EntityPlacement}s.
 *
 * <p>Exists so {@code IRoomGenerator#build} takes one output parameter rather than growing another
 * list every time the pipeline learns to emit a new kind of thing. Blocks were the only channel
 * until pots arrived; the two are written to the world by genuinely different code paths (blocks go
 * through the processor/decoration pass and are idempotent, entities bypass it and are not), so
 * they cannot simply share a list.</p>
 *
 * <p>The three element sub-builders (wall / floor / ceiling) deliberately still take a bare
 * {@code List<BlockPlacement>} &mdash; none of them has any business emitting an entity, and
 * handing them a channel they must not use is worse than not handing it to them. Only the
 * orchestrator sees both.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class RoomPlacements {

    private final List<BlockPlacement> blocks = new ArrayList<>();
    private final List<EntityPlacement> entities = new ArrayList<>();

    public List<BlockPlacement> getBlocks() {
        return blocks;
    }

    public List<EntityPlacement> getEntities() {
        return entities;
    }

    @Override
    public String toString() {
        return "RoomPlacements{" + blocks.size() + " blocks, " + entities.size() + " entities}";
    }
}
