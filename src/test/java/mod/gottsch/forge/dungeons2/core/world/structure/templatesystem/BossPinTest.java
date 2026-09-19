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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.block.entity.DungeonSpawnerBlockEntity;
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import mod.gottsch.forge.gottschcore.mobset.MobSetData;
import mod.gottsch.forge.gottschcore.mobset.WeightedMob;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The boss drawn at planning time reaches its spawner as a PIN beside the assigned set, and the set
 * keeps the final word.
 *
 * <p>Two properties, each of which fails silently in game:</p>
 * <ul>
 *   <li>the pin must survive the processor CODEC, because the pinned processor list is written inline
 *       into the boss room's pool element and saved with the structure piece &mdash; a field the codec
 *       drops is a boss that is right on generation and forgotten on the next chunk load;</li>
 *   <li>the pin must never displace the set: the set is still written, and a pin the set no longer
 *       offers is ignored.</li>
 * </ul>
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
class BossPinTest {

    private static final ResourceLocation MEDIUM = new ResourceLocation("dungeons2", "medium_dungeon_boss");
    private static final ResourceLocation BEHOLDER = new ResourceLocation("dungeons2", "beholder");
    private static final ResourceLocation MINOTAUR = new ResourceLocation("dungeons2", "minotaur");

    @Test
    void aPinnedBossSurvivesTheCodecAndLeavesTheSetAlone() {
        Codec<SpawnerMarkerProcessor> codec = SpawnerMarkerProcessor.codec(() -> null);
        SpawnerMarkerProcessor pinned = new SpawnerMarkerProcessor(
                new ResourceLocation("dungeons2", "classic_undead"), Optional.of(MEDIUM),
                SpawnerMarkerProcessor.DEFAULT_MARKER_BLOCK, 20.0D, SpawnerConfig.Kind.PROXIMITY)
                .withBossMob(BEHOLDER);

        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, pinned)
                .getOrThrow(false, err -> fail("encode: " + err));
        assertEquals(BEHOLDER.toString(), json.getAsJsonObject().get("boss_mob").getAsString());
        assertEquals(MEDIUM.toString(), json.getAsJsonObject().get("boss_mob_set").getAsString(),
                "pinning must not replace the set the tier assigned");

        SpawnerMarkerProcessor back = codec.parse(JsonOps.INSTANCE, json)
                .getOrThrow(false, err -> fail("decode: " + err));
        assertEquals(Optional.of(BEHOLDER), back.bossMob());
        assertEquals(Optional.of(MEDIUM), back.bossMobSet());
    }

    @Test
    void anUnpinnedListStillDecodesWithNoPin() {
        Codec<SpawnerMarkerProcessor> codec = SpawnerMarkerProcessor.codec(() -> null);
        SpawnerMarkerProcessor plain = new SpawnerMarkerProcessor(
                new ResourceLocation("dungeons2", "classic_undead"), Optional.of(MEDIUM),
                SpawnerMarkerProcessor.DEFAULT_MARKER_BLOCK, 20.0D, SpawnerConfig.Kind.PROXIMITY);
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, plain)
                .getOrThrow(false, err -> fail("encode: " + err));
        assertFalse(json.getAsJsonObject().has("boss_mob"), "an unpinned list must not grow the field");
        assertEquals(Optional.empty(), codec.parse(JsonOps.INSTANCE, json)
                .getOrThrow(false, err -> fail("decode: " + err)).bossMob());
    }

    @Test
    void aPinIsHonouredOnlyWhileTheAssignedSetStillOffersIt() {
        MobSetData menu = new MobSetData(MEDIUM, "boss", 1,
                List.of(new WeightedMob(BEHOLDER, 10), new WeightedMob(MINOTAUR, 0)));
        assertTrue(DungeonSpawnerBlockEntity.setOffers(menu, BEHOLDER));
        assertFalse(DungeonSpawnerBlockEntity.setOffers(menu, MINOTAUR),
                "weight 0 is a mob the set can never draw, so it does not offer it");
        assertFalse(DungeonSpawnerBlockEntity.setOffers(menu, new ResourceLocation("dungeons2", "daemon")),
                "a mob taken off the menu after the world generated must lose to the set");
    }
}
