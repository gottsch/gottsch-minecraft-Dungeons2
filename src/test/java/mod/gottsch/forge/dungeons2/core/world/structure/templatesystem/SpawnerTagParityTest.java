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

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSpawnerGenerator;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two ways a Dungeons2 spawner reaches the world must configure it identically.
 *
 * <h2>Why two ways at all</h2>
 * <p>{@link SpawnerMarkerProcessor} converts a marker block an author placed in a template; the
 * {@code spawners} room-scheme slot ({@code RoomSpawnerGenerator}) emits one into a procedurally
 * built room. They cannot share the code that builds the block-entity data: the processor hands
 * vanilla a real {@link CompoundTag} with typed puts, while the procedural side travels as
 * {@code BlockEntityData}'s stringified key/values and is parsed back by
 * {@code DungeonPiece.applyBlockEntity}. Two encodings of one shape.</p>
 *
 * <p>Which makes drift between them cheap and invisible: rename a field on one side and the spawners
 * from <em>that</em> source silently stop reading their mob set, while the others keep working.
 * There is no in-game symptom to notice, because a spawner that does nothing looks like a room that
 * never had one. This is the check that would notice.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
class SpawnerTagParityTest {

    private static final String MOB_SET = "dungeons2:classic_vermin";
    private static final int FLOOR_INDEX = 2;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The processor's tag, for a marker configured exactly as the scheme slot below is. */
    private static CompoundTag authoredTag() {
        return new SpawnerMarkerProcessor(new ResourceLocation(MOB_SET),
                SpawnerMarkerProcessor.DEFAULT_MARKER_BLOCK, 8.0D, 1, 3).spawnerTag();
    }

    private static BlockEntityData proceduralData() {
        SpawnerConfig config = new SpawnerConfig(1, 1, 1, 3, 8.0D,
                List.of(new SpawnerConfig.MobSetEntry(MOB_SET, 1)));
        return RoomSpawnerGenerator.spawnerData(config, MOB_SET, FLOOR_INDEX);
    }

    @Test
    void bothSidesNameTheSameBlockEntityType() {
        assertEquals(authoredTag().getString("id"), proceduralData().getType());
    }

    @Test
    void bothSidesWriteTheSameFields() {
        CompoundTag authored = authoredTag();
        Set<String> authoredFields = new java.util.TreeSet<>(authored.getAllKeys());
        authoredFields.remove("id"); // carried by BlockEntityData's type, not its data map

        Set<String> proceduralFields = new java.util.TreeSet<>(proceduralData().getData().keySet());
        // floorIndex is the one deliberate asymmetry -- see theAuthoredPathCannotKnowItsFloor.
        proceduralFields.remove("floorIndex");

        assertEquals(authoredFields, proceduralFields,
                "the two spawner sources have drifted apart on which fields they set");
    }

    @Test
    void bothSidesWriteTheSameValues() {
        CompoundTag authored = authoredTag();
        Map<String, String> procedural = proceduralData().getData();
        assertEquals(authored.getString("mobSetName"), procedural.get("mobSetName"));
        assertEquals(authored.getInt("minMobs"), Integer.parseInt(procedural.get("minMobs")));
        assertEquals(authored.getInt("maxMobs"), Integer.parseInt(procedural.get("maxMobs")));
        assertEquals(authored.getDouble("proximity"),
                Double.parseDouble(procedural.get("proximity")));
    }

    /**
     * <strong>The one field the two paths cannot agree on, and it is not a defect.</strong> A
     * structure processor runs while vanilla places a jigsaw pool element; nothing in that call
     * chain knows which floor of which dungeon the element belongs to, because the dungeon's own
     * planner is not involved in placing it. So an authored template's spawner carries no floor
     * index and reads {@code DungeonSpawnerBlockEntity.UNKNOWN_FLOOR}.
     *
     * <p><strong>Consequence worth knowing before the SMB integration lands:</strong> spawners in
     * authored rooms will not scale with depth on their own. Either the marker gains an explicit
     * {@code floor} field an author sets by hand, or the integration treats unknown as "use the
     * player's Y", which is SMB's own default behaviour anyway.</p>
     */
    @Test
    void theAuthoredPathCannotKnowItsFloor() {
        assertEquals(FLOOR_INDEX, Integer.parseInt(proceduralData().getData().get("floorIndex")));
        org.junit.jupiter.api.Assertions.assertFalse(authoredTag().contains("floorIndex"),
                "the marker processor grew a floorIndex -- if that is deliberate, this test and the"
                        + " field-parity one above both need revisiting");
    }

    /**
     * The processor's defaults and the scheme slot's defaults are declared in two files. An author
     * who sets neither should get the same spawner either way.
     */
    @Test
    void theTuningDefaultsAgree() {
        // The processor's defaults live in its codec, so they have to be reached through a decode
        // of the minimal authoring form -- naming a set and nothing else.
        JsonObject minimal = new JsonObject();
        minimal.addProperty("mob_set", MOB_SET);
        CompoundTag authored = SpawnerMarkerProcessor.codec(() -> null)
                .parse(JsonOps.INSTANCE, minimal)
                .getOrThrow(false, error -> {
                    throw new AssertionError("the processor's own minimal form no longer decodes: "
                            + error);
                })
                .spawnerTag();

        // effectiveMinMobs(), not minMobs(): the record component answers "what did the author
        // write" and is deliberately EMPTY here, because a scheme stating no count defers to the
        // floor's band before it falls back to this default. The resolved value is what the
        // processor's own default has to agree with.
        SpawnerConfig defaults = new SpawnerConfig(MOB_SET);
        assertEquals(authored.getInt("minMobs"), defaults.effectiveMinMobs());
        assertEquals(authored.getInt("maxMobs"), defaults.clampedMaxMobs());
        assertEquals(authored.getDouble("proximity"), defaults.proximity());
    }
}
