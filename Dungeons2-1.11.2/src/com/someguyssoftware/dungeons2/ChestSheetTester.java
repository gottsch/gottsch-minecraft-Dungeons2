/**
 * 
 */
package com.someguyssoftware.dungeons2;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.someguyssoftware.dungeons2.chest.ChestItem;
import com.someguyssoftware.dungeons2.chest.ChestSheet;

/**
 * 
 * @author Mark Gottschling on Jan 23, 2017
 *
 */
public class ChestSheetTester {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		GsonBuilder gsonBuilder = new GsonBuilder();
		JsonDeserializer<Map<String, ChestItem>> deserializer = new JsonDeserializer<Map<String, ChestItem>>() {
			@Override
			public Map<String, ChestItem> deserialize(JsonElement jsonElement, Type typeOfSrc, JsonDeserializationContext context) {
				Map<String, ChestItem> map = new HashMap<String, ChestItem>();

				JsonObject jsonObj = jsonElement.getAsJsonObject();
			    for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet())  {
			        JsonObject element = entry.getValue().getAsJsonObject();
			        ChestItem item = new ChestItem(
			        	entry.getKey(),
			        	element.get("name").getAsString(),
			        	element.get("damage").getAsInt()
	        		);
			        map.put(entry.getKey(), item);
			    }
			    return map;
			}
		};
		
		// register custom serializer with the builder
		Type mapType = new TypeToken<Map<String, ChestItem>>() {}.getType();
		gsonBuilder.registerTypeAdapter(mapType, deserializer);
		Gson gson = gsonBuilder.create();	
		
		InputStream is = Dungeons2.instance.getClass().getResourceAsStream("/resources/chestSheet2.json");
		// ensure that the path actually points to a file

//		Path path = Paths.get("E:/Minecraft/mods/dungeons2/chestSheet2.json").toAbsolutePath();
//		System.out.println("path:" + path.toString());
//		
//		
//		if (is == null) {
//			System.out.println("input stream is null");
//		}
		Reader r = new InputStreamReader(is);
//		JsonReader reader = null;
//		try {
////			reader = new JsonReader(new InputStreamReader(new FileInputStream(path.toString())));
////		reader = new JsonReader(new FileReader(path.toFile()));
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		catch(Exception e) {
//			e.printStackTrace();
//		}
//		if (reader == null) {
//			System.out.println("reader is null");
//		}
		JsonReader reader = new JsonReader(r);
//		
		
		ChestSheet sheet0 =gson.fromJson(reader, ChestSheet.class);
		System.out.println("sheet0: " + sheet0);

	}

}
