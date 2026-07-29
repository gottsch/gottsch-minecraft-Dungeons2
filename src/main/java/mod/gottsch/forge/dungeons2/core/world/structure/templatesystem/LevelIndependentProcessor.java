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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.dungeons2.core.world.structure.PieceProcessors;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

/**
 * Marker for a {@link StructureProcessor} that <strong>never touches the
 * {@code ServerLevelAccessor} it is handed</strong> &mdash; it decides purely from the block
 * list, the block states in it, and their positions.
 *
 * <p>That single property is what licenses {@link PieceProcessors} to run a processor over a
 * procedural piece's <em>whole</em> block list instead of just the part inside the chunk
 * currently being generated. Reading the world outside the active {@code WorldGenRegion} is
 * illegal, so a processor that reads must be clipped; one that doesn't, needn't be.</p>
 *
 * <h2>Why anything wants to be unclipped</h2>
 * <ul>
 *   <li><strong>Neighbour-aware processors must be.</strong> A neighbour map built from one
 *       chunk's slice is missing everything across the seam, so the piece would decorate
 *       differently on each side of it. {@link DecorationProcessor} is in this category.</li>
 *   <li><strong>Per-block processors benefit.</strong> Being unclipped puts them in the same
 *       pass as the neighbour-aware ones, which preserves the order they were authored in.
 *       {@link AgingProcessor} is marked for this reason: it crumbles some blocks to air and
 *       turns others to dirt, and decoration should see that, the way it does for a jigsaw
 *       prefab.</li>
 * </ul>
 *
 * <h2>What implementing this commits you to</h2>
 * <ul>
 *   <li>No level access at all. In particular <em>not</em>
 *       {@code BlockState#isSolidRender(BlockGetter, BlockPos)}, which looks like a pure state
 *       query but falls through to {@code getOcclusionShape(level, pos)} for blocks with a
 *       dynamic shape. Use {@code canOcclude()}.</li>
 *   <li>Positional determinism: seed from {@code Mth.getSeed(pos)}, never
 *       {@code level.getRandom()} or {@code settings.getRandom()}. A piece is processed once
 *       per chunk it overlaps, and a seam block must resolve identically every time.</li>
 * </ul>
 *
 * <p>An unmarked processor that decides from the whole block list &mdash; {@code
 * minecraft:capped}, say &mdash; is in neither category and belongs in no dungeon processor
 * list: it would land in the clipped pass and cap per chunk.
 * {@code WeatheringProcessorListTest} enforces that on the shipped file.</p>
 *
 * @author Mark Gottschling on Jul 28, 2026
 */
public interface LevelIndependentProcessor {
}
