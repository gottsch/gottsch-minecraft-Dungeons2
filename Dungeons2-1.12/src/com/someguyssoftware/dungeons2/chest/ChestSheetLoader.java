/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.GeneralConfig;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.gottschcore.json.JSMin;

/**
 * 
 * @author Mark Gottschling on Sep 3, 2016
 *
 */
public class ChestSheetLoader {
	public static final String BUILT_IN_CHEST_SHEET_PATH = "/chestsheet.json";
	public static final String BUILT_IN_CHEST_SHEET_SUB_FOLDER = "/chestSheets/";	
	private static final ChestSheet EMPTY_SHEET = new ChestSheet();
	
	/**
	 * Load built-in chestsheet from classpath
	 * @return
	 */
	public static ChestSheet load() throws Exception {
		ChestSheet chestSheet = null;
		
		// read json sheet in and minify it
		InputStream is = Dungeons2.instance.getClass().getResourceAsStream(BUILT_IN_CHEST_SHEET_PATH);	
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JSMin minifier = new JSMin(is, out);
		minifier.jsmin();
	
		// out minified json into a json reader
		ByteArrayInputStream in  = new ByteArrayInputStream(out.toByteArray());
		Reader reader = new InputStreamReader(in);
		JsonReader jsonReader = new JsonReader(reader);
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		/*
		 * get a type for Map<String, ChestItem>
		 */
		Type chestItemsType = new TypeToken<Map<String, ChestItem>>() {}.getType();
		/*
		 * register the type with the custom deserializer
		 */
		gsonBuilder.registerTypeAdapter(chestItemsType, new ChestItemDeserializer());
		Gson gson = gsonBuilder.create();
		
		try {
			chestSheet = gson.fromJson(jsonReader, ChestSheet.class);
			Dungeons2.log.debug("Loaded chestSheet:" + chestSheet);
		}
		catch(JsonIOException | JsonSyntaxException e) {
			// TODO change to custom exception
			throw new Exception("Unable to load chest sheet.");
		}
		finally {
			// close objects
			try {
				jsonReader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading built-in chest sheet.");
			}
		}
		return chestSheet;
	}
	
	/**
	 * Loads all the style sheets from the path specified in the config
	 * @return
	 * @throws Exception
	 */
	public static ChestSheet loadAll() throws Exception {
		// get the path to the default style sheet
		Path defaultSheetPath = Paths.get(GeneralConfig.dungeonsFolder, "chestSheet.json").toAbsolutePath();

		Dungeons2.log.debug("Sheets Folder:" + defaultSheetPath.toString());

		// load the default sheet
		final ChestSheet sheet = load(defaultSheetPath.toString());

		if (sheet == null || sheet == EMPTY_SHEET) {
			Dungeons2.log.warn("Unable to locate default style sheet at location " + defaultSheetPath.toString());
			return EMPTY_SHEET;		
		}

		// load any additional style sheets
		Files.newDirectoryStream(Paths.get(GeneralConfig.dungeonsFolder, BUILT_IN_CHEST_SHEET_SUB_FOLDER), path -> path.toString().endsWith(".json"))	
		.forEach((path) -> {
			try {
				// load the sheet
				ChestSheet s = load(path.toString());
				// add everything from sheet into default sheet
				if (sheet.getContainers() != null && s.getContainers() != null) {
					sheet.getContainers().putAll(s.getContainers());
				}
				if (sheet.getGroups() != null && s.getGroups() != null) {
					sheet.getGroups().putAll(s.getGroups());
				}
				if (sheet.getItems() != null && s.getItems() != null) {
					sheet.getItems().putAll(s.getItems());
				}
			} catch (Exception e) {
				Dungeons2.log.error("Unable to load additional chest sheet " + path.toString(), e);
			}
		});
		return sheet;
	}
	
	/**
	 * 
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public static ChestSheet load(String filePath) throws Exception {
		ChestSheet chestSheet = null;
		
		// ensure that the path actually points to a file
		Path path = Paths.get(filePath).toAbsolutePath();
		if (Files.notExists(path)) {
			Dungeons2.log.warn("Unable to locate chest sheet using path: " + filePath);
			return EMPTY_SHEET;
		}

		InputStream is = new FileInputStream(path.toString());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JSMin minifier = new JSMin(is, out);
		minifier.jsmin();
		
		ByteArrayInputStream in  = new ByteArrayInputStream(out.toByteArray());
		Reader reader = new InputStreamReader(in);
		JsonReader jsonReader = new JsonReader(reader);
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		Type chestItemsType = new TypeToken<Map<String, ChestItem>>() {}.getType();
		gsonBuilder.registerTypeAdapter(chestItemsType, new ChestItemDeserializer());		
		Gson gson = gsonBuilder.create();
		
		try {
			chestSheet = gson.fromJson(jsonReader, ChestSheet.class);
			Dungeons2.log.debug("Loaded chestSheet:" + chestSheet);
		}
		catch(JsonIOException | JsonSyntaxException e) {
			// TODO change to custom exception
			throw new Exception("Unable to load chest sheet.");
		}
		finally {
			// close objects
			try {
				reader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading built-in chest sheet.");
			}
		}		
		return chestSheet;
	}
	
	/**
	 * 
	 * @param filePath
	 * @return
	 */
	public static boolean hasChestSheet(String filePath) {
		Path path = Paths.get(ModConfig.chestSheetFile).toAbsolutePath();
		if (Files.exists(path)) {
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param path
	 * @return
	 */
	public static boolean hasChestSheet(Path path) {
		if (Files.exists(path.toAbsolutePath())) {
			return true;
		}
		return false;
	}

	/**
	 * @param chestSheetFile
	 */
	public static void exposeChestSheet(String filePath) {
		Path path = Paths.get(filePath).toAbsolutePath();
		if (Files.notExists(path)) {				
			// copy from resource/classpath to file path
			InputStream is = Dungeons2.instance.getClass().getResourceAsStream(ChestSheetLoader.BUILT_IN_CHEST_SHEET_PATH);
			try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
				byte[] buf = new byte[2048];
				int r;
				while ((r = is.read(buf)) != -1) {
					fos.write(buf,  0,  r);
				}
			}
			catch(IOException e) {
				Dungeons2.log.error("Error exposing chestsheet resource to file system.");
			}
		}
	}
	
	/**
	 * 
	 * @author Mark Gottschling on Feb 13, 2017
	 *
	 */
	public static class 	ChestItemDeserializer implements	JsonDeserializer<Map<String, ChestItem>> {
		@Override
		public Map<String, ChestItem> deserialize(JsonElement jsonElement, Type typeOfSrc, JsonDeserializationContext context) {
			Map<String, ChestItem> map = new HashMap<String, ChestItem>();

			JsonObject jsonObj = jsonElement.getAsJsonObject();
		    for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet())  {
		        JsonObject element = entry.getValue().getAsJsonObject();
		        ChestItem item = new ChestItem(
		        	entry.getKey(),
		        	element.get("NAME").getAsString(),
		        	element.get("damage").getAsInt()
        		);
		        map.put(entry.getKey(), item);
		    }
		    return map;
		}
	}
}
