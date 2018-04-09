/**
 * 
 */
package com.someguyssoftware.dungeons2;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.someguyssoftware.dungeons2.chest.ChestItem;
import com.someguyssoftware.dungeons2.style.Alias;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.Frame;
import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.dungeons2.style.Style;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class StyleTester {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		GsonBuilder builder = new GsonBuilder();
		
		// register custom serializer with the builder
		Type themeType = new TypeToken<Map<String, Theme>>() {}.getType();
		builder.registerTypeAdapter(themeType, new ThemeDeserializer());

		Type styleType = new TypeToken<Map<String, Style>>() {}.getType();
		builder.registerTypeAdapter(styleType, new StyleDeserializer());
		
		Type layoutType = new TypeToken<Map<String, Layout>>() {}.getType();
		builder.registerTypeAdapter(layoutType, new LayoutDeserializer());
		
		/*
		 *  create a serializer for ChestItem.
		 *  this serializer doesn't include the ID of the chestItem in the JSON object since the ID is used as the key to the map.
		 */
		JsonSerializer<Theme> themeSerializer = new JsonSerializer<Theme>() {  
		    @Override
		    public JsonElement serialize(Theme src, Type typeOfSrc, JsonSerializationContext context) {
		        JsonObject themeObj = new JsonObject();
//		        themeObj.addProperty("name", src.getName());
		        
		        JsonObject aliasMapObj = new JsonObject();
		        for (Map.Entry<String, Alias> entry : src.getAliases().entrySet()) {
		        	JsonObject aliasObj = new JsonObject();
		        	aliasObj.addProperty("style", entry.getValue().getStyle());
		        	aliasMapObj.add(entry.getKey(), aliasObj);
		        }
		        themeObj.add("aliases", aliasMapObj);
		        return themeObj;
		    }
		};
		
		JsonSerializer<Style> styleSerializer = new JsonSerializer<Style>() {  
		    @Override
		    public JsonElement serialize(Style src, Type typeOfSrc, JsonSerializationContext context) {
		        JsonObject styleObj = new JsonObject();
		        styleObj.addProperty("block", src.getBlock());
		        styleObj.addProperty("category", src.getCategory());
		        styleObj.addProperty("decay", src.getDecay());
		        
		        JsonArray blockArray = new JsonArray();
		        for (String s : src.getDecayBlocks()) {
		        	blockArray.add(new JsonPrimitive(s));
		        }
		        styleObj.add("decayBlocks", blockArray);
		        return styleObj;
		    }
		};
		
		JsonSerializer<Layout> layoutSerializer = new JsonSerializer<Layout>() {  
		    @Override
		    public JsonElement serialize(Layout src, Type typeOfSrc, JsonSerializationContext context) {
		        JsonObject obj = new JsonObject();
		        obj.addProperty("ref", src.getRef());
		        obj.addProperty("category", src.getCategory());
		        obj.addProperty("useAll", src.isUseAll());
		        obj.addProperty("__comment", src.getComment());
		        
		        Type frameType = new TypeToken<Map<String, Frame>>() {}.getType();
		        JsonElement frames = context.serialize(src.getFrames(), frameType);
//		        JsonObject mapObj = new JsonObject();
//		        for (Map.Entry<String, Frame> entry : src.getFrames().entrySet()) {
//		        	JsonObject aliasObj = new JsonObject();
//		        	aliasObj.addProperty("style", entry.getValue().getStyle());
//		        	mapObj.add(entry.getKey(), aliasObj);
//		        }
		        obj.add("frames", frames);
		        return obj;
		    }
		};
		
		// register custom serializer with the builder
		builder.registerTypeAdapter(Theme.class, themeSerializer);
		builder.registerTypeAdapter(Style.class, styleSerializer);
		builder.registerTypeAdapter(Layout.class, layoutSerializer);
		
		builder.setPrettyPrinting();
		Gson gson = builder.create();
		
		InputStream is = Dungeons2.instance.getClass().getResourceAsStream("/resources/stylesheet2.json");
		// ensure that the path actually points to a file

//		Path path = Paths.get("E:/Minecraft/mods/dungeons2/styleSheet.json").toAbsolutePath();
//		
//		
//		if (is == null) {
//			System.out.println("input stream is null");
//		}
		Reader r = new InputStreamReader(is);
		JsonReader reader = null;
		try {
//			reader = new JsonReader(new FileReader(path.toFile()));
			reader = new JsonReader(r);
		}
		catch(Exception e) {}
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		if (r == null) {
//			System.out.println("reader is null");
//		}
//		JsonReader reader = new JsonReader(r);
//		
		
		StyleSheet sheet0 =gson.fromJson(reader, StyleSheet.class);
		
				//gson.fromJson(jsonReader, StyleSheet.class);
		
		System.out.println("sheet0: " + sheet0);
		
//		StyleSheet sheet = new StyleSheet();
//		
//		Style style = new Style();
//		style.setName("basic-dungeon")
//		.setDecay(25.0)
//		.setBlock("minecraft:stonebrick@0");
//		style.getDecayBlocks().add("minecraft:dirt@0");
//		style.getDecayBlocks().add("minecraft:gravel@0");
//		
//		sheet.getStyles().put(style.getName(), style);
//		
//		Frame frame = new Frame().setStyle("basic-dungeon").setAlias("room1");
//		
//		Layout layout = new Layout().setName("basic-room");
//		layout.getFrames().put(DesignElement.FLOOR.name(), frame);
//		layout.getFrames().put(DesignElement.WALL.name(), frame);
//		layout.getFrames().put(DesignElement.CEILING.name(), frame);
////		.setFloor(frame)
////		.setWalls(frame)
////		.setCeiling(frame);
//		
//		sheet.getLayouts().put(layout.getName(), layout);
//		
//		Alias alias = new Alias("room1", "basic-dungeon");
//		
//		Theme theme = new Theme("basic");
//		theme.getAliases().put(alias.getAlias(), alias);
//		
//		sheet.getThemes().put(theme.getName(), theme);
		
		String json = gson.toJson(sheet0);		
		
		System.out.println("json: " + json);
	}

	/**
	 * 
	 * @author Mark Gottschling on Jan 26, 2017
	 *
	 */
	public static class ThemeDeserializer implements JsonDeserializer<Map<String, Theme>> {
		@Override
		public Map<String, Theme> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			Map<String, Theme> map = new HashMap<String, Theme>();

			JsonObject jsonObj = json.getAsJsonObject();
		    for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet())  {
		        JsonObject element = entry.getValue().getAsJsonObject();
		        Theme theme = new Theme();
		        theme.setName(entry.getKey());
		        JsonObject aliasMap = element.get("aliases").getAsJsonObject();
		        Map<String, Alias> aliases = new HashMap<>();
		        for (Map.Entry<String, JsonElement> aliasEntry : aliasMap.entrySet()) {
		        	JsonObject aliasElement = aliasEntry.getValue().getAsJsonObject();
		        	Alias alias = new Alias();
		        	alias.setAlias(aliasEntry.getKey());
		        	alias.setStyle(aliasElement.get("style").getAsString());
		        	aliases.put(alias.getAlias(), alias);
		        }
		        theme.setAliases(aliases);	        
		        map.put(entry.getKey(), theme);
		    }
		    return map;
		}		
	}	

	public static class StyleDeserializer implements JsonDeserializer<Map<String, Style>> {
		@Override
		public Map<String, Style> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			Map<String, Style> map = new HashMap<String, Style>();

			JsonObject jsonObj = json.getAsJsonObject();
		    for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet())  {
		        JsonObject element = entry.getValue().getAsJsonObject();
		        Style style = new Style();
		        style.setName(entry.getKey());
		        style.setCategory(element.get("category").getAsString());
		        style.setBlock(element.get("block").getAsString());
		        style.setDecay(element.get("decay").getAsDouble());
		        
		        JsonArray array = element.getAsJsonArray("decayBlocks");
		        List<String> blocks = new ArrayList<>();
	            for (JsonElement e : array) {
	                blocks.add(e.getAsString());
	            }
		        style.setDecayBlocks(blocks);		        
		        map.put(entry.getKey(), style);
		    }
		    return map;
		}		
	}
	
	public static class LayoutDeserializer implements JsonDeserializer<Map<String, Layout>>{
		@Override
		public Map<String, Layout> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			Map<String, Layout> map = new HashMap<String, Layout>();

			JsonObject jsonObj = json.getAsJsonObject();
		    for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet())  {
		        JsonObject element = entry.getValue().getAsJsonObject();
		        Layout layout = new Layout();
		        layout.setName(entry.getKey());
		        if(element.get("ref") != null)
		        	layout.setRef(element.get("ref").getAsString());
		        if(element.get("category") != null)
		        	layout.setCategory(element.get("category").getAsString());
		        if(element.get("useAll") != null)
		        	layout.setUseAll(element.get("useAll").getAsBoolean());
		        // comment is transient so it isn't going to be copied anyway
		        if(element.get("__comment") != null)
		        	layout.setComment(element.get("__comment").getAsString());
		        
		        // deserialize each frame
		        JsonObject frameMap = element.get("frames").getAsJsonObject();
		        Map<String, Frame> frames = new HashMap<>();
		        for (Map.Entry<String, JsonElement> frameEntry : frameMap.entrySet()) {
		        	JsonObject frameElement = frameEntry.getValue().getAsJsonObject();
		        	Frame frame = context.deserialize(frameElement, Frame.class);
		        	frames.put(frameEntry.getKey(), frame);
		        }
		        layout.setFrames(frames);
		        map.put(layout.getName(), layout);
		    }			
			return map;
		}		
	}
}
