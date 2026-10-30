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

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Every element slot a {@link RoomScheme} can fill, in one record.
 *
 * <h2>Why this exists: DFU's arity ceiling, a second time</h2>
 * <p>{@code RecordCodecBuilder.group} is overloaded once per arity and stops at
 * {@code Products.P16}. {@code RoomScheme} has hit that wall three times now. The first two were
 * paid off by folding fields that were <em>already</em> a thing with a name &mdash;
 * {@link SizeGate} (the four size bounds) and {@link FloorRange} (the depth bounds). The
 * {@code props} slot (#73) took it back to exactly 16, and the {@code partition} slot (#74) would
 * not have compiled at all.</p>
 *
 * <p>So this fold is the structural one rather than another two-field rescue: <strong>ten of those
 * sixteen arguments were element slots</strong>, and they are one thing. {@code RoomScheme} keeps
 * {@code name}, {@code weight}, its {@code gate}, its {@code floors}, {@code extends} and
 * {@code abstract} &mdash; six group arguments plus this one, which is seven. A new slot now costs a
 * field <em>here</em>, and this group is at thirteen since {@code hangings}, so there is room for
 * three more before the same wall is reached again.</p>
 *
 * <h2>The JSON did not move</h2>
 * <p>{@link #MAP_CODEC} is a {@code MapCodec}, so every key stays <strong>flat on the scheme
 * object</strong> exactly as they were &mdash; there is no {@code "slots": { ... }} wrapper, and a
 * datapack sees nothing. That is the same property that made the {@code SizeGate} and
 * {@code FloorRange} folds invisible, and it is the whole reason a {@code MapCodec} is the tool for
 * this rather than a nested {@code Codec}.</p>
 *
 * <p>{@code RoomScheme} keeps a delegating accessor for every slot ({@code scheme.floor()},
 * {@code scheme.pots()}, …), so no caller had to learn about this record either.</p>
 *
 * <h2>Declaration order is load-bearing</h2>
 * <p>{@link #resolve} draws each slot's alternatives in the order the fields are written here, out
 * of one {@link RandomSource}. Reordering them, or inserting a new slot anywhere but the end, shifts
 * the random stream for every room that rolls an option list &mdash; which silently regenerates
 * existing worlds. <strong>Append; never insert.</strong></p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record RoomSlots(SlotOptions<FloorPatternEntry> floor,
                        SlotOptions<WallPatternEntry> wall,
                        SlotOptions<CeilingPatternEntry> ceiling,
                        SlotOptions<PotConfig> pots,
                        SlotOptions<PillarPatternEntry> pillars,
                        SlotOptions<PlatformPatternEntry> platforms,
                        SlotOptions<SpawnerConfig> spawners,
                        SlotOptions<ChestConfig> chests,
                        SlotOptions<PitPatternEntry> pit,
                        SlotOptions<PropConfig> props,
                        SlotOptions<PartitionPatternEntry> partition,
                        SlotOptions<TombConfig> tombs,
                        SlotOptions<HangingConfig> hangings) {

    /** Every slot empty: the deliberately undecorated room. */
    public static final RoomSlots EMPTY = new RoomSlots(
            SlotOptions.empty(), SlotOptions.empty(), SlotOptions.empty(), SlotOptions.empty(),
            SlotOptions.empty(), SlotOptions.empty(), SlotOptions.empty(), SlotOptions.empty(),
            SlotOptions.empty(), SlotOptions.empty(), SlotOptions.empty(), SlotOptions.empty(),
            SlotOptions.empty());

    /** The shape before the {@code hangings} slot: nothing hung from the ceiling. */
    public RoomSlots(SlotOptions<FloorPatternEntry> floor, SlotOptions<WallPatternEntry> wall,
                     SlotOptions<CeilingPatternEntry> ceiling, SlotOptions<PotConfig> pots,
                     SlotOptions<PillarPatternEntry> pillars,
                     SlotOptions<PlatformPatternEntry> platforms,
                     SlotOptions<SpawnerConfig> spawners, SlotOptions<ChestConfig> chests,
                     SlotOptions<PitPatternEntry> pit, SlotOptions<PropConfig> props,
                     SlotOptions<PartitionPatternEntry> partition, SlotOptions<TombConfig> tombs) {
        this(floor, wall, ceiling, pots, pillars, platforms, spawners, chests, pit, props,
                partition, tombs, SlotOptions.empty());
    }

    /** The shape before the {@code tombs} slot (#104): a room with nobody buried in it. */
    public RoomSlots(SlotOptions<FloorPatternEntry> floor, SlotOptions<WallPatternEntry> wall,
                     SlotOptions<CeilingPatternEntry> ceiling, SlotOptions<PotConfig> pots,
                     SlotOptions<PillarPatternEntry> pillars,
                     SlotOptions<PlatformPatternEntry> platforms,
                     SlotOptions<SpawnerConfig> spawners, SlotOptions<ChestConfig> chests,
                     SlotOptions<PitPatternEntry> pit, SlotOptions<PropConfig> props,
                     SlotOptions<PartitionPatternEntry> partition) {
        this(floor, wall, ceiling, pots, pillars, platforms, spawners, chests, pit, props,
                partition, SlotOptions.empty());
    }

    /**
     * All thirteen slot keys, flat on the enclosing object. See the class javadoc on why the order of
     * these lines matters and why a new slot is appended rather than inserted.
     */
    public static final MapCodec<RoomSlots> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
            SlotOptions.field(FloorPatternEntry.MAP_CODEC, "floor").forGetter(RoomSlots::floor),
            SlotOptions.field(WallPatternEntry.MAP_CODEC, "wall").forGetter(RoomSlots::wall),
            SlotOptions.field(CeilingPatternEntry.MAP_CODEC, "ceiling").forGetter(RoomSlots::ceiling),
            SlotOptions.field(PotConfig.MAP_CODEC, "pots").forGetter(RoomSlots::pots),
            SlotOptions.field(PillarPatternEntry.MAP_CODEC, "pillars").forGetter(RoomSlots::pillars),
            SlotOptions.field(PlatformPatternEntry.MAP_CODEC, "platforms").forGetter(RoomSlots::platforms),
            SlotOptions.field(SpawnerConfig.MAP_CODEC, "spawners").forGetter(RoomSlots::spawners),
            SlotOptions.field(ChestConfig.MAP_CODEC, "chests").forGetter(RoomSlots::chests),
            // #3. Sits beside the surface slots rather than inside `floor` because it is not a
            // paving pattern: it changes the room's GEOMETRY, and it is bounded by the floor's
            // sink_offset budget rather than by anything a floor pattern knows.
            SlotOptions.field(PitPatternEntry.MAP_CODEC, "pit").forGetter(RoomSlots::pit),
            // #73.
            SlotOptions.field(PropConfig.MAP_CODEC, "props").forGetter(RoomSlots::props),
            // #74. The first slot that changes the SHAPE of the space a player moves through
            // rather than the surfaces around it; see PartitionPatternEntry.
            SlotOptions.field(PartitionPatternEntry.MAP_CODEC, "partition")
                    .forGetter(RoomSlots::partition),
            // #104. LAST, per the class note: appended, never inserted, so no existing scheme's
            // option draws move.
            SlotOptions.field(TombConfig.MAP_CODEC, "tombs").forGetter(RoomSlots::tombs),
            // LAST, for the same reason.
            SlotOptions.field(HangingConfig.MAP_CODEC, "hangings").forGetter(RoomSlots::hangings)
    ).apply(instance, RoomSlots::new));

    /**
     * These slots with every <strong>unfilled</strong> one taken from {@code parent}. A slot the
     * child fills replaces the parent's wholesale, with no merging of the lists inside it &mdash;
     * see {@link RoomScheme#inheritFrom}, which is where that decision is argued.
     */
    public RoomSlots inheritFrom(RoomSlots parent) {
        return new RoomSlots(
                floor.orElse(parent.floor()),
                wall.orElse(parent.wall()),
                ceiling.orElse(parent.ceiling()),
                pots.orElse(parent.pots()),
                pillars.orElse(parent.pillars()),
                platforms.orElse(parent.platforms()),
                spawners.orElse(parent.spawners()),
                chests.orElse(parent.chests()),
                pit.orElse(parent.pit()),
                props.orElse(parent.props()),
                partition.orElse(parent.partition()),
                tombs.orElse(parent.tombs()),
                hangings.orElse(parent.hangings()));
    }

    /**
     * These slots with every {@code $role} replaced by the literal the palette in scope names,
     * or <strong>{@code this}</strong> when nothing named a role.
     *
     * <p>The identity return is not an optimisation detail: this runs per piece, so the path an
     * unconverted motif takes has to allocate nothing. See {@link RoomScheme#withRoles} for the
     * two jobs one walk is doing, and for which slots read a role at all &mdash; {@code pots} and
     * {@code spawners} name entities, mob sets and loot tables rather than blocks, so there is
     * nothing in either for a palette to answer.</p>
     */
    public RoomSlots withRoles(UnaryOperator<String> resolver) {
        SlotOptions<PillarPatternEntry> newPillars = pillars.map(entry -> entry.withRoles(resolver));
        SlotOptions<FloorPatternEntry> newFloor = floor.map(entry -> entry.withRoles(resolver));
        SlotOptions<CeilingPatternEntry> newCeiling = ceiling.map(entry -> entry.withRoles(resolver));
        SlotOptions<PlatformPatternEntry> newPlatforms = platforms.map(entry -> entry.withRoles(resolver));
        SlotOptions<WallPatternEntry> newWall = wall.map(entry -> entry.withRoles(resolver));
        SlotOptions<PitPatternEntry> newPit = pit.map(entry -> entry.withRoles(resolver));
        SlotOptions<ChestConfig> newChests = chests.map(entry -> entry.withRoles(resolver));
        SlotOptions<PropConfig> newProps = props.map(entry -> entry.withRoles(resolver));
        SlotOptions<PartitionPatternEntry> newPartition =
                partition.map(entry -> entry.withRoles(resolver));
        SlotOptions<TombConfig> newTombs = tombs.map(entry -> entry.withRoles(resolver));
        SlotOptions<HangingConfig> newHangings = hangings.map(entry -> entry.withRoles(resolver));
        if (newPillars == pillars && newFloor == floor && newCeiling == ceiling
                && newPlatforms == platforms && newWall == wall && newPit == pit
                && newChests == chests && newProps == props && newPartition == partition
                && newTombs == tombs && newHangings == hangings) {
            return this;
        }
        return new RoomSlots(newFloor, newWall, newCeiling, pots, newPillars, newPlatforms,
                spawners, newChests, newPit, newProps, newPartition, newTombs, newHangings);
    }

    /**
     * These slots with each one's alternatives collapsed to the treatment this room gets.
     *
     * <p>Slots resolve in <strong>declaration order</strong>, which is what makes the draw
     * reproducible across the repeated {@code postProcess} calls a piece gets per overlapping
     * chunk; a slot holding no option list consumes nothing here at all, so an unconverted motif
     * generates the dungeons it always did. See {@link SlotOptions#resolve}.</p>
     */
    public RoomSlots resolve(int width, int depth, int height, RandomSource random) {
        return new RoomSlots(
                floor.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                wall.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                ceiling.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                pots.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                pillars.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                platforms.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                spawners.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                chests.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                pit.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                props.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                partition.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                tombs.resolve(random, entry -> entry.gate().fits(width, depth, height)),
                hangings.resolve(random, entry -> entry.gate().fits(width, depth, height)));
    }

    /**
     * The {@code featuredMinSize} that switches the big-room rule off, since no room is that big.
     * What every caller that does not pass the generation config's value gets, so tests and probes
     * written before the rule see the authored roll alone.
     */
    public static final int NO_FEATURED_MINIMUM = Integer.MAX_VALUE;

    /**
     * {@link #resolve(int, int, int, RandomSource)}, and then the one rule the authored weights
     * cannot express on their own: <strong>a big room may not be bare</strong> (Mark, 2026-10-30).
     *
     * <h2>Why a rule and not more weight</h2>
     * <p>A bare room is a fine result in a small room and a bad one in a big room. The height taper
     * (#51) makes every big procedural room LOW &mdash; 5 to 7 high once its long side passes 15
     * &mdash; and a 17x17 room 5 high with a flat ceiling and nothing standing in it is a lot of
     * floor to cross for nothing; the player heads straight for the nearest door. Each slot's
     * {@code none} weight is chosen for the rooms the scheme mostly dresses, which are small, and
     * a {@code max_size} on a {@code none} option could only fix one slot at a time, where what is
     * wanted is "a ceiling, or pillars, or a pit": any one of them makes the room worth crossing.</p>
     *
     * <h2>The rule</h2>
     * <p>A room whose shorter side is at least {@code featuredMinSize} (the same measure as
     * {@code min_size}) must draw at least one of the slots that change the room's SHAPE:
     * {@code ceiling}, {@code pillars}, {@code platforms}, {@code pit} or {@code partition}. Floor
     * and wall patterns, pots, props, spawners, chests, tombs and hangings do not count &mdash; a
     * patterned floor under a flat ceiling is still an empty box, and so is one with a chandelier
     * in it.</p>
     *
     * <p>When the authored roll drew none of them, the first of those slots, in declaration order
     * with {@code ceiling} first, that has a treatment fitting this room is rolled again with its
     * {@code none} options removed. Ceiling first because it is the treatment that never gets in
     * the way of anything else in the room, and because a big room is always low and so always has
     * rise budget to spare for a vault. A scheme with no fitting treatment in any of the five is
     * left bare; {@code FeaturedRoomRuleTest} checks no shipped scheme is one, and that every shipped
     * scheme a big room can roll has a ceiling for it.</p>
     *
     * <h2>What it costs the random stream</h2>
     * <p>Nothing, except in a room the rule changes. The authored roll draws exactly what it
     * always drew, and the re-roll happens after it and only in a big bare room, which consumes at
     * most one further value &mdash; and that room is generated differently anyway, which is the
     * point. Small rooms, and big rooms the authored roll already dressed, are byte-identical.</p>
     */
    public RoomSlots resolve(int width, int depth, int height, RandomSource random,
                             int featuredMinSize) {
        RoomSlots resolved = resolve(width, depth, height, random);
        if (Math.min(width, depth) < featuredMinSize || resolved.shapesRoom()) {
            return resolved;
        }
        return resolved.featured(this, width, depth, height, random);
    }

    /**
     * Whether a RESOLVED set of slots draws anything that changes the room's shape. See
     * {@link #resolve(int, int, int, RandomSource, int)} for which slots those are and why.
     */
    public boolean shapesRoom() {
        return ceiling.isPresent() || pillars.isPresent() || platforms.isPresent()
                || pit.isPresent() || partition.isPresent();
    }

    /**
     * Whether {@link #featured} can find something for a room of these dimensions: some shape slot
     * has a treatment, not a {@code none}, that fits. Asked of AUTHORED slots.
     */
    public boolean canShape(int width, int depth, int height) {
        return anyOptionFits(ceiling, CeilingPatternEntry::gate, width, depth, height)
                || anyOptionFits(pillars, PillarPatternEntry::gate, width, depth, height)
                || anyOptionFits(platforms, PlatformPatternEntry::gate, width, depth, height)
                || anyOptionFits(pit, PitPatternEntry::gate, width, depth, height)
                || anyOptionFits(partition, PartitionPatternEntry::gate, width, depth, height);
    }

    /**
     * These resolved (and bare) slots with the first shape slot that can draw here re-rolled from
     * {@code authored} without its {@code none} options. Only one slot is forced: the rule asks for
     * a room worth crossing, not for every treatment at once.
     */
    RoomSlots featured(RoomSlots authored, int width, int depth, int height, RandomSource random) {
        if (anyOptionFits(authored.ceiling, CeilingPatternEntry::gate, width, depth, height)) {
            return withCeiling(authored.ceiling.withoutNone()
                    .resolve(random, entry -> entry.gate().fits(width, depth, height)));
        }
        if (anyOptionFits(authored.pillars, PillarPatternEntry::gate, width, depth, height)) {
            return new RoomSlots(floor, wall, ceiling, pots, authored.pillars.withoutNone()
                    .resolve(random, entry -> entry.gate().fits(width, depth, height)),
                    platforms, spawners, chests, pit, props, partition, tombs, hangings);
        }
        if (anyOptionFits(authored.platforms, PlatformPatternEntry::gate, width, depth, height)) {
            return new RoomSlots(floor, wall, ceiling, pots, pillars, authored.platforms.withoutNone()
                    .resolve(random, entry -> entry.gate().fits(width, depth, height)),
                    spawners, chests, pit, props, partition, tombs, hangings);
        }
        if (anyOptionFits(authored.pit, PitPatternEntry::gate, width, depth, height)) {
            return new RoomSlots(floor, wall, ceiling, pots, pillars, platforms, spawners, chests,
                    authored.pit.withoutNone()
                            .resolve(random, entry -> entry.gate().fits(width, depth, height)),
                    props, partition, tombs, hangings);
        }
        if (anyOptionFits(authored.partition, PartitionPatternEntry::gate, width, depth, height)) {
            return new RoomSlots(floor, wall, ceiling, pots, pillars, platforms, spawners, chests,
                    pit, props, authored.partition.withoutNone()
                            .resolve(random, entry -> entry.gate().fits(width, depth, height)),
                    tombs, hangings);
        }
        return this;
    }

    private RoomSlots withCeiling(SlotOptions<CeilingPatternEntry> newCeiling) {
        return new RoomSlots(floor, wall, newCeiling, pots, pillars, platforms, spawners, chests,
                pit, props, partition, tombs, hangings);
    }

    /**
     * Whether any slot is filled at all. False for the deliberately undecorated room, which is a
     * legitimate authored outcome rather than a mistake &mdash; see {@link RoomScheme#declaresAnySlot}.
     */
    public boolean declaresAny() {
        return !floor.isEmpty() || !wall.isEmpty() || !ceiling.isEmpty() || !pots.isEmpty()
                || !pillars.isEmpty() || !platforms.isEmpty() || !spawners.isEmpty()
                || !chests.isEmpty() || !pit.isEmpty() || !props.isEmpty()
                || !partition.isEmpty() || !tombs.isEmpty() || !hangings.isEmpty();
    }

    /** Whether any slot draws in a room of these dimensions. See {@link RoomScheme#drawsAnything}. */
    public boolean drawsAnything(int width, int depth, int height) {
        return anyOptionFits(floor, FloorPatternEntry::gate, width, depth, height)
                || anyOptionFits(wall, WallPatternEntry::gate, width, depth, height)
                || anyOptionFits(ceiling, CeilingPatternEntry::gate, width, depth, height)
                || anyOptionFits(pots, PotConfig::gate, width, depth, height)
                || anyOptionFits(pillars, PillarPatternEntry::gate, width, depth, height)
                || anyOptionFits(platforms, PlatformPatternEntry::gate, width, depth, height)
                || anyOptionFits(spawners, SpawnerConfig::gate, width, depth, height)
                || anyOptionFits(pit, PitPatternEntry::gate, width, depth, height)
                || anyOptionFits(props, PropConfig::gate, width, depth, height)
                || anyOptionFits(partition, PartitionPatternEntry::gate, width, depth, height)
                || anyOptionFits(tombs, TombConfig::gate, width, depth, height)
                || anyOptionFits(hangings, HangingConfig::gate, width, depth, height);
    }

    private static <T> boolean anyOptionFits(SlotOptions<T> slot, Function<T, SizeGate> gate,
                                             int width, int depth, int height) {
        return slot.all().anyMatch(entry -> gate.apply(entry).fits(width, depth, height));
    }

    /**
     * Validates EVERY authored alternative's {@link SizeGate}, not merely the one a room would roll.
     *
     * <p>An inverted gate on the third of four wall options is a load error the moment it is
     * written, rather than a wall that draws nothing in whichever rooms happen to pick that option
     * &mdash; which is indistinguishable from an option that was merely unlucky.</p>
     *
     * <p>Called from {@code RoomScheme#validate} rather than from inside {@code SizeGate}'s own map
     * codec, because that is the level that knows the scheme's name and which slot it was:
     * "max_height 5 is below min_height 7" is not an actionable error message on its own.</p>
     */
    public DataResult<SizeGate> validate(String schemeName) {
        DataResult<SizeGate> result = DataResult.success(SizeGate.UNBOUNDED);
        result = chain(result, floor.all().map(FloorPatternEntry::gate), schemeName, "floor");
        result = chain(result, wall.all().map(WallPatternEntry::gate), schemeName, "wall");
        result = chain(result, ceiling.all().map(CeilingPatternEntry::gate), schemeName, "ceiling");
        result = chain(result, pots.all().map(PotConfig::gate), schemeName, "pots");
        result = chain(result, pillars.all().map(PillarPatternEntry::gate), schemeName, "pillars");
        result = chain(result, platforms.all().map(PlatformPatternEntry::gate), schemeName, "platforms");
        result = chain(result, spawners.all().map(SpawnerConfig::gate), schemeName, "spawners");
        result = chain(result, props.all().map(PropConfig::gate), schemeName, "props");
        result = chain(result, partition.all().map(PartitionPatternEntry::gate), schemeName,
                "partition");
        result = chain(result, tombs.all().map(TombConfig::gate), schemeName, "tombs");
        result = chain(result, hangings.all().map(HangingConfig::gate), schemeName, "hangings");
        return result;
    }

    private static DataResult<SizeGate> chain(DataResult<SizeGate> soFar, Stream<SizeGate> gates,
                                              String scheme, String slot) {
        return gates.reduce(soFar,
                (result, gate) -> result.flatMap(ignored ->
                        gate.validate("scheme '" + scheme + "', " + slot + " slot")),
                (left, right) -> left.flatMap(ignored -> right));
    }
}
