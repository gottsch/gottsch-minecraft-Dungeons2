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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.pit.CentrePitShape;
import mod.gottsch.forge.dungeons2.core.config.pit.PitShapePattern;
import mod.gottsch.forge.dungeons2.core.config.pit.PitShapeRegistry;

import java.util.Optional;

/**
 * The {@code pit} scheme slot: a sunken area in a room's floor. Backlog #3.
 *
 * <h2>A pit is dug out of the FLOOR'S OWN BUDGET, never out of the gap</h2>
 * <p>This is the rule the whole feature hangs on. A floor owns {@code floorHeight} blocks;
 * {@code sinkOffset} (#29) decides how many of them sit <em>below</em> the walking plane, and a pit
 * lives entirely in those. The stone buffer between floors, {@code gapBetweenFloors}, is not
 * available to it at any depth &mdash; the planner measures that buffer from the deepest possible
 * pit bottom rather than from the walking plane, so the separation between floors is preserved
 * whatever a scheme asks for.</p>
 *
 * <p><strong>Depth is the PROVIDER's to ask for and the GENERATOR's to grant.</strong> Each shape
 authors its own {@code depth} (they do not all mean the same thing by it &mdash; a court's is a
 maximum terrace, a hazard's is a sheer drop), and {@code RoomPitGenerator} clamps every cell to
 {@code sinkOffset} as it writes. The clamp is on the OUTPUT rather than on a field precisely
 because providers are extensible: a rule every third-party provider has to remember is one that
 gets forgotten, and the failure it allows is a hole into the room below. A pack asking for 5 on a
 floor that sank 3 gets 3, which is the honest degrade.</p>

 * <p>It cannot be a load error either: a pit is authored on a {@code motif_config} scheme and
 * {@code sinkOffset} on the {@code generation_config}, so no codec can see both &mdash; the same
 * wall {@code ChestConfig#clampedMaxCount} runs into.</p>
 *
 * <p><strong>{@code sinkOffset} 0 means no pit at all</strong>, which is what ships today. The slot
 * is authorable and simply draws nothing, the same degrade-don't-abort convention the
 * {@code spawners} slot follows for an unresolvable mob set. It is not an error: a pack tuned for a
 * taller pitch should still load on one that is not.</p>
 *
 * <h2>One material, because a terrace has no risers to line</h2>
 * <p>An unauthored {@code floorBlock} continues whatever the room is paved with, so a court reads
 * as the same floor at a lower level rather than as a different structure dropped into it.
 * Authoring it is how you say otherwise &mdash; a stone-lined cistern in a mud room, say.</p>
 *
 * <p>There is deliberately <strong>no {@code wallBlock}</strong>, but not for the reason this said
 * until 2026-08-29. The claim was that a terraced court makes every vertical face one block tall
 * and the side of the next terrace's own slab, so there is nothing left to line. That was true of
 * the courts and false the moment a SHEER shape shipped: a hazard shaft cuts a face as deep as it
 * is, and one that lands against a cave or an aquifer pours the terrain into the room &mdash;
 * observed in game, waterfall and all.</p>
 *
 * <p>The lining came back; the field did not. {@code RoomPitGenerator} now backs every cut face
 * with the pit's own floor block, unconditionally, for the same reason the depth clamp lives on the
 * output: a pit open along one side is never what an author meant, so it must not be something a
 * pack or a third-party provider can forget to ask for. Authoring {@code floorBlock} changes what
 * the lining is made of too, which keeps "one material" true &mdash; a stone-lined cistern is lined
 * in stone.</p>
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
public record PitPatternEntry(PitShapePattern shape, Optional<String> floorBlock, SizeGate gate) {

    /** An ungated pit of the default shape, paved like the floor around it. */
    public PitPatternEntry() {
        this(new CentrePitShape(), Optional.empty(), SizeGate.UNBOUNDED);
    }

    /** An ungated pit of a given shape. */
    public PitPatternEntry(PitShapePattern shape) {
        this(shape, Optional.empty(), SizeGate.UNBOUNDED);
    }

    /** This entry if the room may have it, empty otherwise. Mirrors {@code RoomScheme#floorFor}. */
    public Optional<PitPatternEntry> forRoom(int width, int depth, int height) {
        return gate.fits(width, depth, height) ? Optional.of(this) : Optional.empty();
    }

    // Codecs.closed -- see RoomScheme.CODEC.
        /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<PitPatternEntry> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
            // `type` + `config`, dispatched over the pit shape registry. An unregistered id is a
            // LOAD ERROR naming what is registered, not a room that quietly has no pit.
            PitShapeRegistry.MAP_CODEC.forGetter(PitPatternEntry::shape),
            Codecs.strictOptionalFieldOf(Codec.STRING, "floorBlock").forGetter(PitPatternEntry::floorBlock),
            SizeGate.MAP_CODEC.forGetter(PitPatternEntry::gate)
    ).apply(instance, PitPatternEntry::new));

    public static final Codec<PitPatternEntry> CODEC = Codecs.closed(MAP_CODEC);
}
