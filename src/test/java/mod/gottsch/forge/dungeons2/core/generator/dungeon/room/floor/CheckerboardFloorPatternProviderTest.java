package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckerboardFloorPatternProviderTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void alternatesByXPlusZParity() {
        CheckerboardFloorPatternProvider provider =
                new CheckerboardFloorPatternProvider(Blocks.GRANITE, Blocks.DIORITE);
        List<BlockPlacement> out = new ArrayList<>();
        provider.build(4, 4, 0, 0, 0, out);

        Map<String, BlockPlacement> byCoord = new HashMap<>();
        for (BlockPlacement p : out) {
            byCoord.put(p.getX() + "," + p.getZ(), p);
        }
        assertEquals("minecraft:granite", byCoord.get("0,0").getBlockId());
        assertEquals("minecraft:diorite", byCoord.get("1,0").getBlockId());
        assertEquals("minecraft:diorite", byCoord.get("0,1").getBlockId());
        assertEquals("minecraft:granite", byCoord.get("1,1").getBlockId());
        assertEquals(16, out.size());
    }

    @Test
    void neitherSlotHasAJavaSideDefault() {
        // No motif-scoped fallback block for either slot -- the motif config is the single
        // source of truth, so a missing block is a construction-time error, not a silent guess.
        assertThrows(NullPointerException.class,
                () -> new CheckerboardFloorPatternProvider(null, Blocks.DIORITE));
        assertThrows(NullPointerException.class,
                () -> new CheckerboardFloorPatternProvider(Blocks.GRANITE, null));
    }
}
