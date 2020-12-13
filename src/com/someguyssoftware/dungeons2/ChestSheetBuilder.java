package com.someguyssoftware.dungeons2;
/**
 * 
 */


import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.someguyssoftware.dungeons2.chest.ChestContainer;
import com.someguyssoftware.dungeons2.chest.ChestItem;
import com.someguyssoftware.dungeons2.chest.ChestItemEnchantment;
import com.someguyssoftware.dungeons2.chest.ChestItemGroup;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.chest.Enchants;
import com.someguyssoftware.dungeons2.chest.RandomGroup;
import com.someguyssoftware.dungeons2.chest.RandomItem;
import com.someguyssoftware.gottschcore.Quantity;


/**
 * @author Mark Gottschling on Jul 4, 2016
 *
 */
public class ChestSheetBuilder {
	
	/**
	 * map of all the items that a chest can contain
	 */
	Map<String, ChestItem> itemMap = new LinkedHashMap<>();
	/*
	 * map of all randomized items (ie item, chance, min/max, weight, etc)
	 */
	Map<String, RandomItem> randomItemMap = new LinkedHashMap<>();
	/*
	 * map of all the groups that will contain randomized items
	 */
	Map<String, RandomGroup> randomGroupMap = new LinkedHashMap<>();
	/*
	 * map of all the enchantments
	 */
	Map<String, ChestItemEnchantment> enchantmentsMap = new HashMap<>();
	
	ChestSheet chestSheet = null;
	
	public ChestSheetBuilder() {
		chestSheet = new ChestSheet();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ChestSheetBuilder chestBuilder = new ChestSheetBuilder();
		
		GsonBuilder builder = new GsonBuilder();
		/*
		 *  create a serializer for ChestItem.
		 *  this serializer doesn't include the ID of the chestItem in the JSON object since the ID is used as the key to the map.
		 */
		JsonSerializer<ChestItem> serializer = new JsonSerializer<ChestItem>() {  
		    @Override
		    public JsonElement serialize(ChestItem src, Type typeOfSrc, JsonSerializationContext context) {
		        JsonObject jsonObj = new JsonObject();
		        jsonObj.addProperty("NAME", src.getName());
		        jsonObj.addProperty("damage", src.getDamage());

		        return jsonObj;
		    }
		};
		// register custom serializer with the builder
		builder.registerTypeAdapter(ChestItem.class, serializer);
		builder.setPrettyPrinting();
		Gson gson = builder.create();
		
		// build enchantments
		chestBuilder.buildEnchantments();
		
		// build items
		chestBuilder.buildItems();
		
		// build random items
		chestBuilder.buildRandomItems();

		
		// TODO add potions
		
		// build item groups
		chestBuilder.buildItemGroups();
		
		// build random group
		chestBuilder.buildRandomGroups();

		// build containers
		chestBuilder.buildContainers();
			
		String json = gson.toJson(chestBuilder.chestSheet);
		
		System.out.println( json);

	}

	/**
	 * Builds the list/map of enchantments that an item can have
	 */
	private void buildEnchantments() {
		// TODO add enchanted items
		e("bane_of_anthropods_x1", "bane_of_anthropods", 1);
		e("bane_of_anthropods_x2", "bane_of_anthropods", 2);
	}
	
	private void e(String id, String name, int level) {
		ChestItemEnchantment enchantment = new ChestItemEnchantment();
		enchantment.setName(name);
		enchantment.setLevel(level);
		enchantmentsMap.put(id, enchantment);
	}


	/**
	 * Builds the list of items that appears in the json file.
	 * @param itemMap
	 */
	private void buildItems() {
		// TODO it would be better to have another array of modids and pass the reference to m()
		
		// common items
		m("bread", "bread"); // 1
		m("wool", "wool");
		m("torch", "torch");
		m("leather", "leather");
		m("rabbit_hide", "rabbit_hide");
		m("gold_nugget", "gold_nugget");
		m("arrow", "arrow");
		m("dye0", "dye", 0); // ink sac
		m("bone", "bone");
		m("spider_eye", "spider_eye");
		m("rotten_flesh", "rotten_flesh"); // 10
		m("string", "string");
			
		// common food
		m("wheat", "wheat");
		m("wheat_seeds", "wheat_seeds");
		m("sugar", "sugar");
		m("brown_mushroom", "brown_mushroom");
		m("red_mushroom", "red_mushroom");
		m("apple", "apple");
		m("carrot", "carrot");		
		m("porkchop", "porkchop");
		m("rabbit", "rabbit");
		m("egg", "egg");
		m("chicken", "chicken");
		m("fish0", "fish", 0);
		m("fish1", "fish", 1);
		m("beef", "beef");
		m("cookie", "cookie");
		m("pumpkin", "pumpkin");
		m("pumpkin_seeds", "pumpkin_seeds");
		m("melon", "melon");
		m("melon_seeds", "melon_seeds");
		m("mutton", "mutton");
		m("cocoa", "cocoa");
		
		// uncommon items
		m("spectral_arrow", "spectral_arrow");
		m("paper", "paper");
		m("reeds", "reeds");
		m("book", "book");
		m("bowl", "bowl");
		m("redstone", "redstone");
		m("redstone_torch", "redstone_torch");
		m("coal", "coal");
		m("shield", "shield");
		m("bow", "bow");
		m("clay", "clay");
		m("bucket", "bucket");
		m("slime_ball", "slime_ball");
		m("lead", "lead");
		m("fermented_spider_eye", "fermented_spider_eye");
		
		// leather items (common)
		m("leather_chestplate", "leather_chestplate");
		m("leather_leggings", "leather_leggings");
		m("leather_arms", "leather_arms");
		m("leather_helmet", "leather_helmet");
		m("leather_boots", "leather_boots");
		
		// stone items (common)
		m("stone_pickaxe", "stone_pickaxe");
		m("stone_axe", "stone_axe");
		m("stone_sword", "stone_sword");
		
		// gold items (uncommon)
		m("gold_sword", "gold_sword");
		
		// iron items (uncommon)
		m("iron_pickaxe", "iron_pickaxe");
		m("iron_axe", "iron_axe");
		m("iron_chestplate", "iron_chestplate");
		m("iron_leggings", "iron_leggings");
		m("iron_arms", "iron_arms");
		m("iron_helmet", "iron_helmet");
		m("iron_boots", "iron_boots");
		m("iron_sword", "iron_sword");
		m("iron_ingot", "iron_ingot");
		m("iron_block", "iron_block");
				
		// rare items
		m("tnt", "tnt");
		m("blaze_powder", "blaze_powder");
		m("magma_cream", "magma_cream");
		m("clock", "clock");
		m("compass", "compass");
		m("glowstone", "glowstone");
		m("ender_pearl", "ender_pearl");
		m("ender_eye", "ender_pearl");
		m("blaze_rod", "blaze_rod");
		m("ghast_tear", "ghast_tear");
		m("diamond", "diamond");
		m("prismarine_crystals", "prismarine_crystals");
		m("prismarine_shard", "prismarine_shard");
		m("sea_lantern", "sea_lantern");
		m("nether_star", "nether_star");		
		m("firework_charge", "firework_charge");
		
		// rare food
		m("rabbit_foot", "rabbit_foot");
		m("potato", "potato");
		m("cake", "cake");
		m("pumpkin_pie", "pumpkin_pie");
		m("speckled_melon", "speckled_melon");
		m("beetroot", "beetroot");
		m("beetroot_seeds", "beetroot_seeds");
		
		// rare armor
		m("diamond_chestplate", "diamond_chestplate");
		m("diamond_leggings", "diamond_leggings");
		m("diamond_arms", "diamond_arms");
		m("diamond_helmet", "diamond_helmet");
		m("diamond_boots", "diamond_boots");
		m("diamond_block", "diamond_block");
		m("iron_horse_armor", "iron_horse_armor");
		m("gold_horse_armor", "gold_horse_armor");
		m("end_crystal", "end_crystal");
		m("chorus_fruit_popped", "chorus_fruit_popped");
		
		// rare tools
		m("diamond_pickaxe", "diamond_pickaxe");
		m("diamond_sword", "diamond_sword");
				
		// epic items
		m("shulker_box", "shulker_box");
		m("shulker_shell", "shulker_shell");
		m("dragon_breath", "dragon_breath");
		m("elytra", "elytra");
		m("saddle", "saddle");
		m("emerald", "emerald");
		
		// epic food
		m("golden_apple", "golden_apple");
		m("golden_carrot", "golden_carrot");
		
		// epic armor
		m("diamond_horse_armor", "diamond_horse_armor");
		m("totem_of_undying", "totem_of_undying");
		m("experience_bottle", "experience_bottle");
		
		// records
		m("record_13", "record_13");
		m("record_cat", "record_cat");
		m("record_blocks", "record_blocks");
		m("record_chirp", "record_chirp");
		m("record_far", "record_far");
		m("record_mall", "record_mall");
		m("record_mellohi", "record_mellohi");
		m("record_stal", "record_stal");
		m("record_strad", "record_strad");
		m("record_ward", "record_ward");
		m("record_11", "record_11");
		m("record_wait", "record_wait");
		//		 --- DONE TO HERE --	
		
		// jewels - rare
		m("lapis_ore", "lapis_ore");
		m("dye4", "dye4", 4);

		// TODO potions
		
		//// more common
		// shear
		// flint and steel
		// fire charge

		
		for (ChestItem item : this.itemMap.values()) {
			chestSheet.getItems().put(item.getId(), item);
		}
	}

	/**
	 * Add item to the item map (m=map item)
	 * @param id
	 * @param NAME
	 */
	private void m(String id, String name) {
//		int size = itemMap.size();
//		String newId = String.valueOf(size);
		itemMap.put(id, new ChestItem(id, pf(name)));
	}
	
	private void m(String id, String name, int damage) {
//		int size = itemMap.size();
//		String newId = String.valueOf(size);
		itemMap.put(id, new ChestItem(id, pf(name), damage));
	}
	
	private void m(String id, String name, String modid) {
		int size = itemMap.size();
		String newId = String.valueOf(size);
		itemMap.put(id, new ChestItem(id, pf(name, modid)));
	}
	
	private void m(String id, String name, int damage, String modid) {
		ChestItem ci = new ChestItem(id, pf(name, modid));
		ci.setDamage(damage);
		itemMap.put(id, ci);	
	}

	/**
	 * Prefixes the item NAME with the MODID
	 * @param NAME
	 * @return
	 */
	private static String pf(String name) {
		return "minecraft:" + name;
	}
	
	private static String pf(String name, String modid) {
		return modid + ":" + name;
	}
	
	/**
	 * Builds a list/map of random items - those items that have their own change, min, max, weight and order.
	 * These items are used in groups and reference the items from the item map.
	 */
	private void buildRandomItems() {
		// common
		rm("torch", "torch", 50, 1, 5, 50, 52);
		rm("bone", "bone", 35, 1, 3, 50, 57);
		rm("rotten_flesh", "rotten_flesh", 50, 1, 1, 50, 59);
		rm("arrow", "arrow", 35, 5, 10, 50, 56);
		rm("string", "string", 35, 1, 3, 50, 60);
		rm("wool", "wool", 25, 1, 3, 25, 51);
		rm("leather", "leather", 20, 1,3, 20, 53);
		rm("dye0", "dye0", 20, 1, 3, 20, 54);
		rm("gold_nugget", "gold_nugget", 20, 1, 3, 20, 55);				
		rm("spider_eye", "spider_eye", 35, 1, 3, 35, 58);		
		rm("rabbit_hide", "rabbit_hide", 22, 1, 3, 22, 61);
		rm("iron_ingot", "iron_ingot", 30, 1, 5, 10, 62);

		// common armor ie leather
		rm("leather_chestplate", "leather_chestplate", 15, 1, 1, 20, 300);
		rm("leather_leggings", "leather_leggings", 20, 1, 1, 20, 300);
		rm("leather_arms", "leather_arms", 20, 1, 1, 20, 300);
		rm("leather_helmet", "leather_helmet", 20, 1, 1, 20, 300);
		rm("leather_boots", "leather_boots", 20, 1, 1, 20, 300);
		
		// common tools
		rm("stone_pickaxe", "stone_pickaxe", 25, 1, 1, 25, 100);
		rm("stone_axe", "stone_axe", 25, 1, 1, 25, 101);
		rm("stone_sword", "stone_sword", 25, 1, 1, 25, 102);
		
		// common food
		rm("bread", "bread", 80, 2, 5, 80, 50);
		rm("wheat", "wheat", 80, 2, 5, 80, 50);
		rm("wheat_seeds", "wheat_seeds", 80, 3, 5, 80, 50);
		rm("sugar", "sugar", 20, 1, 3, 20, 50);
		rm("brown_mushroom", "brown_mushroom", 80, 1, 5, 80, 50);
		rm("red_mushroom", "red_mushroom", 80, 1, 5, 80, 50);
		rm("apple", "apple", 80, 1, 5, 80, 50);
		rm("carrot", "carrot", 35, 1, 5, 35, 50);		
		rm("porkchop", "porkchop", 25, 1, 2, 25, 50);
		rm("rabbit", "rabbit", 25, 1, 2, 25, 50);
		rm("egg", "egg", 80, 1, 3, 80, 50);
		rm("chicken", "chicken", 25, 1, 2, 25, 50);
		rm("fish0", "fish0", 35, 1, 5, 35, 50);
		rm("fish1", "fish1", 35, 1, 3, 30, 50);
		rm("beef", "beef", 25, 1, 3, 25, 50);
		rm("cookie", "cookie", 15, 1, 2, 15, 50);
		rm("pumpkin", "pumpkin", 15, 1, 1, 15, 50);
		rm("pumpkin_seeds", "pumpkin_seeds", 50, 1, 2, 50, 50);
		rm("melon", "melon", 10, 1, 1, 10, 50);
		rm("melon_seeds", "melon_seeds", 10, 1, 5, 10, 50);
		rm("mutton", "mutton", 25, 1, 5, 25, 50);
		rm("cocoa", "cocoa", 10, 1, 2, 10, 50);
		
		// uncommon items
		rm("spectral_arrow", "spectral_arrow", 15, 2, 6, 15, 120);
		rm("paper", "paper", 25, 1, 2, 25, 120);
		rm("reeds", "reeds", 50, 1, 2, 50, 120);
		rm("book", "book", 25, 1, 2, 25, 120);
		rm("bowl", "bowl", 30, 1, 1, 30, 120);
		rm("redstone", "redstone", 30, 1, 2, 30, 120);
		rm("redstone_torch", "redstone_torch", 40, 1, 2, 40, 120);
		rm("coal", "coal", 50, 1, 5, 50, 120);
		rm("iron_ingot2", "iron_ingot", 20, 2, 7, 20, 120);
		rm("bow", "bow", 25, 1, 1, 25, 120);
		rm("clay", "clay", 15, 1, 3, 15, 120);
		rm("bucket", "bucket", 50, 1, 1, 50, 120);
		rm("slime_ball", "slime_ball", 35, 1, 3, 35, 120);
		rm("lead", "lead", 35, 1, 3, 35, 120);
		rm("fermented_spider_eye", "fermented_spider_eye", 25, 1, 3, 25, 120);
		
		// uncommon armor
		rm("iron_chestplate", "iron_chestplate", 12.5, 1, 1, 12.5, 122);
		rm("iron_leggings", "iron_leggings", 20, 1, 1, 20, 123);
		rm("iron_boots", "iron_boots", 20, 1, 1, 20, 124);
		rm("iron_helmet", "iron_helmet", 20, 1, 1, 20, 125);
		rm("shield", "shield", 20, 1, 1, 20, 122);

		// uncommon tools
		rm("iron_sword", "iron_sword", 35, 1, 1, 35, 121);
		rm("iron_pickaxe", "iron_pickaxe", 25, 1, 1, 25, 121);
		rm("iron_axe", "iron_axe", 25, 1, 1, 25, 121);
		rm("gold_sword", "gold_sword", 25, 1, 1, 25, 121);
				
		// rare items
		rm("tnt", "tnt", 35, 1, 2, 35, 25);
		rm("blaze_powder", "blaze_powder", 25, 2, 5, 25, 25);
		rm("magma_cream", "magma_cream", 25, 2, 5, 25, 25);
		rm("clock", "clock", 25, 1, 1, 25, 25);
		rm("compass", "compass", 25, 1, 1, 25, 25);
		rm("diamond_block", "diamond_block", 10, 1, 1, 10, 25);
		rm("glowstone", "glowstone", 25, 1, 5, 25, 25);
		rm("ender_pearl", "ender_pearl", 35, 1, 3, 35, 25);
		rm("ender_eye", "ender_eye", 25, 1, 2, 25, 25);
		rm("blaze_rod", "blaze_rod", 25, 1, 3, 25, 25);
		rm("ghast_tear", "ghast_tear", 25, 1, 2, 25, 25);
		rm("diamond", "diamond", 25, 1, 3, 25, 25);
		rm("prismarine_crystals", "prismarine_crystals", 20, 1, 3, 20, 25);
		rm("prismarine_shard", "prismarine_shard", 20, 1, 3, 20, 25);
		rm("sea_lantern", "sea_lantern", 20, 1, 1, 20, 25);
		rm("nether_star", "nether_star", 15, 1, 1, 15, 25);		
		rm("firework_charge", "firework_charge", 35, 1, 5, 35, 25);
		
		// rare food
		rm("rabbit_foot", "rabbit_foot", 25, 1, 1, 25, 50);
		rm("potato", "potato", 35, 1, 5, 35, 50);
		rm("cake", "cake", 35, 1, 1, 35, 50);
		rm("pumpkin_pie", "pumpkin_pie", 45, 1, 1, 45, 50);
		rm("speckled_melon", "speckled_melon", 15, 1, 1, 15, 50);
		rm("beetroot", "beetroot", 50, 1, 5, 50, 50);
		rm("beetroot_seeds", "beetroot_seeds", 50, 1, 3, 50, 50);
		
		// rare armor
		rm("diamond_chestplate", "diamond_chestplate", 18, 1, 1, 18, 35);
		rm("diamond_leggings", "diamond_leggings", 22, 1, 1, 22, 35);
		rm("diamond_helmet", "diamond_helmet", 22, 1, 1, 22, 35);
		rm("diamond_boots", "diamond_boots", 22, 1, 1, 22, 35);
		rm("iron_horse_armor", "iron_horse_armor", 18, 1, 1, 18, 35);
		rm("gold_horse_armor", "gold_horse_armor", 20, 1, 1, 18, 35);

		rm("iron_chestplate_e1", "iron_chestplate", 18, 1, 1, 18, 35, 1, 2);
		rm("iron_leggings_e1", "iron_leggings", 22, 1, 1, 22, 35, 1, 2);
		rm("iron_helmet_e1", "iron_helmet", 22, 1, 1, 22, 35, 1, 2);
		rm("iron_boots_e1", "iron_boots", 22, 1, 1, 22, 35, 1, 2);
		
		// rare tools
		rm("diamond_pickaxe", "diamond_pickaxe", 25, 1, 1, 25, 45);
		rm("diamond_sword", "diamond_sword", 25, 1, 1, 25, 45);
		
		// TODO make a set of common,uncommn and rare item for epic chest that has increased chance
		// TODO OR add an additional property to group for multiplying the chance
		// epic items
		rm("shulker_box", "shulker_box", 30, 1, 1, 15, 0);
		rm("shulker_shell", "shulker_shell", 35, 1, 1, 20, 0);
		rm("dragon_breath", "dragon_breath", 30, 1, 1, 15, 0);
		rm("elytra", "elytra", 15, 1, 1, 10, 0);
		rm("totem_of_undying", "totem_of_undying", 15, 1, 1, 10, 0);
		rm("experience_bottle", "experience_bottle", 75, 1, 3, 75, 0);
		rm("emerald", "emerald", 45, 1, 3, 50, 0);
		rm("end_crystal", "end_crystal", 25, 1, 2, 25, 0);
		rm("chorus_fruit_popped", "chorus_fruit_popped", 35, 1, 3, 25, 0);
		
		// epic food
		rm("golden_apple", "golden_apple", 25, 1, 2, 35, 1);
		rm("golden_carrot", "golden_carrot", 35, 1, 2, 35, 1);
		
		// epic armor
		rm("diamond_chestplate_e1", "diamond_chestplate", 35, 1, 1, 18, 50, 1, 2);
		rm("diamond_leggings_e1", "diamond_leggings", 35, 1, 1, 22, 50, 1, 2);
		rm("diamond_helmet_e1", "diamond_helmet", 35, 1, 1, 22, 50, 1, 2);
		rm("diamond_boots_e1", "diamond_boots", 35, 1, 1, 22, 50, 1, 2);

		rm("iron_chestplate_e2", "iron_chestplate", 35, 1, 1, 35, 1, 2, 3);
		rm("iron_leggings_e2", "iron_leggings", 35, 1, 1, 35, 1, 2, 3);
		rm("iron_helmet_e2", "iron_helmet", 35, 1, 1, 35, 1, 2, 3);
		rm("iron_boots_e2", "iron_boots", 35, 1, 1, 35, 1, 2, 3);
		
		rm("diamond_horse_armor", "diamond_horse_armor", 25, 1, 1, 25, 0);
		
		// epic tools
		
		// records (epic)
		rm("record_13", "record_13", 25, 1, 1, 10, 4);
		rm("record_cat", "record_cat", 25, 1, 1, 10, 4);
		rm("record_blocks", "record_blocks", 25, 1, 1, 10, 4);
		rm("record_chirp", "record_chirp", 25, 1, 1, 10, 4);
		rm("record_far", "record_far", 25, 1, 1, 10, 4);
		rm("record_mall", "record_mall", 25, 1, 1, 10, 4);
		rm("record_mellohi", "record_mellohi", 25, 1, 1, 10, 4);
		rm("record_stal", "record_stal", 25, 1, 1, 10, 4);
		rm("record_strad", "record_strad", 25, 1, 1, 10, 4);
		rm("record_ward", "record_ward", 25, 1, 1, 10, 4);
		rm("record_11", "record_11", 25, 1, 1, 10, 4);
		rm("record_wait", "record_wait", 25, 1, 1, 10, 4);
		
	}
	
	/**
	 * Creates a RandomItem and adds it to the randomItemMap
	 * @param id
	 * @param ref
	 * @param chance
	 * @param min
	 * @param max
	 * @param weight
	 * @param order
	 */
	private void rm(String id, String ref, double chance, double min, double max, double weight, double order) {
		RandomItem ri = new RandomItem();	
		ri.setRef(itemMap.get(ref));
		ri.setChance(chance);
		ri.setQuantity(new Quantity(min, max));
		ri.setWeight(weight);
		ri.setOrder(order);
		randomItemMap.put(id, ri);
	}
	
	private void rm(String id, String ref, double chance, double min, double max, double weight, double order,
			double eMin, double eMax) {
		RandomItem ri = new RandomItem();	
		ri.setRef(itemMap.get(ref));
		ri.setChance(chance);
		ri.setQuantity(new Quantity(min, max));
		ri.setWeight(weight);
		ri.setOrder(order);
		
		Enchants e = new Enchants();
		e.setQuantity(new Quantity(eMin, eMax));
//		for (String eid : enchantmentIds) {
//		ChestItemEnchantment cie = enchantmentsMap.get(eid);
//		e.getEnchantments().add(cie);
//	}
		
		ri.setEnchants(e);
		randomItemMap.put(id, ri);		
	}
	
	/**
	 * Builds a list/map of items that belong to specific groups
	 */
	private void buildItemGroups() {
		// common items
		ig("common_items", "bread");
		ig("common_items", "wool");
		ig("common_items", "torch");
		ig("common_items", "leather");
		ig("common_items", "rabbit_hide");
		ig("common_items", "gold_nugget");
		ig("common_items", "iron_ingot");
		ig("common_items", "arrow");
		ig("common_items", "dye0");
		ig("common_items", "bone");
		ig("common_items", "spider_eye");
		ig("common_items", "rotten_flesh");
		ig("common_items", "string");
		
		// common armor ie leather
		ig("common_armor", "leather_chestplate");
		ig("common_armor", "leather_leggings");
		ig("common_armor", "leather_arms");
		ig("common_armor", "leather_helmet");
		ig("common_armor", "leather_boots");
		
		// common tools
		ig("common_tools", "stone_sword");
		ig("common_tools", "stone_pickaxe");
		ig("common_tools", "stone_axe");
		
		// common food
		ig("common_food", "apple");
		ig("common_food", "wheat");
		ig("common_food", "wheat_seeds");
		ig("common_food", "sugar");
		ig("common_food", "brown_mushroom");
		ig("common_food", "red_mushroom");
		ig("common_food", "carrot");		
		ig("common_food", "porkchop");
		ig("common_food", "rabbit");
		ig("common_food", "egg");
		ig("common_food", "chicken");
		ig("common_food", "fish0");
		ig("common_food", "fish1");
		ig("common_food", "beef");
		ig("common_food", "cookie");
		ig("common_food", "pumpkin");
		ig("common_food", "pumpkin_seeds");
		ig("common_food", "melon");
		ig("common_food", "melon_seeds");
		ig("common_food", "mutton");
		ig("common_food", "cocoa");
		
		// uncommon items
		ig("uncommon_items", "spectral_arrow");
		ig("uncommon_items", "iron_ingot2");
		ig("uncommon_items", "paper");
		ig("uncommon_items", "reeds");
		ig("uncommon_items", "book");
		ig("uncommon_items", "bowl");
		ig("uncommon_items", "redstone");
		ig("uncommon_items", "redstone_torch");
		ig("uncommon_items", "coal");
		ig("uncommon_items", "bow");
		ig("uncommon_items", "clay");
		ig("uncommon_items", "bucket");
		ig("uncommon_items", "slime_ball");
		ig("uncommon_items", "lead");
		ig("uncommon_items", "fermented_spider_eye");
		
		// uncommon armor
		ig("uncommon_armor", "iron_chestplate");
		ig("uncommon_armor", "iron_leggings");
		ig("uncommon_armor", "iron_boots");
		ig("uncommon_armor", "iron_helmet");
		ig("uncommon_armor", "shield");
		
		// uncommon tools
		ig("uncommon_tools", "iron_sword");
		ig("uncommon_tools", "iron_pickaxe");
		ig("uncommon_tools", "iron_axe");
		ig("uncommon_tools", "gold_sword");
				
		// rare items
		ig("rare_items", "tnt");
		ig("rare_items", "blaze_powder");
		ig("rare_items", "magma_cream");
		ig("rare_items", "clock");
		ig("rare_items", "compass");
		ig("rare_items", "diamond_block");
		ig("rare_items", "glowstone");
		ig("rare_items", "ender_pearl");
		ig("rare_items", "ender_pearl");
		ig("rare_items", "blaze_rod");
		ig("rare_items", "ghast_tear");
		ig("rare_items", "experience_bottle");
		ig("rare_items", "diamond");
		ig("rare_items", "prismarine_crystals");
		ig("rare_items", "prismarine_shard");
		ig("rare_items", "sea_lantern");
		ig("rare_items", "nether_star");		
		ig("rare_items", "firework_charge");
		
		// rare armor
		ig("rare_armor", "diamond_chestplate");
		ig("rare_armor", "diamond_leggings");
		ig("rare_armor", "diamond_helmet");
		ig("rare_armor", "diamond_boots");
		ig ("rare_armor", "iron_chestplate_e1");
		ig("rare_armor", "iron_leggings_e1");
		ig("rare_armor", "iron_helmet_e1");
		ig("rare_armor", "iron_boots_e1");
		ig("rare_armor", "iron_horse_armor");
		ig("rare_armor", "gold_horse_armor");
		
		// rare tools
		ig("rare_tools", "diamond_pickaxe");
		ig("rare_tools", "diamond_sword");		
		
		// rare food
		ig("rare_food", "rabbit_foot");
		ig("rare_food", "potato");
		ig("rare_food", "cake");
		ig("rare_food", "pumpkin_pie");
		ig("rare_food", "speckled_melon");
		ig("rare_food", "beetroot");
		ig("rare_food", "beetroot_seeds");
		
		// epic
		ig("epic_items", "shulker_box");
		ig("epic_items", "shulker_shell");
		ig("epic_items", "dragon_breath");
		ig("epic_items", "elytra");
		ig("epic_items", "totem_of_undying");
		ig("epic_items", "experience_bottle");
		ig("epic_items", "emerald");
		ig("epic_items", "end_crystal");
		ig("epic_items", "chorus_fruit_popped");
		// records
		ig("epic_records", "record_13");
		ig("epic_records", "record_cat");
		ig("epic_records", "record_blocks");
		ig("epic_records", "record_chirp");
		ig("epic_records", "record_far");
		ig("epic_records", "record_mall");
		ig("epic_records", "record_mellohi");
		ig("epic_records", "record_stal");
		ig("epic_records", "record_strad");
		ig("epic_records", "record_ward");
		ig("epic_records", "record_11");
		ig("epic_records", "record_wait");
		
		// epic food
		ig("epic_food", "golden_apple");
		ig("epic_food", "golden_carrot");
		
		// epic armor
		ig("epic_armor", "diamond_chestplate_e1");
		ig("epic_armor", "diamond_leggings_e1");
		ig("epic_armor", "diamond_helmet_e1");
		ig("epic_armor", "diamond_boots_e1");
		ig ("epic_armor", "iron_chestplate_e2");
		ig("epic_armor", "iron_leggings_e2");
		ig("epic_armor", "iron_helmet_e2");
		ig("epic_armor", "iron_boots_e2");
		ig("epic_armor", "diamond_horse_armor");
	}
	
	/**
	 * Create a ChestItemGroup with maps a ref of a item to a ref to a group. 
	 * @param group
	 * @param randomItemRef
	 */
	private void ig(String group, String randomItemRef) {
		ChestItemGroup g = chestSheet.getGroups().get(group);
		if (g == null) {
			g = new ChestItemGroup(group);
			this.chestSheet.getGroups().put(group, g);
		}		
		g.getItems().add(randomItemMap.get(randomItemRef));		
	}
	
	/**
	 * Build a list of RandomGroups. A RandomGroup contains a list of RandomItems and 
	 * additional properties such as quantity, multiplier, order, etc.
	 */
	private void buildRandomGroups() {
		rg("rg_common", "common_items", 3, 5, 1, 100);
		rg("rg_common_x2", "common_items", 3, 5, 2, 1.2, 100);
		rg("rg_common_x3", "common_items", 3, 5, 3, 1.3, 100);
		
		rg("rg_common_armor", "common_armor", 1, 2, 1, 110);
		rg("rg_common_tools", "common_tools", 1, 2, 1, 110);
		
		rg("rg_common_food", "common_food", 1, 3, 1, 120);
		rg("rg_common_food_x2", "common_food", 1, 3, 2, 1.2, 120);
		rg("rg_common_food_x3", "common_food", 1, 3, 3, 1.3, 120);
		
		rg("rg_uncommon", "uncommon_items", 2, 4, 1, 50);
		rg("rg_uncommon_x2", "uncommon_items", 2, 5, 2, 1.2, 50);		
		rg("rg_uncommon_x3", "uncommon_items", 3, 5, 3, 1.3, 50);
		rg("rg_uncommon_tools", "uncommon_tools", 1, 2, 1, 60);
		rg("rg_uncommon_armor", "uncommon_armor", 1, 2, 1, 70);
		
		// rare
		rg("rg_rare", "rare_items", 2, 4, 1, 10);
		rg("rg_rare_x2", "rare_items", 2,4, 2, 1.2, 10);
		rg("rg_rare_x3", "rare_items", 1, 3, 3, 1.3, 10);
		rg("rg_rare_armor", "rare_armor", 1, 1, 1, 11);
		rg("rg_rare_food", "rare_food", 2, 4, 1, 12);
		rg("rg_rare_tools", "rare_tools", 1, 1, 1, 13);
		
		// epic
		rg("rg_epic", "epic_items", 3, 5, 1, 0);
		rg("rg_epic_records", "epic_records", 1, 1, 1, 0);
		rg("rg_epic_food", "epic_food", 2, 2, 1, 0);
		rg("rg_epic_armor", "epic_armor", 2, 3, 1, 0);
	}

	/**
	 *  Create a RandomGroup and add it to the randomGroupMap
	 * @param id
	 * @param groupRef
	 * @param min
	 * @param max
	 * @param itemFactor
	 * @param order
	 */
	private void rg(String id, String groupRef, double min, double max, double itemFactor, double order) {
		RandomGroup group = new RandomGroup();
		group.setRef(groupRef);
		group.setQuantity(new Quantity(min, max));
		group.setItemsFactor(itemFactor);
		group.setOrder(order);
		randomGroupMap.put(id, group);		
	}
	
	/**
	 * Create a RandomGroup and add it to the randomGroupMap
	 * @param id
	 * @param groupRef
	 * @param min
	 * @param max
	 * @param itemFactor
	 * @param chanceFactor
	 * @param order
	 */
	private void rg(String id, String groupRef, double min, double max, double itemFactor, double chanceFactor, double order) {
		RandomGroup group = new RandomGroup();
		group.setRef(groupRef);
		group.setQuantity(new Quantity(min, max));
		group.setItemsFactor(itemFactor);
		group.setChanceFactor(chanceFactor);
		group.setOrder(order);
		randomGroupMap.put(id, group);		
	}
	
	/**
	 * Builds a list/map of containers (chests) that contain random groups.
	 */
	private void buildContainers() {
		// common_chest container
		c("common_chest", "common", 50D); // c(NAME, category)
		cg("common_chest", "rg_common"); // cg(chest NAME, random group NAME)
		cg("common_chest", "rg_common_armor");
		cg("common_chest", "rg_common_food");
		cg("common_chest", "rg_common_tools");
		// common_chest container 2
		c("common_chest2", "common", 50D);
		cg("common_chest2", "rg_common_x2");
		cg("common_chest2", "rg_common_armor");
		cg("common_chest2", "rg_common_food_x2");
		cg("common_chest2", "rg_common_tools");
		// common_chest container 3
		c("common_chest3", "common", 50D);
		cg("common_chest3", "rg_common_x3");
		cg("common_chest3", "rg_common_armor");
		cg("common_chest3", "rg_common_food_x3");
		cg("common_chest3", "rg_common_tools");
		
		// uncommon_chest container - has both common and uncommon groups
		c("uncommon_chest", "uncommon", 35.0D);		
		cg("uncommon_chest", "rg_uncommon");
		cg("uncommon_chest", "rg_common_armor");
		cg("uncommon_chest", "rg_common_tools");
		cg("uncommon_chest", "rg_common");
		cg("uncommon_chest", "rg_common_food");
		
		c("uncommon_chest2", "uncommon", 35.0D);		
		cg("uncommon_chest2", "rg_uncommon_x2");
		cg("uncommon_chest2", "rg_common_armor");
		cg("uncommon_chest2", "rg_common_tools");
		cg("uncommon_chest2", "rg_common");
		cg("uncommon_chest2", "rg_common_food");
		
		c("uncommon_chest3", "uncommon", 35.0D);		
		cg("uncommon_chest3", "rg_uncommon_x3");
		cg("uncommon_chest3", "rg_common_armor");
		cg("uncommon_chest3", "rg_common_tools");
		cg("uncommon_chest3", "rg_common");
		cg("uncommon_chest3", "rg_common_food");
		
		// rare_chest container - has common, uncommon and rare groups
		c("rare_chest", "rare", 50.0D);
		cg("rare_chest", "rg_rare");
		cg("rare_chest", "rg_rare_armor");
		cg("rare_chest", "rg_rare_food");
		cg("rare_chest", "rg_rare_tools");
		cg("rare_chest", "rg_uncommon_x2");
		cg("rare_chest", "rg_common_x3");
				
		// epic - has rare and epic items
		c("epic_chest", "epic");
		cg("epic_chest", "rg_epic");
		cg("epic_chest", "rg_epic_food");
		cg("epic_chest", "rg_epic_armor");
		cg("epic_chest", "rg_epic_records");
		cg("epic_chest", "rg_rare_x3");
		cg("epic_chest", "rg_rare_x2");
		cg("epic_chest", "rg_rare");
		cg("epic_chest", "rg_rare_armor");
		cg("epic_chest", "rg_rare_food");
		cg("epic_chest", "rg_rare_tools");
	}
	
	/**
	 * Create a ChestContainer and add it to the containers map
	 * @param NAME
	 * @param category
	 */
	private void c(String name, String category) {
		ChestContainer container = new ChestContainer();
		container.setName(name);		
		container.setCategory(category);
		container.setWeight(0);
		chestSheet.getContainers().put(name, container);
	}

	/**
	 * 
	 * @param NAME
	 * @param category
	 * @param weight
	 */
	private void c(String name, String category, Double weight) {
		ChestContainer container = new ChestContainer();
		container.setName(name);		
		container.setCategory(category);
		container.setWeight(weight);
		chestSheet.getContainers().put(name, container);
	}
	
	/**
	 * Add a RandomGroup to a ChestContainer
	 * @param NAME
	 * @param groupRef
	 */
	private void cg(String name, String groupRef) {
		chestSheet.getContainers().get(name).getRandomGroups().add(this.randomGroupMap.get(groupRef));
	}
	
	/**
	 * Add a RandomItem to a ChestContainer
	 * @param NAME
	 * @param itemRef
	 */
	private void ci(String name, String itemRef) {
		chestSheet.getContainers().get(name).getRandomItems().add(this.randomItemMap.get(itemRef));
	}
}
