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
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mod.gottsch.forge.dungeons2.core.config.platform.CentrePlatformLayout;
import mod.gottsch.forge.dungeons2.core.config.platform.PlatformLayoutPattern;
import mod.gottsch.forge.dungeons2.core.config.platform.PlatformLayoutRegistry;

/**
 * A {@link RoomScheme}'s {@code platforms} slot: <strong>raised daises standing on the room's
 * floor</strong>, optionally carrying something on top.
 *
 * <h2>Why this is not part of floor generation</h2>
 * <p>A dais is a raised <em>area</em>, so the obvious implementation is to give the floor a Y offset
 * over some cells. It is deliberately not built that way. Drawing it after everything else &mdash;
 * the same place {@link PillarPatternEntry} runs &mdash; means the whole feature is blocks placed in
 * the row above the finished floor, in interior air {@code RoomVolumeGenerator} has already cleared.
 * No floor-height plumbing, no interaction with floor patterns, and a dais composes with a decorated
 * floor instead of replacing part of it.</p>
 *
 * <h2>Why a brazier is not a slot of its own</h2>
 * <p>A lone brazier standing on a bare floor reads as an object someone dropped, not as
 * architecture. It wants a platform under it, which is why the two are one entry: {@code top_block}
 * is placed on the dais's centre, one row up. Authoring them separately would make the wrong thing
 * the easy thing.</p>
 *
 * <h2>Geometry</h2>
 * <pre>
 *   B s B      B  corner / fill block   (floorY + 1)
 *   s C s      s  stair, solid half toward the centre -- a step up
 *   B s B      C  centre block, carrying topBlock at floorY + 2
 * </pre>
 *
 * <p>{@code size} is the dais's side and must be <strong>odd</strong>, so it has a true centre cell
 * for {@code top_block} to stand on. The outer ring's straight runs are stairs and its corners are
 * full blocks, which generalises: at size 5 the ring is stairs with block corners and the 3&times;3
 * inside it is solid.</p>
 *
 * <p><strong>A dais needs a room bigger than itself.</strong> A 3&times;3 dais in a 5-wide room
 * would be the entire interior and would sit across the doorways, so it is dropped there &mdash;
 * see {@code BasicPlatformGenerator}. In practice that makes 7 wide the smallest room this can
 * dress.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record PlatformPatternEntry(List<PlatformEntry> patterns, SizeGate gate) {

    /** See {@link PlatformEntry#withRoles}. Returns {@code this} when no dais named a role. */
    public PlatformPatternEntry withRoles(java.util.function.UnaryOperator<String> resolver) {
        List<PlatformEntry> resolved = null;
        for (int i = 0; i < patterns.size(); i++) {
            PlatformEntry entry = patterns.get(i);
            PlatformEntry mapped = entry.withRoles(resolver);
            if (mapped == entry) {
                if (resolved != null) {
                    resolved.add(entry);
                }
                continue;
            }
            if (resolved == null) {
                resolved = new java.util.ArrayList<>(patterns.subList(0, i));
            }
            resolved.add(mapped);
        }
        return resolved == null ? this : new PlatformPatternEntry(List.copyOf(resolved), gate);
    }

    /** An ungated treatment -- drawn whenever its scheme is rolled. */
    public PlatformPatternEntry(List<PlatformEntry> patterns) {
        this(patterns, SizeGate.UNBOUNDED);
    }

    /** A raised platform with stepped sides. */
    public static final String DAIS = "dais";

    /** The default dais side. Three is the smallest that has a centre and a step on every side. */
    public static final int DEFAULT_SIZE = 3;

    /**
     * One platform. {@code layout} says <em>where</em> the daises go and reuses the same layout
     * vocabulary the {@code pillars} slot uses &mdash; {@code centre}, {@code corners},
     * {@code grid}, {@code quartet}, {@code colonnade}. Splitting "where" from "what" is why a
     * brazier in every corner and a brazier on a central platform are one feature and not two.
     *
     * <p>{@code orient} controls which way the step faces, and defaults to {@link
     * SurfaceOrient#INWARD} &mdash; the solid half of the stair toward the dais centre, so the low
     * edge faces the room and a player walks up. <strong>{@code dungeonblocks}' directional trim is
     * modelled facing-inverted relative to vanilla</strong>, so the same visual result needs
     * {@code outward} there; that is the same trap wall courses and ceiling rings both carry, and it
     * is not derivable at runtime.</p>
     */
    public record PlatformEntry(String type, PlatformLayoutPattern layout, String block,
                                Optional<String> stairBlock, Optional<String> centreBlock,
                                Optional<String> topBlock, int size,
                                SurfaceOrient orient, Map<String, String> properties,
                                Optional<Map<String, String>> topProperties,
                                SizeGate gate) {

        /**
         * See {@code FloorPattern#withRoles}. All four of the dais's block fields, so a role on
         * {@code top_block} -- the brazier the shipped {@code brazier_corners_hall} stands there --
         * resolves like the dais under it.
         */
        public PlatformEntry withRoles(java.util.function.UnaryOperator<String> resolver) {
            String resolvedBlock = Codecs.resolveRole(block, resolver);
            Optional<String> resolvedStair = Codecs.resolveRole(stairBlock, resolver);
            Optional<String> resolvedCentre = Codecs.resolveRole(centreBlock, resolver);
            Optional<String> resolvedTop = Codecs.resolveRole(topBlock, resolver);
            if (resolvedBlock.equals(block) && resolvedStair.equals(stairBlock)
                    && resolvedCentre.equals(centreBlock) && resolvedTop.equals(topBlock)) {
                return this;
            }
            return new PlatformEntry(type, layout, resolvedBlock, resolvedStair, resolvedCentre,
                    resolvedTop, size, orient, properties, topProperties, gate);
        }

        /** A plain ungated dais of one block at the room's centre. */
        public PlatformEntry(String block) {
            this(DAIS, new CentrePlatformLayout(), block, Optional.empty(), Optional.empty(),
                    Optional.empty(), DEFAULT_SIZE, SurfaceOrient.INWARD, Map.of(),
                    Optional.empty(), SizeGate.UNBOUNDED);
        }

        /** The stair block, falling back to {@link #block} when unauthored. */
        public String stairBlockOrBase() {
            return stairBlock.orElse(block);
        }

        /** The centre block, falling back to {@link #block} when unauthored. */
        public String centreBlockOrBase() {
            return centreBlock.orElse(block);
        }

        /** The properties for whatever stands on top, falling back to {@link #properties}. */
        public Map<String, String> topPropertiesOrBase() {
            return topProperties.orElse(properties);
        }

        /** Whether this entry is the dais type, compared the way the selector dispatches. */
        public boolean isDais() {
            return DAIS.equals(type().trim().toLowerCase(Locale.ROOT));
        }

        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<PlatformEntry> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(PlatformEntry::type),
                // `layout` + `config`, dispatched over the platform layout registry. An
                // unregistered id is a LOAD ERROR, not a skipped platform.
                PlatformLayoutRegistry.MAP_CODEC.forGetter(PlatformEntry::layout),
                Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(PlatformEntry::block),
                Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "stair_block").forGetter(PlatformEntry::stairBlock),
                Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "centre_block").forGetter(PlatformEntry::centreBlock),
                Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "top_block").forGetter(PlatformEntry::topBlock),
                // Odd only, and enforced in validate(): an even dais has no centre cell, so
                // topBlock would have nowhere defensible to stand.
                Codecs.strictOptionalFieldOf(Codec.intRange(1, 15), "size", DEFAULT_SIZE)
                        .forGetter(PlatformEntry::size),
                Codecs.strictOptionalFieldOf(SurfaceOrient.CODEC, "orient", SurfaceOrient.INWARD)
                        .forGetter(PlatformEntry::orient),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "properties", Map.of()).forGetter(PlatformEntry::properties),
                Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                        "top_properties").forGetter(PlatformEntry::topProperties),
                SizeGate.MAP_CODEC.forGetter(PlatformEntry::gate)
        ).apply(instance, PlatformEntry::new)));
    }

    // Codecs.closed -- see RoomScheme.CODEC.
        /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<PlatformPatternEntry> MAP_CODEC =
            RecordCodecBuilder.<PlatformPatternEntry>mapCodec(instance -> instance.group(
                    PlatformEntry.CODEC.listOf().fieldOf("patterns")
                            .forGetter(PlatformPatternEntry::patterns),
                    SizeGate.MAP_CODEC.forGetter(PlatformPatternEntry::gate)
            ).apply(instance, PlatformPatternEntry::new)).flatXmap(PlatformPatternEntry::validate, PlatformPatternEntry::validate);

    public static final Codec<PlatformPatternEntry> CODEC = Codecs.closed(MAP_CODEC);

    private static DataResult<PlatformPatternEntry> validate(PlatformPatternEntry entry) {
        for (PlatformEntry pattern : entry.patterns()) {
            // `type` is the OTHER discriminator -- what the platform is, as opposed to where the
            // copies go, which is `layout` and is now registry-dispatched. Only `dais` exists, and
            // an unrecognized value used to make the selector drop the whole platform silently:
            // the room simply came out flat, with nothing logged and nothing to grep for. It is
            // the same silent-degradation class the layout registry's load error closes, so it is
            // closed here too rather than left as the last one standing.
            if (!pattern.isDais()) {
                return DataResult.error(() -> "unknown platform type '" + pattern.type()
                        + "'. The only type is '" + DAIS + "'; did you mean to set `layout`?");
            }
            // An even dais has no centre cell. Rejecting it rather than rounding keeps topBlock's
            // position meaningful, and a silently off-centre brazier is exactly the sort of thing
            // nobody notices until they are standing in the room.
            if (pattern.size() % 2 == 0) {
                return DataResult.error(() -> "platform '" + pattern.type() + "': size "
                        + pattern.size() + " is even, so the dais has no centre cell for its top"
                        + " block to stand on -- use an odd size");
            }
            if (pattern.topBlock().isPresent() && pattern.size() < 1) {
                return DataResult.error(() -> "platform '" + pattern.type()
                        + "': a top_block needs a dais to stand on");
            }
            DataResult<SizeGate> gate = pattern.gate()
                    .validate("platform '" + pattern.type() + "'");
            if (gate.error().isPresent()) {
                return DataResult.error(() -> gate.error().orElseThrow().message());
            }
        }
        return DataResult.success(entry);
    }

    /** See {@code PillarPatternEntry#forRoom}. */
    public PlatformPatternEntry forRoom(int width, int depth, int height) {
        List<PlatformEntry> fitting = new ArrayList<>(patterns.size());
        for (PlatformEntry pattern : patterns) {
            if (pattern.gate().fits(width, depth, height)) {
                fitting.add(pattern);
            }
        }
        return fitting.size() == patterns.size() ? this : new PlatformPatternEntry(fitting, gate);
    }
}
