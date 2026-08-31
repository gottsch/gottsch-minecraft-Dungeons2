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

import java.util.List;

/**
 * The Mining Chest's payout table &mdash; backlog #7.
 *
 * <p>Entries live at {@code data/dungeons2/dungeons2/mining_config/<name>.json}; one shipped entry,
 * {@code default}, looked up via {@link MiningConfigHelper#get}.</p>
 *
 * <h2>Estimated, not measured</h2>
 * <p>The premise of #7 is that carving a dungeon out of the stone destroys ore, and a big dungeon
 * should not leave the player worse off for having found it. The obvious implementation &mdash;
 * sample the world before each block write and tally what was really there &mdash; is the one place
 * this cannot live: {@code postProcess} re-runs once per chunk with placements clipped to that
 * chunk's box, so a counter incremented at write time gets partial and repeated counts in whatever
 * order chunks happen to generate, and the chest could not be filled until every contributing chunk
 * had run.</p>
 *
 * <p>So the haul is <strong>estimated</strong> from what the planner already knows: excavated volume
 * per floor, each floor's world Y, and the table below. That loses "this dungeon really ate three
 * diamonds" and keeps everything else &mdash; determinism, no cross-piece state, no dependence on
 * chunk order, and the self-scaling property that made the idea worth having. A deeper or larger
 * dungeon excavates more and is paid more, with the right ore mix for its depth, for free.</p>
 *
 * <h2>The table is a set of bands, and an ore may have several</h2>
 * <p>{@link OreBand} is flat on purpose: one item, one rate, one Y range. Vanilla's real
 * distributions are not flat &mdash; diamond climbs steeply toward the world floor &mdash; and the
 * way to express that here is <strong>several bands for the same item</strong>, which are summed.
 * That keeps the codec simple and puts the shape of the curve in the datapack where an author can
 * see and tune it, rather than in a Java function they cannot.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
public record MiningConfig(double payoutFraction, double depthBias, List<OreBand> ores) {

    /**
     * How much of the estimated haul the chest actually hands back.
     *
     * <p>Shipped at 0.5 &mdash; half the estimate. Mark's call (2026-08-31): the Mining Chest
     * <strong>must not be better than the boss chest</strong>; it is a nice bonus, not the prize.
     * Three things keep it there and only one of them is this number.</p>
     *
     * <ol>
     *   <li><strong>It is a different KIND of reward.</strong> A spoil pile is bulk material &mdash;
     *       coal, copper, iron, a little gold. It has no enchanted book, no diamond gear, no golden
     *       apple, and it never will, because those are not things a mining crew dug out of a wall.
     *       {@code classic_hoard} wins on kind before any quantity is compared, which is the part of
     *       this that cannot drift when someone retunes a number.</li>
     *   <li><strong>The caps.</strong> {@link OreBand#max} bounds each band, so the tail of the
     *       distribution &mdash; a LARGE dungeon on the world floor &mdash; cannot run away. That
     *       matters more than the fraction: excavation scales without bound and nothing else here
     *       does.</li>
     *   <li><strong>This fraction</strong>, the global dial, kept so the whole feature can be turned
     *       down (or off, at 0) without editing every band.</li>
     * </ol>
     */
    public static final double DEFAULT_PAYOUT_FRACTION = 0.5D;

    /**
     * How hard the chest's placement is weighted toward the bottom of the dungeon.
     *
     * <p>A floor's weight is {@code (floorIndex + 1) ^ depthBias}, so at the shipped 3.0 a four-floor
     * dungeon weights its floors 1 : 8 : 27 : 64 &mdash; the bottom floor takes 64%, floor 0 takes
     * 1%. Mark's call: "in a 4 level dungeon, the player shouldn't get a big Mining Chest on level
     * 1." The haul is the <em>whole dungeon's</em> excavation wherever it lands, so a chest found on
     * floor 0 would pay out the deep floors' diamonds without the player having gone down for them.
     *
     * <p>0 makes every floor equally likely; higher values concentrate it further down. Kept a
     * continuous exponent rather than a "bottom floor only" flag so a two-floor dungeon still has
     * some spread &mdash; a guaranteed location is a solved dungeon.</p>
     */
    public static final double DEFAULT_DEPTH_BIAS = 3.0D;

    /**
     * One item, its rate, and the Y range it is found in. Rate is <strong>blocks of ore per 1000
     * blocks of stone excavated</strong> within that range.
     *
     * <p>Per-thousand rather than per-chunk because excavated volume is what this feature has in
     * hand; a chunk is not a unit any part of the estimate speaks in. The numbers shipped are
     * derived from vanilla's own densities &mdash; see the shipped {@code default.json}, which shows
     * its working.</p>
     *
     * @param item the item the ore yields, not the ore block: a dungeon that ate a diamond ore pays
     *             back a <em>diamond</em>, because the spoil pile is what the builders carried out,
     *             not what they left in the wall. Fortune and silk touch do not enter into it.
     * @param max  the cap on this band's contribution, applied before the bands for one item are
     *             summed. The safety valve on the top end: excavation scales with dungeon size and
     *             depth without bound, and nothing else here does.
     */
    public record OreBand(String item, double perThousand, int minY, int maxY, int max) {

        public static final Codec<OreBand> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("item").forGetter(OreBand::item),
                Codec.doubleRange(0.0D, 1000.0D).fieldOf("perThousand").forGetter(OreBand::perThousand),
                Codec.intRange(-2032, 2031).fieldOf("minY").forGetter(OreBand::minY),
                Codec.intRange(-2032, 2031).fieldOf("maxY").forGetter(OreBand::maxY),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "max", Integer.MAX_VALUE)
                        .forGetter(OreBand::max)
        ).apply(instance, OreBand::new)));

        public static final Codec<List<OreBand>> LIST_CODEC = CODEC.listOf();

        /**
         * How much of {@code [floorY, floorY + height)} this band covers, as a fraction of that
         * height.
         *
         * <p>A floor is not a single Y and its ore mix should not be read off one. A floor 20 blocks
         * tall straddling Y 16 is half in diamond country and half out of it, and taking the floor's
         * own Y would make it all one or all the other &mdash; a step change at the boundary, which
         * is exactly the artefact a player would notice as "the seed decides everything".</p>
         *
         * <p>Both ranges are treated as half-open at the top, so adjacent bands written
         * {@code [-64,0]} and {@code [0,16]} do not double-count Y 0. That is the one arithmetic
         * detail an author has to know, and it is why the shipped file's bands abut rather than
         * overlap.</p>
         */
        public double overlapFraction(int floorY, int height) {
            if (height <= 0) {
                return 0.0D;
            }
            int lo = Math.max(floorY, minY);
            int hi = Math.min(floorY + height, maxY + 1);
            return hi <= lo ? 0.0D : (double) (hi - lo) / height;
        }
    }

    // Codecs.closed + strictOptionalFieldOf -- see RoomScheme.CODEC. A misspelled band key here
    // would be a silently smaller payout, which is precisely the class of failure nobody reports.
    public static final Codec<MiningConfig> CODEC = Codecs.closed(
            Codecs.documented(RecordCodecBuilder.<MiningConfig>mapCodec(instance -> instance.group(
                    // 0 is in range and turns the feature off without deleting the file, the same
                    // way roomTemplateAttemptsPerFloor 0 turns prefab rooms off. Above 1 is allowed:
                    // "pay back more than was destroyed" is a defensible dial for a reward chest,
                    // and 4 is far enough out to catch a typo'd 10.
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 4.0D), "payoutFraction",
                            DEFAULT_PAYOUT_FRACTION).forGetter(MiningConfig::payoutFraction),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 8.0D), "depthBias",
                            DEFAULT_DEPTH_BIAS).forGetter(MiningConfig::depthBias),
                    // REQUIRED. An absent table is not "the default table" -- it is a chest with
                    // nothing in it, and this feature exists to not do that.
                    OreBand.LIST_CODEC.fieldOf("ores").forGetter(MiningConfig::ores)
            ).apply(instance, MiningConfig::new)).flatXmap(MiningConfig::validate, MiningConfig::validate),
            "_comment", "//ores"));

    /**
     * The one thing no field range can catch: a band whose range is empty.
     *
     * <p>{@code minY} above {@code maxY} contributes nothing at any depth, so the ore it names
     * silently never appears. Left to a range check it would look exactly like a correctly authored
     * band that happens to sit outside the dungeon's depths, which is a legitimate thing to author.
     */
    private static DataResult<MiningConfig> validate(MiningConfig config) {
        for (OreBand band : config.ores()) {
            if (band.minY() > band.maxY()) {
                return DataResult.error(() -> "mining_config band for '" + band.item()
                        + "' has minY " + band.minY() + " above maxY " + band.maxY()
                        + ", so it can never contribute; swap them or delete the band");
            }
        }
        return DataResult.success(config);
    }

    /** Used when no datapack entry is present, so callers never deal with null. */
    public static final MiningConfig DEFAULT =
            new MiningConfig(DEFAULT_PAYOUT_FRACTION, DEFAULT_DEPTH_BIAS, List.of());
}
