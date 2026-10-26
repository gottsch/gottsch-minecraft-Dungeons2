package mod.gottsch.forge.dungeons2.core.world;

import mod.gottsch.forge.dungeons2.core.map.FloorMapStamp;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor index a crafted Dungeon Map starts from: a floor is found by standing on it and nowhere
 * else, the same floor filed twice is one floor, and the index survives a save.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class DungeonFloorIndexTest {

    private static final FloorMapStamp UPPER = new FloorMapStamp(0, 40, 100, 200, 170, 260, new int[]{150, 240, 156, 246});
    private static final FloorMapStamp LOWER = new FloorMapStamp(1, 18, 95, 195, 175, 265, null);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aFloorIsFoundByStandingOnIt() {
        DungeonFloorIndex index = new DungeonFloorIndex();
        index.add(UPPER);
        index.add(LOWER);

        assertEquals(0, index.floorAt(new BlockPos(130, 41, 230)).orElseThrow().floorIndex(), "on the upper floor");
        assertEquals(0, index.floorAt(new BlockPos(130, 37, 230)).orElseThrow().floorIndex(), "down a corridor descent");
        assertEquals(1, index.floorAt(new BlockPos(130, 19, 230)).orElseThrow().floorIndex(), "on the lower floor");
        assertTrue(index.floorAt(new BlockPos(130, 29, 230)).isEmpty(), "on the stairs between them");
        assertTrue(index.floorAt(new BlockPos(130, 41, 300)).isEmpty(), "beside the dungeon");
        assertTrue(index.floorAt(new BlockPos(130, 70, 230)).isEmpty(), "on the surface above it");
    }

    @Test
    void aFloorFiledTwiceIsOneFloor() {
        DungeonFloorIndex index = new DungeonFloorIndex();
        index.add(UPPER);
        index.add(new FloorMapStamp(0, 40, 100, 200, 170, 260, new int[]{150, 240, 156, 246}));
        assertEquals(1, index.size());
    }

    @Test
    void theIndexSurvivesASave() {
        DungeonFloorIndex index = new DungeonFloorIndex();
        index.add(UPPER);
        index.add(LOWER);
        DungeonFloorIndex loaded = DungeonFloorIndex.load(index.save(new CompoundTag()));

        assertEquals(2, loaded.size());
        FloorMapStamp upper = loaded.floorAt(new BlockPos(130, 41, 230)).orElseThrow();
        assertEquals(UPPER.floorY(), upper.floorY());
        assertArrayEquals(UPPER.down(), upper.down());
    }
}
