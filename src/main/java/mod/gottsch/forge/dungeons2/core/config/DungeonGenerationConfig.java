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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Datapack-driven, codec-backed dungeon generation tuning.
 * <p>
 * Entries live at {@code data/dungeons2/dungeons2/generation_config/<name>.json}. There is
 * currently one shipped entry, {@code default}, looked up via
 * {@link DungeonGenerationConfigHelper#get}. This replaces the old
 * {@code Config.SERVER.dungeons.corridorWidth} {@code ForgeConfigSpec} field with the same
 * datapack-registry + {@link Codec} pattern already used by gmm's {@code MobConfig}
 * ({@code mod.gottsch.forge.gmm.core.config.MobConfig}) &mdash; reloadable with the world's
 * datapacks, no restart required.
 * <p>
 * <strong>Future knobs:</strong> per-size-tier values (room count range, floor count range,
 * footprint range &mdash; currently hard-coded per tier in {@link mod.gottsch.forge.dungeons2.core.data.DungeonSize})
 * are a natural follow-up, but are a separate migration: {@code DungeonStackPlanner} is
 * deliberately Minecraft-import-free and rolls its own size tier internally from the seed, so
 * per-size config can't be resolved by the caller up front the way {@code corridorWidth} is
 * here (the caller already knows what it wants before planning starts). Threading per-size
 * config in would need the same builder-injection approach used for {@code withCorridorWidth}/
 * {@code withTransitionAssembler}, just resolved per-tier once the planner has picked one
 * &mdash; likely its own registry keyed by tier name ({@code small}/{@code medium}/{@code large}).
 *
 * @author Mark Gottschling on Jul 25, 2026
 */
public record DungeonGenerationConfig(int corridorWidth, int roomTemplateAttemptsPerFloor,
                                      List<RoomHeightBand> roomHeightBands,
                                      int floorHeight, int gapBetweenFloors) {

    /**
     * The shipped floor-to-floor pitch, {@code floorHeight + gapBetweenFloors} = 12.
     *
     * <h2>Changing this is an AUTHORING decision, not a tuning one</h2>
     * <p>The pitch is the distance a transition template has to bridge, and <strong>every entrance
     * and transition that ships is cut for exactly 12</strong>. Move it and they no longer reach:
     * {@code stairs_1} and {@code ladder1} carry their two door markers 12 apart and cannot be
     * stretched, and the {@code stairs_2} chain lands only on multiples of 6. The entrance is the
     * same story from the other end &mdash; its bottom door marker <em>defines</em> floor 0's
     * walking plane, so a taller floor puts floor 0's ceiling above the entrance chamber's own.
     * See backlog #52 and #29.</p>
     *
     * <p>This is why the shipped file carries a {@code _comment} saying so: a pack author changing
     * a number needs the warning in front of them at that moment, not in a document they may not
     * have read. {@code TransitionSpanTest} fails the build and {@code [D2-SPAN]} logs at ERROR if
     * the templates cannot reach, so the failure is loud &mdash; but it is still a failure, and the
     * fix for it is re-cutting {@code .nbt} files.</p>
     */
    public static final int DEFAULT_FLOOR_HEIGHT = 10;
    /** See {@link #DEFAULT_FLOOR_HEIGHT}. The stone buffer below a walking plane. */
    public static final int DEFAULT_GAP_BETWEEN_FLOORS = 2;

    /**
     * The shipped room-height taper (#51), kept in step with
     * {@code data/dungeons2/dungeons2/generation_config/default.json}. The ceiling <em>falls</em>
     * as the footprint grows: a 7x7 may be a shaft, a 19x19 may not be a box. See
     * {@link RoomHeightBand} for why it is a clamp rather than a re-roll.
     */
    public static final List<RoomHeightBand> DEFAULT_ROOM_HEIGHT_BANDS = List.of(
            new RoomHeightBand(Optional.of(7), 6, 10),
            new RoomHeightBand(Optional.of(11), 5, 9),
            new RoomHeightBand(Optional.of(15), 5, 8),
            new RoomHeightBand(Optional.empty(), 5, 7));

    /** Fallback used when no entry exists, so lookups never NPE. */
    public static final DungeonGenerationConfig DEFAULT =
            new DungeonGenerationConfig(3, 4, DEFAULT_ROOM_HEIGHT_BANDS,
                    DEFAULT_FLOOR_HEIGHT, DEFAULT_GAP_BETWEEN_FLOORS);

    /** The floor-to-floor drop a transition must span. See {@link #DEFAULT_FLOOR_HEIGHT}. */
    public int pitch() {
        return floorHeight + gapBetweenFloors;
    }

    /** True if the pitch is the one every shipped entrance and transition was cut for. */
    public boolean pitchIsShipped() {
        return pitch() == DEFAULT_FLOOR_HEIGHT + DEFAULT_GAP_BETWEEN_FLOORS;
    }

    // Codecs.closed + strictOptionalFieldOf -- see RoomScheme.CODEC and Codecs#closed. With DFU's
    // own optionalFieldOf, "corridorWidth": 5 decoded cleanly to 3 and every corridor in the
    // dungeon came out a width the author never asked for, with nothing anywhere saying so.
    public static final Codec<DungeonGenerationConfig> CODEC = Codecs.closed(
            Codecs.documented(RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 3), "corridorWidth",
                            DEFAULT.corridorWidth()).forGetter(DungeonGenerationConfig::corridorWidth),
                    // 0 is meaningful and deliberately in range: it turns prefab rooms off entirely
                    // without deleting the pool, which is the only way to A/B them. The upper bound
                    // is a cost guard -- each attempt is TWO jigsaw assemblies (probe + place).
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, 8), "roomTemplateAttemptsPerFloor",
                            DEFAULT.roomTemplateAttemptsPerFloor())
                            .forGetter(DungeonGenerationConfig::roomTemplateAttemptsPerFloor),
                    // #51. Omitting the key keeps the shipped taper rather than removing the cap:
                    // an uncapped roll puts a height-10 ceiling on a 19x19 room, which is the box
                    // this exists to prevent, so "absent" must not mean "off".
                    Codecs.strictOptionalFieldOf(RoomHeightBand.LIST_CODEC, "roomHeightBands",
                            DEFAULT_ROOM_HEIGHT_BANDS)
                            .forGetter(DungeonGenerationConfig::roomHeightBands),
                    // Bounded, not free. Below 6 a room has no interior worth decorating; above 24
                    // a transition would need more segments than any authored chain has. Neither
                    // bound is the real constraint -- see DEFAULT_FLOOR_HEIGHT, the real constraint
                    // is that the shipped templates are cut for 12.
                    Codecs.strictOptionalFieldOf(Codec.intRange(6, 24), "floorHeight",
                            DEFAULT_FLOOR_HEIGHT).forGetter(DungeonGenerationConfig::floorHeight),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, 8), "gapBetweenFloors",
                            DEFAULT_GAP_BETWEEN_FLOORS)
                            .forGetter(DungeonGenerationConfig::gapBetweenFloors)
            ).apply(instance, DungeonGenerationConfig::new)),
            // The file's own warning label. Declared so the closed schema accepts it, decoded to
            // nothing -- see Codecs#documented for why a config like this needs one at all.
            "_comment"));
}
