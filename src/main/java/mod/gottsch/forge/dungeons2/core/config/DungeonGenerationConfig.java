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
import com.mojang.serialization.DataResult;
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
 * per-size config can't be resolved by the caller up front the way {@code corridor_width} is
 * here (the caller already knows what it wants before planning starts). Threading per-size
 * config in would need the same builder-injection approach used for {@code withCorridorWidth}/
 * {@code withTransitionAssembler}, just resolved per-tier once the planner has picked one
 * &mdash; likely its own registry keyed by tier name ({@code small}/{@code medium}/{@code large}).
 *
 * @author Mark Gottschling on Jul 25, 2026
 */
public record DungeonGenerationConfig(int corridorWidth, int roomTemplateAttemptsPerFloor,
                                      List<RoomHeightBand> roomHeightBands,
                                      int floorHeight, int gapBetweenFloors, int sinkOffset) {

    /** The shape before {@code sink_offset}: a floor whose walking plane is its own slab. */
    public DungeonGenerationConfig(int corridorWidth, int roomTemplateAttemptsPerFloor,
                                   List<RoomHeightBand> roomHeightBands,
                                   int floorHeight, int gapBetweenFloors) {
        this(corridorWidth, roomTemplateAttemptsPerFloor, roomHeightBands, floorHeight,
                gapBetweenFloors, DEFAULT_SINK_OFFSET);
    }

    /**
     * The shipped floor-to-floor pitch, {@code floorHeight + gapBetweenFloors} = 12.
     *
     * <h2>Changing this is an AUTHORING decision, not a tuning one</h2>
     * <p><strong>Raised to 20 on 2026-08-27</strong> (backlog #29 stage 2, Gottsch), for a pitch of
     * 22: a 15-block ceiling budget with a {@code sink_offset} of 5 underneath it for pits. The 15
     * is headroom for <strong>authored</strong> rooms &mdash; a template may be cut that tall and
     * now fits. {@code DungeonStackPlanner#pickRoomHeight} still caps a PROCEDURAL room at 10, and
     * the slack above one costs nothing, since unused floor height is unexcavated stone rather
     * than empty volume. <strong>The shipped
     * templates are still cut for 12 and are being re-authored</strong>, so until they land a
     * generated dungeon has transitions that stop short of the floor above. That is expected, and
     * it is why {@code TransitionSpanTest} is tagged {@code release} rather than failing
     * {@code test}.</p>
     *
     * <p>The pitch is the distance a transition template has to bridge. Move it and they no longer
     * reach:
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
    public static final int DEFAULT_FLOOR_HEIGHT = 20;
    /** See {@link #DEFAULT_FLOOR_HEIGHT}. The stone buffer below a walking plane. */
    public static final int DEFAULT_GAP_BETWEEN_FLOORS = 2;

    /**
     * How far the walking plane sits UP into its own floor's slab &mdash; the room a pit has to
     * sink into (#3). <strong>0, and shipping at 0 is the point:</strong> at 0 this whole mechanism
     * is arithmetically absent and every existing world lays out identically.
     *
     * <h2>It is bought from the ceiling, not from the descent</h2>
     * <p>A floor owns exactly {@code floor_height} blocks however this is set. {@code sink_offset}
     * moves the boundary inside that budget: {@code ceilingBudget = floorHeight - sinkOffset} above
     * the walking plane, {@code sink_offset} below it. So the drop between two walking planes stays
     * {@code floorHeight + gapBetweenFloors} &mdash; <strong>pit depth costs nothing in
     * descent</strong>, and no transition template has to change to allow pits. Raising
     * {@code gap_between_floors} to make room instead would lengthen every transition, which is the
     * trap #3's original sketch fell into.</p>
     *
     * <p>The stone buffer is preserved rather than eaten: floor {@code i-1}'s pit bottom lands
     * exactly {@code gap_between_floors} blocks above floor {@code i}'s ceiling, because the stacking
     * measures the buffer from the pit bottom rather than from the walking plane.</p>
     *
     * <p><strong>At the shipped {@code floor_height} of 10 this trades one-for-one against room
     * height</strong> &mdash; {@code sink_offset} 5 leaves rooms 5 high, which is the shortest a
     * room can be. Pits become worth having once the pitch rises (#29 stage 2); until then the
     * machinery is here and switched off. Mark's worked numbers: {@code 20/5/2} gives rooms to 15,
     * pits to 5, and a transition drop of 22.</p>
     */
    public static final int DEFAULT_SINK_OFFSET = 5;

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
                    DEFAULT_FLOOR_HEIGHT, DEFAULT_GAP_BETWEEN_FLOORS, DEFAULT_SINK_OFFSET);

    /**
     * The vertical budget ABOVE the walking plane &mdash; what a room's height is actually bounded
     * by, and what {@code DungeonStackPlanner} caps its height roll to.
     *
     * <p>Equal to {@code floor_height} until something sets {@link #sinkOffset}. Deriving it is the
     * point of #29's stage 1: {@code DEFAULT_FLOOR_HEIGHT} and the height roll's own maximum are
     * both 10 <em>by coincidence</em>, and nothing anywhere asserted the invariant they encode
     * &mdash; that a room must never be taller than the floor holding it, or its ceiling punches
     * through {@code gap_between_floors} into the floor above.</p>
     */
    public int ceilingBudget() {
        return floorHeight - sinkOffset;
    }

    /** The floor-to-floor drop a transition must span. See {@link #DEFAULT_FLOOR_HEIGHT}. */
    public int pitch() {
        return floorHeight + gapBetweenFloors;
    }

    /** True if the pitch is the one every shipped entrance and transition was cut for. */
    public boolean pitchIsShipped() {
        return pitch() == DEFAULT_FLOOR_HEIGHT + DEFAULT_GAP_BETWEEN_FLOORS;
    }

    // Codecs.closed + strictOptionalFieldOf -- see RoomScheme.CODEC and Codecs#closed. With DFU's
    // own optionalFieldOf, "corridor_width": 5 decoded cleanly to 3 and every corridor in the
    // dungeon came out a width the author never asked for, with nothing anywhere saying so.
    public static final Codec<DungeonGenerationConfig> CODEC = Codecs.closed(
            Codecs.documented(RecordCodecBuilder.<DungeonGenerationConfig>mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 3), "corridor_width",
                            DEFAULT.corridorWidth()).forGetter(DungeonGenerationConfig::corridorWidth),
                    // 0 is meaningful and deliberately in range: it turns prefab rooms off entirely
                    // without deleting the pool, which is the only way to A/B them. The upper bound
                    // is a cost guard -- each attempt is TWO jigsaw assemblies (probe + place).
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, 8), "room_template_attempts_per_floor",
                            DEFAULT.roomTemplateAttemptsPerFloor())
                            .forGetter(DungeonGenerationConfig::roomTemplateAttemptsPerFloor),
                    // #51. Omitting the key keeps the shipped taper rather than removing the cap:
                    // an uncapped roll puts a height-10 ceiling on a 19x19 room, which is the box
                    // this exists to prevent, so "absent" must not mean "off".
                    Codecs.strictOptionalFieldOf(RoomHeightBand.LIST_CODEC, "room_height_bands",
                            DEFAULT_ROOM_HEIGHT_BANDS)
                            .forGetter(DungeonGenerationConfig::roomHeightBands),
                    // Bounded, not free. Below 6 a room has no interior worth decorating; above 24
                    // a transition would need more segments than any authored chain has. Neither
                    // bound is the real constraint -- see DEFAULT_FLOOR_HEIGHT, the real constraint
                    // is that the shipped templates are cut for 12.
                    Codecs.strictOptionalFieldOf(Codec.intRange(6, 24), "floor_height",
                            DEFAULT_FLOOR_HEIGHT).forGetter(DungeonGenerationConfig::floorHeight),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, 8), "gap_between_floors",
                            DEFAULT_GAP_BETWEEN_FLOORS)
                            .forGetter(DungeonGenerationConfig::gapBetweenFloors),
                    // #3/#29. Upper bound is floorHeight's own, since a range cannot name another
                    // field; what actually constrains it is validateBudget below.
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, 24), "sink_offset",
                            DEFAULT_SINK_OFFSET).forGetter(DungeonGenerationConfig::sinkOffset)
            ).apply(instance, DungeonGenerationConfig::new)).flatXmap(
                    DungeonGenerationConfig::validateBudget, DungeonGenerationConfig::validateBudget),
            // The file's own warning labels. Declared so the closed schema accepts them, decoded to
            // nothing -- see Codecs#documented for why a config like this needs one at all.
            // "//sink_offset" sits beside the field it explains rather than in the header block: the
            // header is the pitch warning, and burying a second unrelated essay in it is how both
            // stop being read.
            "_comment", "//sink_offset", "//room_height_bands"));

    /**
     * The one thing this file can say that no field range can catch: a {@code sink_offset} that
     * leaves no room to stand in.
     *
     * <p>It is a relationship between three fields &mdash; {@code floor_height}, {@code sink_offset}
     * and the shortest room {@code room_height_bands} can produce &mdash; so no {@code intRange} can
     * express it, the same shape as {@code Stratum}'s name check and {@code RoomScheme}'s inverted
     * range. Worth failing rather than clamping: a budget too small for its own bands would produce
     * rooms whose ceilings sit inside the floor above, and that is invisible until someone walks
     * into it.</p>
     */
    private static DataResult<DungeonGenerationConfig> validateBudget(DungeonGenerationConfig config) {
        int budget = config.ceilingBudget();
        for (RoomHeightBand band : config.roomHeightBands()) {
            if (band.minHeight() > budget) {
                return DataResult.error(() -> "sink_offset " + config.sinkOffset() + " leaves a"
                        + " ceiling budget of " + budget + " (floor_height " + config.floorHeight()
                        + " - sink_offset), but a roomHeightBand asks for rooms at least "
                        + band.minHeight() + " high. sink_offset is bought from the room's headroom,"
                        + " not from the descent -- raise floor_height by as much as you sink, or"
                        + " lower the band's min_height");
            }
        }
        return DataResult.success(config);
    }
}
