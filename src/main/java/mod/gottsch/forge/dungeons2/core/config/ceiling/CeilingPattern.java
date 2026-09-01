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
package mod.gottsch.forge.dungeons2.core.config.ceiling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector.Layer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * One authored ceiling treatment: its config, and the ability to add the {@link Layer}s it draws.
 *
 * <h2>These follow the FLOOR model, not the pillar one</h2>
 * <p>A pillar layout is a bare footprint whose blocks have to travel alongside it, so a
 * {@code PillarEntry} keeps its materials. A surface pattern bakes {@link BlockState} into its
 * provider, because a {@code SurfacePlan} carries a state per cell &mdash; so a ceiling pattern
 * absorbs <em>everything</em>, exactly as a floor pattern does, and
 * {@code CeilingPatternEntry.SurfacePatternEntry} is left holding only {@code projection} and its
 * gate.</p>
 *
 * <h2>What the split fixes here</h2>
 * <p>The flat record carried eleven fields for four types with near-disjoint needs: {@code
 * cornerBlock} means nothing to {@code coffers}, {@code bracket_block} nothing to {@code centre},
 * {@code size} nothing to anything but {@code centre}. Every one of those was a silent no-op.</p>
 *
 * <p>It also <strong>deletes a validation rule by construction</strong>. {@code
 * CeilingPatternEntry.validate} rejected an {@code orient} on a type with no direction to face
 * &mdash; a rule that existed only because every type shared one record. Now {@code orient} is
 * declared by {@link BorderCeilingPattern} and {@link JoistsCeilingPattern} and by nobody else, so
 * an oriented {@code coffers} is a stray key: the same error, from the schema rather than from a
 * hand-written check. (The <em>other</em> orient rule survives, because it is a real relationship
 * between two fields of one type &mdash; see {@link JoistsCeilingPattern}.)</p>
 *
 * @see CeilingPatternRegistry
 */
public interface CeilingPattern {

    /**
     * This pattern's own codec, as registered. An implementation must return the <em>same</em>
     * instance it was registered with; that identity is how the id is recovered on encode.
     */
    MapCodec<? extends CeilingPattern> codec();

    /**
     * This pattern with any {@code $role} in its block fields replaced by the literal the palette in
     * scope names. #65 phase 4, and the exact counterpart of {@code FloorPattern#withRoles} --
     * including the reason it is a {@code default} rather than abstract: the registry is open to
     * other mods, so an abstract method would break every third-party pattern on upgrade, and the
     * default is safe because a field only carries a role when its codec is
     * {@code Codecs.BLOCK_ID_OR_ROLE}. Forgetting to override costs a load error, not a ceiling that
     * silently comes out blank.
     *
     * <p>Implementations must return {@code this} when nothing changed; this is on the per-piece
     * path.</p>
     */
    default CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        return this;
    }

    /**
     * Appends the layers this pattern draws &mdash; usually one, and <strong>two for a bracketed
     * {@code joists}</strong>.
     *
     * <p>Nothing is appended when the pattern's own block will not resolve: a treatment with no
     * material is not a treatment, and dropping it leaves a plain ceiling rather than a half-drawn
     * one. That is the floor patterns' degrade-the-whole-entry rule, unchanged.</p>
     *
     * @param projection the depth this pattern hangs at, from the entry. It stays on the entry
     *                   because it positions the pattern within the ceiling's stack rather than
     *                   describing the pattern's own shape &mdash; and because the bracket layer
     *                   needs {@code projection + 1}, which is a fact about the stack.
     */
    void addLayers(int projection, List<Layer> out);

    /** The pattern's block with the author's properties applied, or {@code null} if unresolvable. */
    static BlockState state(String id, Map<String, String> properties) {
        Block block = BlockStateCodec.blockOrNull(id);
        return block == null ? null : BlockStateCodec.withProperties(block.defaultBlockState(), properties);
    }

    /** {@code properties}, shared by every type: applied once to every state the pattern places. */
    static <P extends CeilingPattern> com.mojang.serialization.codecs.RecordCodecBuilder<P, Map<String, String>>
            propertiesField(java.util.function.Function<P, Map<String, String>> getter) {
        return mod.gottsch.forge.dungeons2.core.config.Codecs.strictOptionalFieldOf(
                Codec.unboundedMap(Codec.STRING, Codec.STRING), "properties", Map.of()).forGetter(getter);
    }
}
