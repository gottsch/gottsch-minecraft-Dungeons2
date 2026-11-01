package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.PropConfig.PropVariant;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every barrel in a dungeon is worth opening: a template's empty barrel is stocked by
 * {@code dungeons2:barrel_loot}, a barrel prop by its {@code loot_table}, and an authored barrel's
 * own contents are left alone.
 *
 * @author Mark Gottschling on 10/22/2026
 */
class BarrelLootProcessorTest {

    private static final ResourceLocation TABLE = new ResourceLocation("dungeons2", "barrels/classic");
    private static final String LISTS = "/data/dungeons2/worldgen/processor_list/";
    private static final List<String> SHIPPED = List.of(
            "classic_weathering.json", "classic_mud_weathering.json",
            "classic_deepslate_weathering.json", "classic_entrance_weathering.json", "classic_mine_weathering.json",
            "classic_boss_weathering_small.json", "classic_boss_weathering_medium.json",
            "classic_boss_weathering_large.json");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static StructureBlockInfo barrel(BlockPos pos, CompoundTag nbt) {
        return new StructureBlockInfo(pos, Blocks.BARREL.defaultBlockState(), nbt);
    }

    @Test
    void anEmptyTemplateBarrelGetsTheTableAndAFixedNonZeroSeed() {
        BlockPos pos = new BlockPos(12, 40, -7);
        CompoundTag stocked = BarrelLootProcessor.stocked(barrel(pos, null), TABLE);
        assertNotNull(stocked);
        assertEquals(TABLE.toString(), stocked.getString("LootTable"));
        assertNotEquals(0L, stocked.getLong("LootTableSeed"));
        assertEquals(stocked.getLong("LootTableSeed"),
                BarrelLootProcessor.stocked(barrel(pos, new CompoundTag()), TABLE).getLong("LootTableSeed"),
                "the seed is the position's, so every chunk pass agrees");
    }

    @Test
    void anAuthoredBarrelIsLeftAsAuthored() {
        CompoundTag withTable = new CompoundTag();
        withTable.putString("LootTable", "minecraft:chests/simple_dungeon");
        assertNull(BarrelLootProcessor.stocked(barrel(BlockPos.ZERO, withTable), TABLE));

        CompoundTag withItems = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:apple");
        stack.putByte("Count", (byte) 1);
        items.add(stack);
        withItems.put("Items", items);
        assertNull(BarrelLootProcessor.stocked(barrel(BlockPos.ZERO, withItems), TABLE));
    }

    @Test
    void anythingButABarrelIsLeftAlone() {
        assertNull(BarrelLootProcessor.stocked(
                new StructureBlockInfo(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), null), TABLE));
    }

    @Test
    void aRackedPropCannotAlsoRollATable() {
        assertTrue(PropVariant.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"block\":\"dungeonblocks:weapon_rack\",\"loot_table\":\"dungeons2:barrels/classic\","
                        + "\"rack\":{\"loot_table\":\"dungeons2:racks/classic_common\"}}")).result().isEmpty());
        assertTrue(PropVariant.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"block\":\"minecraft:barrel\",\"loot_table\":\"dungeons2:barrels/classic\"}"))
                .result().isPresent());
    }

    /** Every list stocks template barrels, and every barrel prop the motif ships rolls the table. */
    @Test
    void everyShippedBarrelRollsTheBarrelTable() throws Exception {
        for (String list : SHIPPED) {
            String text = read(LISTS + list);
            assertTrue(text.contains("\"processor_type\": \"dungeons2:barrel_loot\""), list);
        }
        assertNotNull(BarrelLootProcessorTest.class.getResourceAsStream(
                "/data/dungeons2/loot_tables/barrels/classic.json"), "the barrel table");
        Pattern barrelVariant = Pattern.compile("\\{[^{}]*\"block\": \"minecraft:barrel\"[^{}]*}");
        for (String config : List.of("schemes.json", "strata.json")) {
            Matcher m = barrelVariant.matcher(read("/data/dungeons2/dungeons2/motif_config/classic/" + config));
            int barrels = 0;
            while (m.find()) {
                barrels++;
                assertTrue(m.group().contains("\"loot_table\": \"dungeons2:barrels/classic\""),
                        config + ": a barrel prop with no table: " + m.group());
            }
            assertTrue(barrels > 0, config + " ships no barrel props");
        }
    }

    private static String read(String path) throws Exception {
        try (InputStream in = BarrelLootProcessorTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
