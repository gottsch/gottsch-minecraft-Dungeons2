package mod.gottsch.neo.dungeons2.core.item.crafting;

import mod.gottsch.neo.dungeons2.core.map.DungeonMaps;
import mod.gottsch.neo.dungeons2.core.map.FloorMapStamp;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copying a Dungeon Map: one Dungeon Map, one Empty Map and one coal, nothing else, and the Dungeon
 * Map comes back out of the grid.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class DungeonMapCopyRecipeTest {

    private static final DungeonMapCopyRecipe RECIPE =
            new DungeonMapCopyRecipe(CraftingBookCategory.MISC);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aDungeonMapAndAnEmptyMapMatch() {
        assertTrue(RECIPE.matches(grid(dungeonMap(), new ItemStack(Items.MAP), new ItemStack(Items.COAL)), null));
        assertTrue(RECIPE.matches(grid(new ItemStack(Items.CHARCOAL), dungeonMap(), new ItemStack(Items.MAP)), null));
    }

    @Test
    void anythingElseDoesNot() {
        ItemStack coal = new ItemStack(Items.COAL);
        assertFalse(RECIPE.matches(grid(dungeonMap(), coal.copy()), null), "no empty map");
        // left to vanilla's map copying, which makes a second copy of the same floor
        assertFalse(RECIPE.matches(grid(dungeonMap(), new ItemStack(Items.MAP)), null), "no coal");
        assertFalse(RECIPE.matches(grid(new ItemStack(Items.FILLED_MAP), new ItemStack(Items.MAP), coal.copy()), null),
                "a vanilla map is not a Dungeon Map");
        assertFalse(RECIPE.matches(grid(dungeonMap(), dungeonMap(), new ItemStack(Items.MAP), coal.copy()), null),
                "two maps");
        assertFalse(RECIPE.matches(grid(dungeonMap(), new ItemStack(Items.MAP), coal.copy(), new ItemStack(Items.STICK)), null),
                "something extra");
    }

    @Test
    void theDungeonMapComesBack() {
        ItemStack map = dungeonMap();
        NonNullList<ItemStack> remaining = RECIPE.getRemainingItems(grid(map, new ItemStack(Items.MAP), new ItemStack(Items.COAL)));
        assertNotNull(DungeonMaps.stampOf(remaining.get(0)), "the Dungeon Map stays");
        assertEquals(1, remaining.get(0).getCount());
        assertTrue(remaining.get(1).isEmpty(), "the empty map is used");
        assertTrue(remaining.get(2).isEmpty(), "and the coal");
    }

    private static ItemStack dungeonMap() {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, new MapId(7));
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.put(DungeonMaps.TAG, new FloorMapStamp(1, 9, 0, 0, 40, 40, null).save()));
        return stack;
    }

    /** A 3x3 crafting grid filled from the top-left, with no menu behind it. */
    private static CraftingInput grid(ItemStack... stacks) {
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }
        return CraftingInput.of(3, 3, items);
    }
}
