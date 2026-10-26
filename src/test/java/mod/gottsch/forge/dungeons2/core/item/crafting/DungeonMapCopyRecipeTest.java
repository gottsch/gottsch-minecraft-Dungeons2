package mod.gottsch.forge.dungeons2.core.item.crafting;

import mod.gottsch.forge.dungeons2.core.map.DungeonMaps;
import mod.gottsch.forge.dungeons2.core.map.FloorMapStamp;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
            new DungeonMapCopyRecipe(new ResourceLocation("dungeons2", "blank_dungeon_map"), CraftingBookCategory.MISC);

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
        stack.getOrCreateTag().putInt("map", 7);
        stack.getOrCreateTag().put(DungeonMaps.TAG, new FloorMapStamp(1, 9, 0, 0, 40, 40, null).save());
        return stack;
    }

    private static CraftingContainer grid(ItemStack... stacks) {
        Grid grid = new Grid();
        for (int i = 0; i < stacks.length; i++) {
            grid.setItem(i, stacks[i]);
        }
        return grid;
    }

    /** A 3x3 crafting grid with no menu behind it. */
    private static class Grid extends SimpleContainer implements CraftingContainer {
        Grid() {
            super(9);
        }

        @Override
        public int getWidth() {
            return 3;
        }

        @Override
        public int getHeight() {
            return 3;
        }

        @Override
        public List<ItemStack> getItems() {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < getContainerSize(); i++) {
                items.add(getItem(i));
            }
            return items;
        }

        @Override
        public void fillStackedContents(StackedContents contents) {
            getItems().forEach(contents::accountStack);
        }
    }
}
