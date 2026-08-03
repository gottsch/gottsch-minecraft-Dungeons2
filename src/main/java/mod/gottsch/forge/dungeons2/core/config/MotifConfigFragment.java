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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One <em>file</em> of a motif. Several of these fold into the single {@link MotifConfig} a motif
 * renders with; this is the type the {@code motif_config} datapack registry actually holds.
 *
 * <h2>Why a motif is a folder, not a file</h2>
 * <p>A motif's entry is now everything under {@code motif_config/<motif>/}, not one
 * {@code motif_config/<motif>.json}. Classic passed 390 lines with three of its five element types
 * still unwritten, and a scheme list is authored content that only grows &mdash; one file per motif
 * makes the wall schemes and the floor schemes neighbours in a scroll rather than in a structure,
 * and makes any two edits to the same motif a merge conflict. Splitting by folder costs nothing at
 * runtime: the registry loads every file under the folder as its own entry anyway, and
 * {@link MotifConfigHelper} collects them by path.</p>
 *
 * <p>The flat form still works. {@code motif_config/<motif>.json} is a fragment like any other and
 * layers <em>underneath</em> the folder's files (it sorts first, being a prefix of them), so a
 * one-section motif stays a one-file motif &mdash; see {@code catacombs.json}.</p>
 *
 * <h2>Why this is not just {@link MotifConfig} with optional fields</h2>
 * <p>Merging needs to tell "this file does not mention walls" from "this file authors the default
 * wall". {@link MotifConfig}'s codec cannot: an absent section decodes to that section's
 * {@code DEFAULT}, which is the right answer for a whole motif and the wrong one for a fragment
 * &mdash; a fragment holding only schemes would carry stone_bricks walls and stomp the base file's.
 * Absence has to survive decoding to be mergeable, so it is {@link Optional} here, and the defaults
 * are applied once at the end by {@link #resolve}.</p>
 *
 * @author Mark Gottschling on Aug 2, 2026
 */
public record MotifConfigFragment(Optional<WallConfig> wall, Optional<CeilingConfig> ceiling,
                                  Optional<DoorConfig> door, Optional<CorridorConfig> corridor,
                                  Optional<FloorConfig> floor, List<RoomScheme> schemes) {

    public static final Codec<MotifConfigFragment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codecs.strictOptionalFieldOf(WallConfig.CODEC, "wall").forGetter(MotifConfigFragment::wall),
            Codecs.strictOptionalFieldOf(CeilingConfig.CODEC, "ceiling").forGetter(MotifConfigFragment::ceiling),
            Codecs.strictOptionalFieldOf(DoorConfig.CODEC, "door").forGetter(MotifConfigFragment::door),
            Codecs.strictOptionalFieldOf(CorridorConfig.CODEC, "corridor").forGetter(MotifConfigFragment::corridor),
            Codecs.strictOptionalFieldOf(FloorConfig.CODEC, "floor").forGetter(MotifConfigFragment::floor),
            Codecs.strictOptionalFieldOf(RoomScheme.CODEC.listOf(), "schemes", List.of())
                    .forGetter(MotifConfigFragment::schemes)
    ).apply(instance, MotifConfigFragment::new));

    /**
     * Folds a motif's fragments, <strong>in the order given</strong>, into the config it renders
     * with. {@link MotifConfigHelper} supplies them sorted by id, which is what makes the result a
     * function of the datapack's content rather than of registry iteration order.
     *
     * <p>Two merge rules, chosen so that a later file can only ever add to or replace something
     * nameable, never silently subtract:</p>
     * <ul>
     *   <li><strong>Element sections</strong> (the materials) are whole-section: the last fragment
     *       that authors {@code wall} wins it outright. Not field-by-field, because a section is
     *       already all-or-nothing at the codec level &mdash; every block within one is required
     *       precisely so a half-authored section fails loudly.</li>
     *   <li><strong>Schemes</strong> concatenate, and a later scheme with a name already seen
     *       <em>replaces</em> the earlier one in place. That is what makes a scheme addressable: an
     *       addon (or a {@code schemes_pots.json} sitting beside a {@code base.json}) can retune
     *       {@code plain}'s weight without restating the list. Keeping the original position means
     *       overriding a scheme cannot quietly reorder the rest.</li>
     * </ul>
     *
     * <p>An empty scheme list resolves to {@link RoomScheme#PLAIN} alone, the same floor
     * {@link MotifConfig#DEFAULT} has: a motif that authors materials but no schemes is undecorated,
     * not unrenderable.</p>
     */
    public static MotifConfig resolve(List<MotifConfigFragment> fragments) {
        WallConfig wall = WallConfig.DEFAULT;
        CeilingConfig ceiling = CeilingConfig.DEFAULT;
        DoorConfig door = DoorConfig.DEFAULT;
        CorridorConfig corridor = CorridorConfig.DEFAULT;
        FloorConfig floor = FloorConfig.DEFAULT;
        Map<String, RoomScheme> schemes = new LinkedHashMap<>();

        for (MotifConfigFragment fragment : fragments) {
            wall = fragment.wall().orElse(wall);
            ceiling = fragment.ceiling().orElse(ceiling);
            door = fragment.door().orElse(door);
            corridor = fragment.corridor().orElse(corridor);
            floor = fragment.floor().orElse(floor);
            for (RoomScheme scheme : fragment.schemes()) {
                // LinkedHashMap#put keeps an existing key's position, which is the "replaces in
                // place" half of the rule above.
                schemes.put(scheme.name(), scheme);
            }
        }

        return new MotifConfig(wall, ceiling, door, corridor, floor,
                schemes.isEmpty() ? List.of(RoomScheme.PLAIN) : List.copyOf(schemes.values()));
    }
}
