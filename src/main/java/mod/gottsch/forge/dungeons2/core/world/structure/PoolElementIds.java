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
package mod.gottsch.forge.dungeons2.core.world.structure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which template a jigsaw actually drew from its pool.
 *
 * <h2>Why this is not simply a getter</h2>
 * <p>1.20.1's {@code SinglePoolElement} keeps its template as a
 * {@code protected final Either<ResourceLocation, StructureTemplate>} with <strong>no accessor</strong>,
 * so the id cannot just be asked for. Three routes exist and two of them are traps:</p>
 * <ul>
 *   <li><strong>Reflection is wrong here, not merely ugly.</strong> The field is
 *       {@code template} in a development environment and its SRG name in a shipped jar, so a
 *       lookup by name works in dev and fails in production &mdash; the failure mode this project
 *       has already been bitten by once when verifying a published jar.</li>
 *   <li><strong>Parsing {@code toString()}</strong> is what {@code DungeonStructure} used while the
 *       id was only ever logged. The method survives obfuscation, but its <em>format</em> is not
 *       API and renders differently for each element type. Fine for a log line, not for something a
 *       generation decision keys on.</li>
 *   <li><strong>The element's own codec is public and is the datapack format.</strong>
 *       {@code StructurePoolElement.CODEC} round-trips through the same {@code "location"} key an
 *       author writes in a pool JSON, so this reads exactly the string the pack declared. Data
 *       rather than names, so obfuscation cannot touch it.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 * <p>An encode per assembled piece. That happens at <em>plan</em> time, a handful of times per
 * dungeon (see {@code DungeonStackPlanner}'s room-template attempt loop), not per chunk or per
 * block &mdash; so the convenience is worth more than the allocation.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public final class PoolElementIds {

    /** Key the pool-element codec writes its template path under; the datapack field name. */
    private static final String LOCATION = "location";

    private PoolElementIds() {}

    /**
     * The template id this element draws from, or empty when it has none to give.
     *
     * <p>Empty is a real answer, not a failure: an {@code EmptyPoolElement} has no template, a
     * {@code FeaturePoolElement} places a feature rather than a structure, and a pool element
     * holding an <em>inline</em> template (the {@code Either}'s right side) cannot be serialised at
     * all &mdash; vanilla's own encoder refuses it with "Can not serialize a runtime pool element".
     * A caller that cannot identify an element should treat it as unlimited rather than as an
     * error; an uncapped room is a much smaller problem than a dungeon that fails to generate.</p>
     */
    public static Optional<String> locationOf(StructurePoolElement element) {
        Optional<Tag> encoded = StructurePoolElement.CODEC
                .encodeStart(NbtOps.INSTANCE, element)
                .result();
        if (encoded.isEmpty() || !(encoded.get() instanceof CompoundTag tag)) {
            return Optional.empty();
        }
        String location = tag.getString(LOCATION);
        return location.isEmpty() ? Optional.empty() : Optional.of(location);
    }

    /**
     * The template ids behind a list of assembled pieces, in order, skipping any piece that is not
     * a pool element or cannot name its template.
     *
     * <p>A list rather than one id because an assembly may be a <em>chain</em> &mdash; a transition
     * is several linked pieces. For a room it is normally one, and a caller wanting "the room" wants
     * the first: jigsaw assembly starts from the piece placed at the requested position.</p>
     */
    public static List<String> locationsOf(List<StructurePiece> pieces) {
        List<String> ids = new ArrayList<>(pieces.size());
        for (StructurePiece piece : pieces) {
            if (piece instanceof PoolElementStructurePiece pool) {
                locationOf(pool.getElement()).ifPresent(ids::add);
            }
        }
        return ids;
    }
}
