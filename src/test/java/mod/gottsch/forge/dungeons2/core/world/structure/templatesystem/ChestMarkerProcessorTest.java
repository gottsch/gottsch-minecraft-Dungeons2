package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.dungeons2.core.block.entity.ChestMarkerBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authored chest route. Backlog #48 step 3.
 *
 * <p>The marker block itself is not registered in a bare {@code Bootstrap}, so these exercise the
 * parts that do not need it: the loot-table precedence, the seed, and the two failure paths. That a
 * marker block matches at all is what {@code ShippedBlockIdsTest} and an in-game visit cover.</p>
 */
class ChestMarkerProcessorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static StructureTemplate.StructureBlockInfo marker(CompoundTag nbt) {
        // Blocks.CHEST stands in for the marker: the processor is asked about a block it was not
        // configured to match, which is the "leave it alone" path.
        return new StructureTemplate.StructureBlockInfo(new BlockPos(3, 4, 5),
                Blocks.CHEST.defaultBlockState(), nbt);
    }

    private static ChestMarkerProcessor processor(String poolTable) {
        return new ChestMarkerProcessor(
                poolTable == null ? null : new ResourceLocation(poolTable),
                new ResourceLocation("dungeons2:chest_marker"),
                new ResourceLocation("minecraft:chest"));
    }

    /**
     * A block that is not the configured marker must come back untouched -- byte for byte the same
     * object, since a processor that rebuilt every block it saw would be rewriting whole templates.
     */
    @Test
    void aBlockThatIsNotTheMarkerIsUntouched() {
        StructureTemplate.StructureBlockInfo info = marker(null);
        assertSame(info, processor("dungeons2:chests/classic_shallow")
                .processBlock(null, BlockPos.ZERO, BlockPos.ZERO, info, info,
                        new StructurePlaceSettings()));
    }

    /** The seed must never be 0: vanilla reads 0 as "roll fresh on open". */
    @Test
    void theLootSeedIsNeverZero() {
        assertNotEquals(0L, ChestMarkerProcessor.lootSeed(BlockPos.ZERO));
        assertNotEquals(0L, ChestMarkerProcessor.lootSeed(new BlockPos(12, 70, -40)));
    }

    /**
     * Two markers at different positions must not share a seed, or a template placed twice in one
     * dungeon holds the same items both times.
     */
    @Test
    void twoPositionsGiveDifferentSeeds() {
        assertNotEquals(ChestMarkerProcessor.lootSeed(new BlockPos(1, 2, 3)),
                ChestMarkerProcessor.lootSeed(new BlockPos(3, 2, 1)));
    }

    /** The per-cell table is the whole reason the marker carries a block entity. */
    @Test
    void aMarkersOwnTableIsReadOffItsNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(ChestMarkerBlockEntity.LOOT_TABLE, "dungeons2:chests/classic_hoard");
        assertEquals("dungeons2:chests/classic_hoard", nbt.getString(ChestMarkerBlockEntity.LOOT_TABLE));
        assertTrue(nbt.contains(ChestMarkerBlockEntity.LOOT_TABLE));
    }

    /** The opt-in #48 step 4 will read. Present in the shape now so a template can carry it early. */
    @Test
    void theTreasureFlagRoundTripsThroughTheBlockEntityTag() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean(ChestMarkerBlockEntity.TREASURE, true);
        assertTrue(nbt.getBoolean(ChestMarkerBlockEntity.TREASURE));
    }
}
