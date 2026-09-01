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
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How many times one authored template may be placed &mdash; backlog #44.
 *
 * <h2>Templates, not schemes</h2>
 * <p>This caps <strong>authored</strong> rooms only. A procedural room's scheme is a dressing style,
 * not an identity: capping one would make the second large room fall back to a lesser dressing for a
 * reason no player can perceive, and procedural rooms exist to fill gaps rather than to be unique.
 * A designed space is a template, so that is what carries a limit.</p>
 *
 * <h2>Both bounds are optional, and 0 is meaningful</h2>
 * <p>{@code max_per_floor} and {@code max_per_dungeon} compose: whichever binds first stops the
 * placement. A template with neither is a load error rather than a no-op &mdash; an entry that caps
 * nothing is an authoring mistake, and the whole point of declaring one is to constrain something.
 * </p>
 *
 * <p><strong>{@code maxPerDungeon: 0} is legal and means "never place this"</strong>, which is how a
 * pack disables a base mod's room without rewriting its template pool. That is why the range starts
 * at 0 rather than 1.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public record TemplateLimit(Optional<Integer> maxPerFloor, Optional<Integer> maxPerDungeon) {

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<TemplateLimit> CODEC = Codecs.closed(RecordCodecBuilder.<TemplateLimit>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_per_floor")
                    .forGetter(TemplateLimit::maxPerFloor),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_per_dungeon")
                    .forGetter(TemplateLimit::maxPerDungeon)
    ).apply(instance, TemplateLimit::new))).flatXmap(TemplateLimit::validate, TemplateLimit::validate);

    private static DataResult<TemplateLimit> validate(TemplateLimit limit) {
        if (limit.maxPerFloor.isEmpty() && limit.maxPerDungeon.isEmpty()) {
            return DataResult.error(() -> "template_limits entry declares neither max_per_floor nor"
                    + " max_per_dungeon, so it limits nothing. Remove the entry, or give it a bound");
        }
        return DataResult.success(limit);
    }

    /**
     * Whether one more copy may be placed, given how many are already committed.
     *
     * <p>Both counts are of <em>committed</em> placements, not attempts: an assembly the planner
     * measured and then rejected never existed as far as a player is concerned, so it must not
     * consume the budget.</p>
     */
    public boolean allows(int placedOnThisFloor, int placedInThisDungeon) {
        return maxPerFloor.map(max -> placedOnThisFloor < max).orElse(true)
                && maxPerDungeon.map(max -> placedInThisDungeon < max).orElse(true);
    }
}
