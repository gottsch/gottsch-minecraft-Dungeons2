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

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which cells {@code dungeons2:pedestal_loot} schedules a roll for. The scheduling itself is one
 * vanilla call; what can go wrong is the selection.
 *
 * @author Mark Gottschling on Oct 24, 2026
 */
class PedestalLootProcessorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos AT = new BlockPos(6, 1, 11);
    private static final BoundingBox CHUNK = new BoundingBox(0, -64, 0, 15, 320, 15);

    private static StructureTemplate.StructureBlockInfo info(BlockPos pos, String id, String table) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", id);
        if (table != null) {
            nbt.putString("LootTable", table);
        }
        // The state is irrelevant to the selection: a blackstone pedestal shares the entity type.
        return new StructureTemplate.StructureBlockInfo(pos, Blocks.STONE.defaultBlockState(), nbt);
    }

    @Test
    void aPedestalWithATableInThisChunkRolls() {
        assertTrue(PedestalLootProcessor.rollsLoot(
                info(AT, "dungeonblocks:pedestal", "dungeons2:pedestals/classic_altar"), CHUNK));
    }

    @Test
    void anEmptyOrStockedPedestalDoesNot() {
        assertFalse(PedestalLootProcessor.rollsLoot(info(AT, "dungeonblocks:pedestal", null), CHUNK));
    }

    @Test
    void aChestWithATableIsNotAPedestal() {
        assertFalse(PedestalLootProcessor.rollsLoot(
                info(AT, "minecraft:chest", "dungeons2:chests/classic_deep"), CHUNK));
    }

    @Test
    void aPedestalInAnotherChunkIsLeftToThatChunksPass() {
        assertFalse(PedestalLootProcessor.rollsLoot(
                info(new BlockPos(20, 1, 11), "dungeonblocks:pedestal", "dungeons2:pedestals/classic_altar"),
                CHUNK));
    }
}
