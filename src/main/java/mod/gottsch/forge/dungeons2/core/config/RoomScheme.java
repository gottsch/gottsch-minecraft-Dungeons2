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

import java.util.Optional;

/**
 * One weighted way to dress a room: a coordinated set of decorative treatments, one slot per
 * architectural element, rolled <strong>once per room</strong> by {@code RoomSchemeSelector}.
 *
 * <h2>Why the roll is per-room and not per-element</h2>
 * <p>Until Jul 2026 the floor rolled its own pattern from {@code FloorConfig.patterns} and nothing
 * else had a pattern list at all, so there was nothing to coordinate with. Walls, ceilings and
 * pillars each want one too &mdash; and independent per-element rolls guarantee combinations no
 * author chose: pilasters at an offset that doesn't line up with the vault they carry, a formal
 * bordered floor under rough undecorated walls. Elements of a room are not independent variables;
 * an architectural style is one choice with several consequences. So the weighted roll happens
 * here, at the room level, and each element slot just names the treatment that scheme wants.</p>
 *
 * <p>The cost is that shared treatments are repeated across schemes rather than referenced &mdash;
 * two schemes that want the same floor border spell it out twice. That is the same trade
 * {@link MotifConfig} already accepted for one-file-per-motif: authoring redundancy in exchange for
 * one mechanism instead of a lookup layer between two.</p>
 *
 * <h2>Element slots</h2>
 * <p>{@code floor} holds a single {@link FloorPatternEntry} &mdash; the same record the old
 * {@code patterns} list held, minus the roll. Its {@code weight} field is ignored here; only this
 * scheme's own {@link #weight} matters. An absent slot means "plain for that element", so a scheme
 * with no slots at all is the undecorated room.</p>
 *
 * <p>Slots for {@code wall}, {@code ceiling} and {@code pillars} are deliberately <em>not</em>
 * declared yet: they are additive optional codec fields, and there are no providers behind them to
 * give them meaning. The load-bearing decision is that this container exists and owns the roll, not
 * that it is populated up front.</p>
 *
 * <h2>Eligibility</h2>
 * <p>{@link #minHeight} and {@link #minSize} filter a scheme out of the roll for rooms too small to
 * carry it, <em>before</em> weights are totalled. This matters more than it did for floors:
 * {@code DungeonStackPlanner#pickRoomHeight} rolls {@code min(rand(5..10), max(width, depth))}, so a
 * room has only {@code height - 2} interior wall rows &mdash; between <strong>3 and 8</strong>. At
 * the low end, rows 1 and 2 are the door halves and row 3 is the door lintel, leaving nowhere to put
 * a crown molding course. A vaulted ceiling or a two-course wall is not a pattern that degrades
 * gracefully in a 5-high room; it is a pattern that must not be rolled there.</p>
 *
 * <p>{@code minHeight} is measured against the room's <strong>full</strong> height (floor block
 * through ceiling block inclusive, what {@code RoomData#getHeight} returns), not the interior row
 * count, because that is the number the planner actually rolls. {@code minSize} is measured against
 * the <em>smaller</em> of width and depth, so a long thin room is gated by its narrow axis &mdash;
 * which is the one that makes a centred pattern degenerate.</p>
 *
 * <p>Both default to 0 (always eligible). Authors should keep at least one unconstrained scheme in
 * the list; if a room matches none, {@code RoomSchemeSelector} degrades to the undecorated room
 * rather than forcing an ineligible one.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record RoomScheme(String name, int weight, int minHeight, int minSize,
                         Optional<FloorPatternEntry> floor) {

    /** The undecorated room: plain floor, plain walls, plain ceiling, eligible everywhere. */
    public static final RoomScheme PLAIN = new RoomScheme("plain", 1, 0, 0, Optional.empty());

    public static final Codec<RoomScheme> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(RoomScheme::name),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(RoomScheme::weight),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("minHeight", 0).forGetter(RoomScheme::minHeight),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("minSize", 0).forGetter(RoomScheme::minSize),
            Codecs.strictOptionalFieldOf(FloorPatternEntry.CODEC, "floor").forGetter(RoomScheme::floor)
    ).apply(instance, RoomScheme::new));

    /**
     * Whether this scheme may be rolled for a room of these dimensions. {@code height} is the full
     * room height; {@code width}/{@code depth} the full footprint, walls included.
     */
    public boolean fits(int width, int depth, int height) {
        return height >= minHeight && Math.min(width, depth) >= minSize;
    }
}
