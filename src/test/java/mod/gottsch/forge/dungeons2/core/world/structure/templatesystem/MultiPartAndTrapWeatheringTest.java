package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two kinds of block a per-block weathering rule must not treat as ordinary stone, checked in every
 * shipped list: a fixture built of several blocks, which a rule would take one part of, and the
 * crumbling lid of a false floor, which may only age into another crumbling block.
 *
 * @author Mark Gottschling on 10/22/2026
 */
class MultiPartAndTrapWeatheringTest {

    private static final String DIR = "/data/dungeons2/worldgen/processor_list/";
    private static final List<String> SHIPPED = List.of(
            "classic_weathering.json", "classic_mud_weathering.json",
            "classic_deepslate_weathering.json", "classic_entrance_weathering.json", "classic_mine_weathering.json",
            "classic_boss_weathering_small.json", "classic_boss_weathering_medium.json",
            "classic_boss_weathering_large.json");

    /** Fixtures of several blocks: a rule keyed on the block leaves the rest standing (half a gibbet). */
    private static final Set<String> MULTI_PART = Set.of("dungeonblocks:gibbet");

    @Test
    void noRuleTakesOnePartOfAMultiPartFixture() throws Exception {
        for (String file : SHIPPED) {
            for (JsonObject rule : agingRules(file)) {
                String block = rule.get("block").getAsString();
                assertTrue(!MULTI_PART.contains(block), file + ": a weathering rule on " + block);
            }
        }
    }

    /** A crumbling block turned into anything else is a safe tile in the trap, or a hole that shows it. */
    @Test
    void aCrumblingBlockOnlyAgesIntoAnotherCrumblingBlock() throws Exception {
        for (String file : SHIPPED) {
            boolean aged = false;
            for (JsonObject rule : agingRules(file)) {
                if (!rule.get("block").getAsString().startsWith("dungeonblocks:crumbling_")) continue;
                aged = true;
                for (JsonElement out : rule.getAsJsonArray("output_blocks")) {
                    String to = out.getAsJsonObject().get("block").getAsString();
                    assertTrue(to.startsWith("dungeonblocks:crumbling_"), file + ": crumbling -> " + to);
                }
            }
            assertEquals(true, aged, file + " does not age the crumbling lid");
        }
    }

    /** Every rule of the property-carrying aging processors, both shapes (surface_aging and aging). */
    private static List<JsonObject> agingRules(String file) throws Exception {
        List<JsonObject> rules = new ArrayList<>();
        for (JsonElement element : processors(file)) {
            JsonObject processor = element.getAsJsonObject();
            String type = processor.get("processor_type").getAsString();
            if (!type.equals("dungeons2:surface_aging") && !type.equals("dungeons2:aging")) continue;
            for (JsonElement rule : processor.getAsJsonArray("rules")) {
                rules.add(rule.getAsJsonObject());
            }
        }
        assertTrue(!rules.isEmpty(), file + " has no aging rules");
        return rules;
    }

    private static JsonArray processors(String file) throws Exception {
        try (InputStream in = MultiPartAndTrapWeatheringTest.class.getResourceAsStream(DIR + file)) {
            assertNotNull(in, "missing " + file);
            JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.setLenient(true); // the shipped lists carry // comments
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("processors");
        }
    }
}
