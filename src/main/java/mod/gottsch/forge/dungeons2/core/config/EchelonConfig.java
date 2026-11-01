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

import java.util.List;
import java.util.Optional;

/**
 * The motif's {@code echelon} block: what one step of Enemy Echelons difficulty DOES to a mob this
 * dungeon spawns. The depth bands' {@code difficulty} says which step a floor is at; this says what
 * a step is worth. Only read when the Enemy Echelons API is installed.
 *
 * <h2>Dungeons2's own numbers, not Stronger Mobs Below's</h2>
 * <p>These become an {@code EchelonRegistry} Dungeons2 owns, so they apply with EE alone and are
 * free to disagree with whatever SMB's config says a difficulty is worth. SMB may or may not be
 * installed; when it is, it still scales every mob Dungeons2 did not spawn, by world Y.</p>
 *
 * <h2>Each factor is per difficulty step, and 0 is "leave this attribute alone"</h2>
 * <p>{@code hp_factor: 0.2} at difficulty 3 is {@code +60%} max health (EE's
 * {@code MULTIPLY_BASE}). The two {@code _increment}s are flat additions per step. EE ignores a
 * factor that is not strictly positive, so the codec's floor of 0 is the real floor.</p>
 *
 * <h2>What is deliberately missing</h2>
 * <ul>
 *   <li><strong>{@code max_hp}, {@code max_damage}, ...</strong> &mdash; EE 1.0.1 declares them and
 *       applies none of them. A key that parses and does nothing is the failure this schema is
 *       closed to prevent. {@code max_xp} is the exception because Dungeons2 applies XP itself.</li>
 *   <li><strong>{@code flying_speed_factor}</strong> &mdash; EE 1.0.1 scales {@code FLYING_SPEED}
 *       by {@code speed_factor}, not by its own factor. So {@code speed_factor} already covers
 *       fliers, and a separate key would be inert.</li>
 * </ul>
 *
 * @param mobBlacklist entity ids never scaled, e.g. a boss whose fight is tuned by hand
 *
 * @author Mark Gottschling on Sep 23, 2026
 */
public record EchelonConfig(double hpFactor, double damageFactor, double armorFactor,
                            double armorToughnessFactor, double knockbackIncrement,
                            double knockbackResistIncrement, double speedFactor,
                            double xpFactor, Optional<Double> maxXp,
                            List<String> mobBlacklist) {

    private static final Codec<Double> FACTOR = Codec.doubleRange(0.0D, Double.MAX_VALUE);

    // Codecs.closed -- see RoomScheme.CODEC. Every factor defaults to 0, so an author writes only
    // the attributes they want scaled.
    public static final Codec<EchelonConfig> CODEC = Codecs.closed(RecordCodecBuilder.<EchelonConfig>mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(FACTOR, "hp_factor", 0.0D).forGetter(EchelonConfig::hpFactor),
            Codecs.strictOptionalFieldOf(FACTOR, "damage_factor", 0.0D).forGetter(EchelonConfig::damageFactor),
            Codecs.strictOptionalFieldOf(FACTOR, "armor_factor", 0.0D).forGetter(EchelonConfig::armorFactor),
            Codecs.strictOptionalFieldOf(FACTOR, "armor_toughness_factor", 0.0D)
                    .forGetter(EchelonConfig::armorToughnessFactor),
            Codecs.strictOptionalFieldOf(FACTOR, "knockback_increment", 0.0D)
                    .forGetter(EchelonConfig::knockbackIncrement),
            Codecs.strictOptionalFieldOf(FACTOR, "knockback_resist_increment", 0.0D)
                    .forGetter(EchelonConfig::knockbackResistIncrement),
            Codecs.strictOptionalFieldOf(FACTOR, "speed_factor", 0.0D).forGetter(EchelonConfig::speedFactor),
            Codecs.strictOptionalFieldOf(FACTOR, "xp_factor", 0.0D).forGetter(EchelonConfig::xpFactor),
            Codecs.strictOptionalFieldOf(FACTOR, "max_xp").forGetter(EchelonConfig::maxXp),
            Codecs.strictOptionalFieldOf(Codec.STRING.listOf(), "mob_blacklist", List.of())
                    .forGetter(EchelonConfig::mobBlacklist)
    ).apply(instance, EchelonConfig::new)));

    /** The XP a mob at {@code difficulty} drops, from what it would have dropped unscaled. */
    public int scaleXp(int originalXp, int difficulty) {
        double xp = originalXp * (1.0D + xpFactor * Math.max(0, difficulty));
        if (maxXp.isPresent()) {
            xp = Math.min(xp, maxXp.get());
        }
        return (int) xp;
    }
}
