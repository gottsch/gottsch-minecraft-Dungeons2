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
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The {@code props} scheme slot: a room's <strong>furniture</strong> &mdash; barrels, crates, cages,
 * an anvil, a lectern. Backlog #73.
 *
 * <h2>Why this is not the {@code pots} slot</h2>
 * <p>Mechanically it very nearly is: pick interior cells, roll a count, honour the {@code taken}
 * set, draw a weighted variant. Two things make it a slot of its own rather than a second variant
 * list on {@link PotConfig}:</p>
 * <ol>
 *   <li><strong>A prop is a block, a pot is an entity.</strong> Props travel on the ordinary block
 *       channel (like {@link ChestConfig}), so they can claim their cells against everything placed
 *       after them; a pot cannot, because it is spawned rather than written.</li>
 *   <li><strong>Furniture is not scattered.</strong> A pot may stand anywhere on the inner ring and
 *       reads as dropped there; a barrel in the middle of a floor reads as a mistake. That is
 *       {@link PropPlacement}, and it is the field with no counterpart on any other slot.</li>
 * </ol>
 *
 * <h2>No loot here</h2>
 * <p>Deliberately. A container with a loot table is a chest by another name, and the {@code chests}
 * slot (#48) already owns that whole pipeline &mdash; the table, the depth band, the fixed
 * {@code LootTableSeed}. A scheme that wants a lootable barrel authors {@code minecraft:barrel} as a
 * {@code chests} variant, which works today; this slot is for the barrel that is scenery. Keeping
 * them apart is also what stops a store room quietly becoming a treasure room because someone
 * authored six containers.</p>
 *
 * <h2>What a variant may name</h2>
 * <p>Anything that stands on a floor cell. Nothing here can check that a block is a sensible thing
 * to stand on a floor &mdash; a block id is a string to this codec &mdash; so a variant naming a
 * block with gravity, or one that needs a support that is not there, places and then falls. The
 * same limitation, and the same reason, as {@code ChestConfig.ChestVariant}.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record PropConfig(int minCount, int maxCount, PropPlacement placement,
                         List<PropVariant> variants, SizeGate gate) {

    /** Ungated props -- placed whenever the scheme is rolled. */
    public PropConfig(int minCount, int maxCount, PropPlacement placement,
                      List<PropVariant> variants) {
        this(minCount, maxCount, placement, variants, SizeGate.UNBOUNDED);
    }

    /**
     * Where in the room a prop may stand. The one field that makes this slot different in kind from
     * {@code pots}, and the reason it is an enum rather than a provider registry: unlike a floor or
     * a wall pattern there is no geometry to author here &mdash; the set of "places furniture goes"
     * is small, closed, and a property of rooms rather than of a style.
     *
     * <h2>Cells, not shapes</h2>
     * <p>Each value names a candidate <em>set</em>; the count roll then draws from it. So a
     * {@code corner} slot in a room whose corners are all taken places nothing, rather than falling
     * back to the middle of the floor &mdash; the fallback would silently produce exactly the
     * arrangement the author picked {@code corner} to avoid.</p>
     */
    public enum PropPlacement implements StringRepresentable {
        /**
         * The interior ring, backing onto a wall &mdash; barrels, crates, bookshelves, a bench. The
         * default, and the same candidate set the pots slot uses, so this is the placement that
         * needed no new geometry at all.
         */
        AGAINST_WALL("against_wall"),
        /**
         * The four interior corners. For the single deliberate object: an anvil, a cage, a brazier.
         * A room has four of these and no more, so a {@code max_count} above 4 is not an error, it
         * simply cannot be met.
         */
        CORNER("corner"),
        /**
         * Anywhere in the interior. What the pots slot would do if it did not restrict itself to the
         * ring; here it is for the things that genuinely belong out in the floor &mdash; a cage, a
         * pile of crates in a hall too big for its walls to be near anything.
         */
        FREE("free"),
        /**
         * The pair of cells either side of a doorway's approach. Reads as guarding, or as
         * ceremonial, depending on what is authored. The approach cells themselves are excluded
         * everywhere (see {@code RoomInterior#cellsInsideDoorways}), so this is the two cells
         * diagonally beside each door and never the one a player walks through.
         */
        FLANKING_DOOR("flanking_door");

        private final String name;

        PropPlacement(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /**
         * A failing codec rather than a lenient string with a default, for {@code CourseAnchor}'s
         * reason: the set is closed and tiny, so a value outside it is a typo, and silently reading
         * {@code "corners"} as {@code against_wall} would put the room's one anvil against a wall
         * with no error anywhere.
         */
        public static final Codec<PropPlacement> CODEC =
                StringRepresentable.fromEnum(PropPlacement::values);
    }

    /**
     * One weighted prop block. A record rather than a bare id list so a store room can be "mostly
     * barrels, occasionally a crate" without repeating ids &mdash; the same shape as
     * {@code PotConfig.PotVariant} and {@code ChestConfig.ChestVariant}.
     *
     * <h2>{@code oriented}</h2>
     * <p>When true (the default) the generator writes a {@code facing} pointing away from the wall
     * the prop backs onto, so a barrel or a lectern faces into the room rather than at the masonry.
     * A block with no {@code facing} property drops it silently at resolve time
     * ({@code BlockStateCodec#resolve}), so this is <em>not</em> a "does this block have a facing"
     * flag &mdash; iron bars need no opt-out.</p>
     *
     * <p>It exists for the block whose {@code facing} means something other than "the side you look
     * at". A {@code minecraft:barrel} defaults to {@code facing: up}, which is a barrel standing
     * open on the floor; orienting it lays it on its side against the wall. Both are things an
     * author might want, and no default can tell which, so the choice is written down.</p>
     */
    public record PropVariant(String block, int weight, boolean oriented) {

        /** The oriented form, which is the default. */
        public PropVariant(String block, int weight) {
            this(block, weight, true);
        }

        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<PropVariant> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(PropVariant::block),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(PropVariant::weight),
                Codecs.strictOptionalFieldOf(Codec.BOOL, "oriented", true)
                        .forGetter(PropVariant::oriented)
        ).apply(instance, PropVariant::new)));
    }

    /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<PropConfig> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
            // 1/2, not the pots' 1/3. Furniture is bigger than a pot and there are only so many
            // wall cells worth standing something in; a room that reliably gets three barrels reads
            // as a warehouse rather than as a room that happens to have barrels in it.
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "min_count", 1)
                    .forGetter(PropConfig::minCount),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "max_count", 2)
                    .forGetter(PropConfig::maxCount),
            Codecs.strictOptionalFieldOf(PropPlacement.CODEC, "placement", PropPlacement.AGAINST_WALL)
                    .forGetter(PropConfig::placement),
            PropVariant.CODEC.listOf().fieldOf("variants").forGetter(PropConfig::variants),
            SizeGate.MAP_CODEC.forGetter(PropConfig::gate)
    ).apply(instance, PropConfig::new));

    public static final Codec<PropConfig> CODEC = Codecs.closed(MAP_CODEC);

    /**
     * This slot with each variant's block resolved against the palette in scope. #65's second half;
     * {@code placement} names a rule rather than a material, so there is nothing there for a role.
     */
    public PropConfig withRoles(UnaryOperator<String> resolver) {
        List<PropVariant> resolved = null;
        for (int i = 0; i < variants.size(); i++) {
            PropVariant variant = variants.get(i);
            String block = Codecs.resolveRole(variant.block(), resolver);
            if (block.equals(variant.block())) {
                if (resolved != null) {
                    resolved.add(variant);
                }
                continue;
            }
            if (resolved == null) {
                resolved = new ArrayList<>(variants.subList(0, i));
            }
            resolved.add(new PropVariant(block, variant.weight(), variant.oriented()));
        }
        return resolved == null ? this
                : new PropConfig(minCount, maxCount, placement, List.copyOf(resolved), gate);
    }

    /**
     * The inclusive count range, normalised. A {@code max_count} below {@code min_count} is
     * authoring nonsense a codec range cannot express (the bound is another field), so it is clamped
     * here rather than producing an empty or negative range at generation time. Same treatment, and
     * same reasoning, as {@code PotConfig#clampedMaxCount}.
     */
    public int clampedMaxCount() {
        return Math.max(minCount, maxCount);
    }
}
