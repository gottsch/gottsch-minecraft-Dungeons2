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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Turns a fungus growth marker into the mob it stands for.
 *
 * <h2>Why the weathering pass cannot just place the mob</h2>
 * <p>The fungi grow on the dirt the aging rules make out of decayed stone, and <em>only</em> the
 * decoration pass knows where that dirt ended up &mdash; it is produced at render time, long after
 * the room generator has decided anything. But that pass is a {@code StructureProcessor}, and a
 * processor returns block states; there is no point in it where an entity can be created. So
 * {@code floor_growth} emits a marker block, and this class converts it in
 * {@code DungeonPiece#placeAll}, which is the first place that has both the decorated output and a
 * level. The marker never reaches the world on that path.</p>
 *
 * <h2>The yaw is derived from the position, and that is not a detail</h2>
 * <p>A piece's {@code postProcess} runs once per chunk it overlaps and must plan identically every
 * time, or the chunk-box clip in {@code DungeonPiece#placeEntities} stops being a correct
 * exactly-once filter. Drawing the rotation from a shared stream would make the yaw depend on how
 * many cells the pass happened to visit first, which differs per chunk. Seeding from the block
 * position &mdash; the same {@code Mth.getSeed} the decoration processor uses to decide the growth
 * itself &mdash; makes it a pure function of where the fungus is.</p>
 *
 * <h2>These carry no loot table</h2>
 * <p>Unlike the pots, which are entities <em>because</em> they hold loot, a fungus drops whatever
 * its own mob drops when killed. Passing a loot table here would override that.</p>
 *
 * @author Mark Gottschling on Aug 19, 2026
 */
public final class FungusGrowth {

    /**
     * Marker block id &rarr; the entity it becomes.
     *
     * <p>One entry per mob rather than one marker with a payload, so which fungus grows stays a
     * datapack decision &mdash; the markers sit in {@code floor_growth}'s uniform pick beside the
     * mushrooms. {@code FungusGrowthWiringTest} pins this map against the shipped JSON, because a
     * marker in the datapack that is missing here would be placed as a block and left standing.</p>
     */
    private static final Map<String, String> MARKER_TO_ENTITY = Map.of(
            "dungeons2:shrieker_marker", "dungeons2:shrieker",
            "dungeons2:violet_fungus_marker", "dungeons2:violet_fungus");

    /** Keeps the yaw draw off any other position-seeded stream in the pipeline. */
    private static final long YAW_SALT = 0xD2_F0_9A_11L;

    private FungusGrowth() {}

    /** The marker ids this class knows how to convert. Exposed for the wiring test. */
    public static java.util.Set<String> markerIds() {
        return MARKER_TO_ENTITY.keySet();
    }

    /** Whether this state is a fungus marker, i.e. whether it must NOT be written to the world. */
    public static boolean isMarker(BlockState state) {
        return MARKER_TO_ENTITY.containsKey(idOf(state));
    }

    /**
     * By id, which is the form the tests can reach.
     *
     * <p>Forge wraps {@code BuiltInRegistries.BLOCK} in a locked {@code NamespacedWrapper}, so a
     * headless test cannot register this mod's blocks the way {@code TestRegistries} registers its
     * processor types &mdash; there is no way to build a real marker {@code BlockState} outside a
     * running game. Splitting the id lookup out keeps the mapping, the coordinate translation and
     * the yaw derivation all under test, and leaves only the one registry call that resolves a
     * state to its id uncovered.</p>
     */
    public static boolean isMarker(String blockId) {
        // Null-guarded, and not defensively: Map.of() is an ImmutableCollections.MapN, whose
        // containsKey THROWS on null rather than returning false. idOf returns null for a block
        // with no registry key, and this runs inside the piece's placement loop -- so without the
        // guard an unkeyed block state would abort chunk generation.
        return blockId != null && MARKER_TO_ENTITY.containsKey(blockId);
    }

    /**
     * The placement a marker at {@code worldPos} becomes, or {@code null} when the state is not a
     * marker.
     *
     * <p>Returns floor-local XZ with an absolute Y, which is the coordinate space
     * {@link DungeonPiece#placeEntities} expects &mdash; it re-adds the anchor itself. Note it does
     * <strong>not</strong> take the Z mirror {@code placeBlock} needs: that reflection is vanilla's
     * {@code getWorldZ} convention for piece-local block coordinates, and the entity path never goes
     * through it.</p>
     */
    public static EntityPlacement toPlacement(BlockState state, BlockPos worldPos,
                                              int anchorX, int anchorZ) {
        return toPlacement(idOf(state), worldPos, anchorX, anchorZ);
    }

    /** By id; see {@link #isMarker(String)} for why this overload exists. */
    public static EntityPlacement toPlacement(String blockId, BlockPos worldPos,
                                              int anchorX, int anchorZ) {
        String entityId = blockId == null ? null : MARKER_TO_ENTITY.get(blockId);
        if (entityId == null) {
            return null;
        }
        RandomSource random = RandomSource.create(Mth.getSeed(worldPos) ^ YAW_SALT);
        return new EntityPlacement(
                worldPos.getX() - anchorX, worldPos.getY(), worldPos.getZ() - anchorZ,
                entityId, random.nextFloat() * 360.0F, null, 0L);
    }

    /**
     * The block's registry id, or {@code null}.
     *
     * <p>Matched by id rather than by {@code DungeonsBlocks.SHRIEKER_MARKER.get()} on purpose: this
     * runs inside the piece's placement loop, which the headless tests drive through
     * {@code FakeWorldGenLevel} with no Forge {@code DeferredRegister} ever fired. Calling
     * {@code get()} on an unfilled {@code RegistryObject} throws there, and a marker cannot appear
     * in that situation anyway &mdash; an unregistered block never decodes into the growth palette
     * in the first place. Same string-id convention {@code RoomSpawnerGenerator} uses for
     * {@code dungeons2:mob_set_spawner}.</p>
     */
    private static String idOf(BlockState state) {
        if (state == null) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? null : id.toString();
    }
}
