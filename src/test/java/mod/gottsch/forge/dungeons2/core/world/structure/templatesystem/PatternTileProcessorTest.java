package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import mod.gottsch.forge.dungeonblocks.core.block.PatternTileBlock;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The flagstone fix-up: a pattern tile's {@code tile} follows its WORLD position, whatever the
 * template said, and the processor that does it sits last in every shipped list.
 *
 * @author Mark Gottschling on 10/22/2026
 */
class PatternTileProcessorTest {

    private static final String DIR = "/data/dungeons2/worldgen/processor_list/";
    private static final List<String> SHIPPED = List.of(
            "classic_weathering.json", "classic_mud_weathering.json",
            "classic_deepslate_weathering.json", "classic_entrance_weathering.json",
            "classic_boss_weathering_small.json", "classic_boss_weathering_medium.json",
            "classic_boss_weathering_large.json");

    private static PatternTileBlock flagstones;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        flagstones = new PatternTileBlock(BlockBehaviour.Properties.of());
    }

    /** Whatever slice the template carried, the result is the world position's, negatives included. */
    @Test
    void theTileFollowsTheWorldPosition() {
        BlockState authored = flagstones.defaultBlockState().setValue(PatternTileBlock.TILE, 7);
        for (BlockPos pos : List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 2),
                new BlockPos(-1, 64, -1), new BlockPos(-145, 63, 63), new BlockPos(16, 5, -17))) {
            BlockState aligned = PatternTileProcessor.aligned(authored, pos);
            int expected = Math.floorMod(pos.getX(), 3) + 3 * Math.floorMod(pos.getZ(), 3);
            assertEquals(expected, aligned.getValue(PatternTileBlock.TILE), "at " + pos);
            assertEquals(PatternTileBlock.tileAt(pos), aligned.getValue(PatternTileBlock.TILE));
        }
    }

    @Test
    void anyOtherBlockIsLeftAlone() {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        assertSame(stone, PatternTileProcessor.aligned(stone, new BlockPos(4, 64, 5)));
    }

    /**
     * Flagstones age in every list, and only through the property-carrying processors. A vanilla
     * {@code minecraft:rule} would still be re-tiled by the processor at the end, so this is not
     * about the pattern: it is about keeping the floor's chain in the one place every other floor
     * rule lives, so a retune finds all of it.
     */
    @Test
    void flagstonesAgeInEveryListAndNeverThroughAVanillaRule() throws Exception {
        for (String file : SHIPPED) {
            JsonArray processors = processors(file);
            boolean aged = false;
            for (JsonElement element : processors) {
                String type = element.getAsJsonObject().get("processor_type").getAsString();
                String body = element.toString();
                if ("minecraft:rule".equals(type)) {
                    assertEquals(false, body.contains("flagstones"),
                            file + ": a vanilla rule touches flagstones");
                } else if (body.contains("\"dungeonblocks:flagstones\"")) {
                    aged = true;
                }
            }
            assertEquals(true, aged, file + " has no rule ageing dungeonblocks:flagstones");
        }
    }

    private static JsonArray processors(String file) throws Exception {
        try (InputStream in = PatternTileProcessorTest.class.getResourceAsStream(DIR + file)) {
            assertNotNull(in, "missing " + file);
            JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.setLenient(true);
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("processors");
        }
    }

    /**
     * LAST, because anything after it could put a tile down unaligned -- an aging rule swapping in a
     * weathered twin, most likely. And in every list, so a tile lines up whichever pool placed it.
     */
    @Test
    void everyShippedListEndsWithIt() throws Exception {
        for (String file : SHIPPED) {
            try (InputStream in = PatternTileProcessorTest.class.getResourceAsStream(DIR + file)) {
                assertNotNull(in, "missing " + file);
                JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                reader.setLenient(true); // the shipped lists carry // comments
                JsonArray processors = JsonParser.parseReader(reader).getAsJsonObject()
                        .getAsJsonArray("processors");
                JsonElement last = processors.get(processors.size() - 1);
                assertEquals("dungeons2:pattern_tile",
                        last.getAsJsonObject().get("processor_type").getAsString(),
                        file + " does not end with dungeons2:pattern_tile");
            }
        }
    }
}
