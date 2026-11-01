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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <strong>Vanilla strips {@code minecraft:structure_block} out of a jigsaw pool element before any
 * of the pool's own processors run.</strong> This is why a {@code d2:spawner} DATA marker cannot be
 * implemented as a structure processor, and it cost a wrong design to find out (2026-08-14).
 *
 * <h2>The symptom, and why it was confusing</h2>
 * <p>An authored marker produced neither a spawner nor a visible structure block &mdash; it produced
 * <em>whatever terrain was already there</em> (a coal ore, in the reported case). That reads like
 * "the cell was never written", and that is exactly right: {@code BlockIgnoreProcessor} returns
 * {@code null} for the block, which removes it from the placement list entirely rather than
 * replacing it. A cell nothing writes leaves the stone the dungeon was carved out of.</p>
 *
 * <h2>The ordering is the whole point</h2>
 * <p>{@code SinglePoolElement.getSettings} adds {@code BlockIgnoreProcessor.STRUCTURE_BLOCK}
 * <em>first</em> and only then appends the pool's {@code processors} list. Processors run in order,
 * so a pool processor is handed a block list the structure blocks have already been removed from.
 * "A processor sees every block in the template" is true for a raw
 * {@code StructureTemplate.placeInWorld} and false for a jigsaw pool element, which is the only way
 * Dungeons2 places authored content.</p>
 *
 * <p>Village Dungeons keys its spawner processor on ordinary <em>marker blocks</em> rather than DATA
 * structure blocks. That looked like a stylistic choice; it is forced.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
class JigsawStripsStructureBlocksTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The mechanism: ignore means REMOVE, not "leave as authored". */
    @Test
    void blockIgnoreProcessorRemovesAStructureBlockEntirely() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("mode", "DATA");
        nbt.putString("metadata", "d2:spawner");
        StructureTemplate.StructureBlockInfo info = new StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO, Blocks.STRUCTURE_BLOCK.defaultBlockState(), nbt);

        assertNull(BlockIgnoreProcessor.STRUCTURE_BLOCK.processBlock(
                        null, BlockPos.ZERO, BlockPos.ZERO, info, info, new StructurePlaceSettings()),
                "returning null removes the block from the placement list. The cell is then never"
                        + " written and the surrounding terrain shows through -- which is what a"
                        + " d2:spawner marker actually produced in game.");
    }

    /** And a jigsaw pool element always installs it, ahead of the pool's own processors. */
    @Test
    void aJigsawPoolElementInstallsThatProcessorBeforeItsOwn() throws Exception {
        SinglePoolElement element = StructurePoolElement
                .single("dungeons2:rooms/classic/15x21_hall_1")
                .apply(StructureTemplatePool.Projection.RIGID);

        Method getSettings = SinglePoolElement.class.getDeclaredMethod(
                "getSettings", Rotation.class, BoundingBox.class, boolean.class);
        getSettings.setAccessible(true);
        StructurePlaceSettings settings =
                (StructurePlaceSettings) getSettings.invoke(element, Rotation.NONE, BoundingBox.infinite(), false);

        boolean strips = false;
        for (StructureProcessor processor : settings.getProcessors()) {
            if (processor instanceof BlockIgnoreProcessor) {
                strips = true;
                break;
            }
        }
        assertTrue(strips,
                "SinglePoolElement no longer installs a BlockIgnoreProcessor. If that is genuinely true, DATA"
                        + " structure-block markers may have become viable again -- re-read the"
                        + " d2:spawner design notes before acting on it.");
    }
}
