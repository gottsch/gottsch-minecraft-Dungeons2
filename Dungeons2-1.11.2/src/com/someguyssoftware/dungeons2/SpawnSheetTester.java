/**
 * 
 */
package com.someguyssoftware.dungeons2;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.someguyssoftware.dungeons2.spawner.SpawnGroup;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class SpawnSheetTester {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		GsonBuilder gsonBuilder = new GsonBuilder();
		Gson gson = gsonBuilder.create();	
		
//		InputStream is = Dungeons2.instance.getClass().getResourceAsStream("/resources/spawnSheet.json");
		// ensure that the path actually points to a file

		Path path = Paths.get("E:/Minecraft/mods/dungeons2/spawnSheet.json").toAbsolutePath();
		System.out.println("path:" + path.toString());
//		
//		
//		if (is == null) {
//			System.out.println("input stream is null");
//		}
//		Reader reader = new InputStreamReader(is);
		JsonReader reader = null;
		try {
//			reader = new JsonReader(new InputStreamReader(new FileInputStream(path.toString())));
		reader = new JsonReader(new FileReader(path.toFile()));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if (reader == null) {
			System.out.println("reader is null");
		}
//		JsonReader reader = new JsonReader(r);
//		
		
		SpawnSheet sheet0 =gson.fromJson(reader, SpawnSheet.class);
		
		List<SpawnGroup> groups = new ArrayList<>(sheet0.getGroups().values());
		
				//gson.fromJson(jsonReader, SpawnerSheet.class);
		
		System.out.println("sheet0: " + sheet0);
		
		SpawnSheet sheet = new SpawnSheet();
		
		SpawnGroup group = new SpawnGroup();
		group.setName("common-mobs1");
		group.setCategory("common");
		group.setChance(5.0);
		group.setWeight(50D);
		group.setLevel(new Quantity(1, 5));
		List<String> mobs = new ArrayList<>();
		mobs.add("zombie");
		mobs.add("skeleton");
		group.setMobs(mobs);

		sheet.getGroups().put("common-mobs1", group);
		
		String json = gson.toJson(sheet);		
		
		System.out.println("json >> " + json);
	}

}
