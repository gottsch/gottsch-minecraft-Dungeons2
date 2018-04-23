/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.gottschcore.json.JSMin;

/**
 * @author Mark Gottschling on Aug 2, 2016
 *
 */
public class StyleSheetLoader {
	public static final String BUILT_IN_STYLE_SHEET_PATH = "/stylesheet.json";
	public static final String BUILT_IN_STYLE_SHEET_SUB_FOLDER = "/styleSheets/";
	private static final StyleSheet EMPTY_SHEET = new StyleSheet();

	/**
	 * Load built-in stylesheet from classpath
	 * @return
	 */
	public static StyleSheet load() throws Exception {
		StyleSheet styleSheet = null;

		// read json sheet in and minify it
		InputStream is = Dungeons2.instance.getClass().getResourceAsStream(BUILT_IN_STYLE_SHEET_PATH);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JSMin minifier = new JSMin(is, out);
		minifier.jsmin();

		// out minified json into a json reader
		ByteArrayInputStream in  = new ByteArrayInputStream(out.toByteArray());
		Reader reader = new InputStreamReader(in);
		JsonReader jsonReader = new JsonReader(reader);

		// create a gson builder
		GsonBuilder gsonBuilder = new GsonBuilder();
		/*
		 * create types for all the properties of a StyleSheet
		 */
		Type styleType = new TypeToken<Map<String, Style>>() {}.getType();
		Type layoutType = new TypeToken<Map<String, Layout>>() {}.getType();
		Type themeType = new TypeToken<Map<String, Theme>>() {}.getType();
		/*
		 * register the types with the custom deserializer
		 */
		gsonBuilder.registerTypeAdapter(styleType, new StyleDeserializer());
		gsonBuilder.registerTypeAdapter(layoutType, new LayoutDeserializer());
		gsonBuilder.registerTypeAdapter(themeType, new ThemeDeserializer());
		Gson gson = gsonBuilder.create();		

		// read minified json into gson and generate objects
		try {
			styleSheet = gson.fromJson(jsonReader, StyleSheet.class);
		}
		catch(JsonIOException | JsonSyntaxException e) {
			// TODO change to custom exception
			throw new Exception("Unable to load style sheet.");
		}
		finally {
			// close objects
			try {
				jsonReader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading built-in style sheet.");
			}
		}
		return styleSheet;
	}

	/**
	 * Loads all the style sheets from the path specified in the config
	 * @return
	 * @throws Exception
	 */
	public static StyleSheet loadAll() throws Exception {
		// get the path to the default style sheet
		Path defaultSheetPath = Paths.get(ModConfig.dungeonsFolder, "styleSheet.json").toAbsolutePath();

		Dungeons2.log.debug("Sheets Folder:" + defaultSheetPath.toString());

		// load the default sheet
		final StyleSheet sheet = load(defaultSheetPath.toString());

		if (sheet == null || sheet == EMPTY_SHEET) {
			Dungeons2.log.warn("Unable to locate default style sheet at location " + defaultSheetPath.toString());
			return EMPTY_SHEET;		
		}

		// load any additional style sheets
		Files.newDirectoryStream(Paths.get(ModConfig.dungeonsFolder, BUILT_IN_STYLE_SHEET_SUB_FOLDER), path -> path.toString().endsWith(".json"))	
		.forEach((x) -> {
			try {
				// load the sheet
				StyleSheet s = load(x.toString());
				if (sheet.getThemes() != null && s.getThemes() != null) {
					for(Entry<String, Theme> entry : s.getThemes().entrySet()) {
						// see if the default contains the same mapped theme
						if (sheet.getThemes().containsKey(entry.getKey())) {
							// copy over the individual aliases
							Theme theme = sheet.getThemes().get(entry.getKey());
							theme.getAliases().putAll(entry.getValue().getAliases());
						}
						else {
							sheet.getThemes().put(entry.getKey(), entry.getValue());
						}
					}
				}
				if (sheet.getStyles() != null && s.getStyles() != null) {
					sheet.getStyles().putAll(s.getStyles());
				}
				if (sheet.getLayouts() != null && s.getLayouts() != null) {
					sheet.getLayouts().putAll(s.getLayouts());
				}
			} catch (Exception e) {
				Dungeons2.log.error("Unable to load additional style sheet " + x.toString(), e);
			}
		});

		return sheet;
	}

	/**
	 * Loads a style sheet from the specified path.
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public static StyleSheet load(String filePath) throws Exception {
		StyleSheet styleSheet = null;

		// ensure that the path actually points to a file
		Path path = Paths.get(filePath).toAbsolutePath();
		if (Files.notExists(path)) {
			Dungeons2.log.warn("Unable to locate style sheet using path: " + filePath);
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
		/*
		 * create types for all the properties of a StyleSheet
		 */
		Type styleType = new TypeToken<Map<String, Style>>() {}.getType();
		Type layoutType = new TypeToken<Map<String, Layout>>() {}.getType();
		Type themeType = new TypeToken<Map<String, Theme>>() {}.getType();
		/*
		 * register the types with the custom deserializer
		 */
		gsonBuilder.registerTypeAdapter(styleType, new StyleDeserializer());
		gsonBuilder.registerTypeAdapter(layoutType, new LayoutDeserializer());
		gsonBuilder.registerTypeAdapter(themeType, new ThemeDeserializer());
		Gson gson = gsonBuilder.create();	

		try {
			styleSheet = gson.fromJson(jsonReader, StyleSheet.class);
			Dungeons2.log.debug("Loaded styleSheet:" + styleSheet);
		}
		catch(JsonIOException | JsonSyntaxException e) {
			// TODO change to custom exception
			throw new Exception("Unable to load style sheet.", e);
		}
		finally {
			// close objects
			try {
				reader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading built-in style sheet.");
			}
		}

		return styleSheet;
	}

	/**
	 * 
	 * @param filePath
	 * @return
	 */
	public static boolean hasStyleSheet(String filePath) {
		Path path = Paths.get(filePath).toAbsolutePath();
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
	public static boolean hasStyleSheet(Path path) {
		if (Files.exists(path.toAbsolutePath())) {
			return true;
		}
		return false;
	}

	/**
	 * @param styleSheetFile
	 */
	public static void exposeStyleSheet(String filePath) {
		Path path = Paths.get(filePath).toAbsolutePath();
		if (Files.notExists(path)) {				
			// copy from resource/classpath to file path
			InputStream is = Dungeons2.instance.getClass().getResourceAsStream(StyleSheetLoader.BUILT_IN_STYLE_SHEET_PATH);
			try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
				byte[] buf = new byte[2048];
				int r;
				while ((r = is.read(buf)) != -1) {
					fos.write(buf,  0,  r);
				}
			}
			catch(IOException e) {
				Dungeons2.log.error("Error exposing stylesheet resource to file system.");
			}
		}
	}

	/**
	 * 
	 * @author Mark Gottschling on Jan 23, 2017
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

	/**
	 * 
	 * @author Mark Gottschling on Jan 23, 2017
	 *
	 */
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

	/**
	 * 
	 * @author Mark Gottschling on Jan 23, 2017
	 *
	 */
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
