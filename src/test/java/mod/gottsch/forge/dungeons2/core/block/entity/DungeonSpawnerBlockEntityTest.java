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
package mod.gottsch.forge.dungeons2.core.block.entity;

import mod.gottsch.forge.dungeons2.core.block.MobSetSpawnerBlock;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two things this subclass exists for: <strong>persisting the floor index</strong>, and being
 * saveable at all.
 *
 * <p>Both failures it guards are invisible in game. A floor index that does not persist would be
 * correct at generation and gone after the chunk reloaded &mdash; the spawner still spawns, just at
 * the wrong difficulty, forever. A save that throws would leave the spawner with none of its
 * configuration at all, which looks exactly like a room that never had one.</p>
 *
 * <p>{@code saveAdditional} / {@code load} directly rather than {@code saveWithFullMetadata}:
 * that one resolves the block-entity type's registry key and throws for an unregistered type, and
 * Forge's {@code DeferredRegister} does not run in a unit test. The field round trip is the subject
 * here, and it lives below the metadata layer.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
class DungeonSpawnerBlockEntityTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static BlockEntityType<DungeonSpawnerBlockEntity> type;
    private static BlockState state;

    /**
     * Constructing a {@code Block} allocates an intrusive holder in a frozen registry; the same
     * Forge {@code unfreeze()} hook {@code MobSetSpawnerBlockTest} documents is the way round it.
     * The type is built but never registered -- see the class note.
     */
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze();
        MobSetSpawnerBlock block = new MobSetSpawnerBlock(MobSetSpawnerBlock.properties());
        state = block.defaultBlockState();
        type = BlockEntityType.Builder.of(
                (pos, blockState) -> new DungeonSpawnerBlockEntity(() -> type, pos, blockState),
                block).build(null);
    }

    private static DungeonSpawnerBlockEntity spawner() {
        return new DungeonSpawnerBlockEntity(() -> type, POS, state);
    }

    @Test
    void aFreshSpawnerHasNoFloorYet() {
        assertEquals(DungeonSpawnerBlockEntity.UNKNOWN_FLOOR, spawner().getFloorIndex(),
                "unset must be distinguishable from floor 0, which is a real and common answer");
    }

    @Test
    void theFloorIndexSurvivesASaveAndLoad() {
        DungeonSpawnerBlockEntity original = spawner();
        original.setFloorIndex(4);

        CompoundTag tag = new CompoundTag();
        original.saveAdditional(tag);

        DungeonSpawnerBlockEntity loaded = spawner();
        loaded.load(tag);
        assertEquals(4, loaded.getFloorIndex());
    }

    /**
     * The generation path's shape: a tag built by {@code RoomSpawnerGenerator} is loaded into a
     * fresh entity, and the entity must then be able to save what it was given.
     */
    @Test
    void aFloorIndexLoadedFromGenerationDataIsSavedBackOut() {
        CompoundTag generated = new CompoundTag();
        generated.putString("mobSetName", "dungeons2:classic_vermin");
        generated.putInt("minMobs", 1);
        generated.putInt("maxMobs", 3);
        generated.putDouble("proximity", 8.0D);
        generated.putInt(DungeonSpawnerBlockEntity.FLOOR_INDEX, 2);

        DungeonSpawnerBlockEntity spawner = spawner();
        spawner.load(generated);
        assertEquals(2, spawner.getFloorIndex());

        CompoundTag resaved = new CompoundTag();
        spawner.saveAdditional(resaved);
        assertEquals(2, resaved.getInt(DungeonSpawnerBlockEntity.FLOOR_INDEX),
                "the floor index did not survive the round trip a chunk unload makes -- which is"
                        + " the entire reason this subclass exists rather than a plain tag key");
        assertEquals("dungeons2:classic_vermin", resaved.getString("mobSetName"));
    }

    /**
     * The parent's {@code saveAdditional} reads {@code getMobSizeRange().getMin()} unguarded, so it
     * throws NPE for any spawner whose range has not been loaded yet -- which is every freshly
     * created one. {@code DungeonPiece.applyBlockEntity} saves the entity <em>before</em> applying
     * data to it, so that was a real path, and it cost the spawner its whole configuration.
     */
    @Test
    void aFreshSpawnerCanBeSavedWithoutThrowing() {
        DungeonSpawnerBlockEntity spawner = spawner();
        CompoundTag tag = new CompoundTag();
        assertDoesNotThrow(() -> spawner.saveAdditional(tag));
        assertTrue(tag.contains("minMobs"), "the parent's own fields should still be written");
    }
}
