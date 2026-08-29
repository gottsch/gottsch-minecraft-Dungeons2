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
package mod.gottsch.forge.dungeons2.core.data;

/**
 * One authored potion effect: what it is, how strong, and for how long.
 *
 * <h2>What carries this, and what happens to it</h2>
 * <p>{@code dungeonblocks}' {@code PotionEntity} <strong>extends</strong> its {@code PotEntity} and
 * adds one NBT key, {@code Effects}, which it reads with {@code PotionUtils.getAllEffects} and
 * releases as an {@code AreaEffectCloud} when the pot breaks. So an effect-bearing pot is not a
 * different kind of prop from an ordinary one &mdash; it is the same prop with one extra tag, and
 * that is why this rides on {@link EntityPlacement} beside the loot table rather than needing a
 * pipeline of its own.</p>
 *
 * <p><strong>Effects on a variant that is not a {@code PotionEntity} are inert.</strong> A plain
 * {@code PotEntity} ignores the tag: the effects are written, saved and reloaded with the entity and
 * nothing ever reads them. That is deliberate rather than guarded &mdash; the alternative is
 * compiling against {@code dungeonblocks}' entity classes to ask, and {@code EntitySpawner}'s whole
 * design is to stay off them (a content dependency, not an API one). The authoring gates warn
 * instead.</p>
 *
 * <h2>Why an id string and not a MobEffect</h2>
 * <p>Same reason {@link EntityPlacement} holds an entity id string: this record is constructed and
 * compared in the placement planners, which are unit tested with no Forge instance running, so
 * nothing here may import {@code net.minecraft}. The string is resolved once, at spawn time, by
 * {@code EntitySpawner} &mdash; which also serialises through {@code MobEffectInstance#save} rather
 * than hand-writing the tag, so the numeric-vs-namespaced id question is vanilla's to answer and not
 * ours.</p>
 *
 * @param effect a mob effect id, e.g. {@code minecraft:poison}
 * @param amplifier 0 is level I, 1 is level II, as everywhere else in Minecraft
 * @param duration in ticks
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
public record PotionEffectSpec(String effect, int amplifier, int duration) {

    /** Level I for {@code duration} ticks &mdash; the common case. */
    public PotionEffectSpec(String effect, int duration) {
        this(effect, 0, duration);
    }
}
