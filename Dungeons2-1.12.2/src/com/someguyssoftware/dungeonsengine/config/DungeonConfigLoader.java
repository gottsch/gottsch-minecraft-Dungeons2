/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeonsengine.json.GenericDeserializer;
import com.someguyssoftware.gottschcore.json.JSMin;

/**
 * @author Mark Gottschling on Dec 18, 2018
 *
 */
public class DungeonConfigLoader {
	private static final String DUNGEON_CONFIGS_RESOURCE_DEFAULT_PATH = "/assets/dungeons2/dungeons/";
	private static final String DUNGEON_CONFIGS_RESOURCE_BUILTIN_PATH = "/assets/dungeons2/dungeons/builtin/";
	private static final String DEFAULT_CONFIG_FILE = "default.json";
	private static final String DUNGEON_CONFIGS_FS_PATH = "dungeons";
	private static final List<IDungeonConfig>EMPTY_CONFIG_LIST = new ArrayList<>(0);
		
	static {
		// create folder
		createFolder();
		
		// expose resource configs if not existing on file system
		exposeDungeonsConfigs();
	}
	
	/**
	 * 
	 */
	public DungeonConfigLoader() {
	}

	/**
	 * 
	 * @return
	 * @throws Exception
	 */
	public static List<IDungeonConfig> loadAll() {
		List<IDungeonConfig> configs = new ArrayList<>(5);
		
		// load all the files under the folder
		Path path = Paths.get("mods", Dungeons2.MODID, DUNGEON_CONFIGS_FS_PATH).toAbsolutePath();
				
		// check if path/folder exists
		if (Files.notExists(path)) {
			Dungeons2.log.debug("Unable to locate -> {}", path.toString());
			return EMPTY_CONFIG_LIST;
		}

		try {
			Files.walk(path).filter(Files::isRegularFile).forEach(
					f -> {
						List<IDungeonConfig> dungeonConfig = null;
						try {
							dungeonConfig = load(f);
							configs.addAll(dungeonConfig);
						} catch (Exception e) {
							Dungeons2.log.error("Unable to load dungeon config json file -> " + f.toString(), e);
						}
					});
		} catch (Exception e) {
			Dungeons2.log.error("Unable to read dungeon config file.", e);
		}
		
		return configs;
	}
	
	/**
	 * Load defaul dungeon/level config from resource path (mod jar file)
	 * @return
	 * @throws Exception
	 */
	public static IDungeonConfig loadDefault() throws Exception {
		IDungeonConfig config = null;
		
		// read json sheet in and minify it
		InputStream is = Dungeons2.instance.getClass().getResourceAsStream(DUNGEON_CONFIGS_RESOURCE_DEFAULT_PATH + DEFAULT_CONFIG_FILE);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JSMin minifier = new JSMin(is, out);
		minifier.jsmin();

		// out minified json into a json reader
		ByteArrayInputStream in  = new ByteArrayInputStream(out.toByteArray());
		Reader reader = new InputStreamReader(in);
		JsonReader jsonReader = new JsonReader(reader);

		// create a gson builder
		GsonBuilder builder = new GsonBuilder();
		
		builder.registerTypeAdapter(ILevelConfig.class, new GenericDeserializer(LevelConfig.class));

		Gson gson = builder.create();	
		
		// read minified json into gson and generate objects
		try {
			config = gson.fromJson(jsonReader, DungeonConfig.class);
		}
		catch(JsonIOException | JsonSyntaxException e) {
			throw new Exception("Unable to load default dungeon config.", e);
		}
		finally {
			// close objects
			try {
				jsonReader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading built-in style sheet.");
			}
		}
		return config;		
	}
	
	/**
	 * 
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public static List<IDungeonConfig> load(Path path) throws Exception {
		InputStream is = new FileInputStream(path.toString());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JSMin minifier = new JSMin(is, out);
		minifier.jsmin();

		ByteArrayInputStream in  = new ByteArrayInputStream(out.toByteArray());
		Reader reader = new InputStreamReader(in);
		JsonReader jsonReader = new JsonReader(reader);
		
		// create a gson builder
		GsonBuilder builder = new GsonBuilder();	
		builder.registerTypeAdapter(IDungeonConfig.class, new GenericDeserializer(DungeonConfig.class));
		builder.registerTypeAdapter(ILevelConfig.class, new GenericDeserializer(LevelConfig.class));

		Gson gson = builder.create();	
		List<IDungeonConfig> config = null;
		try {
			Type listType = new TypeToken<List<IDungeonConfig>>() {}.getType();
			config = gson.fromJson(jsonReader, /*DungeonConfig.class*/listType);
			for (IDungeonConfig c : config) {
				Dungeons2.log.debug("loaded dungeon config -> {}", c);
				if (c.getBiomeWhiteList() != null) c.getBiomeWhiteList().replaceAll(String::toUpperCase);
				if (c.getBiomeBlackList() != null) c.getBiomeBlackList().replaceAll(String::toUpperCase);
				for (ILevelConfig lc : c.getLevelConfigs()) {
					if (lc.getChestCategories() != null) lc.getChestCategories().replaceAll(String::toUpperCase);
				}
				Dungeons2.log.debug("Loaded dungeon config:" + config);
				
			}
		}
		catch(JsonIOException | JsonSyntaxException e) {
			throw new Exception("Unable to load dungeon config.", e);
		}
		finally {
			// close objects
			try {
				reader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading dungeon config.");
			}
		}
		return config;
	}
	
	/**
	 * 
	 */
	private static void createFolder() {
		Path modsPath = Paths.get("mods"); // <-- TODO update config like Dungeons2's
		Path folder = Paths.get(modsPath.toString(), Dungeons2.MODID, DUNGEON_CONFIGS_FS_PATH).toAbsolutePath();
		
	    if(Files.notExists(folder)){
	        Dungeons2.log.debug("dungeons config folder \"{}\" will be created.", folder.toString());
	        try {
				Files.createDirectories(folder);
				
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to create dungeons config folder \"{}\"", folder.toString());
			}
	    }
	}
	
	/**
	 * 
	 */
	private static void exposeDungeonsConfigs() {
		Stream<Path> walk = null;
		Path folder = null;
		
		FileSystem fs = getResourceAsFileSystem(DUNGEON_CONFIGS_RESOURCE_BUILTIN_PATH);
		if (fs == null) return;
		
		try {
			// get the base path of the resource 
			Path resourceBasePath = fs.getPath(DUNGEON_CONFIGS_RESOURCE_BUILTIN_PATH);
			Dungeons2.log.debug("resource base path -> {}", resourceBasePath.toString());
						
			boolean isFirst = true;
			// proces all the files in the folder			
			walk = Files.walk(resourceBasePath, 1);
			for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
				Path resourceFilePath = it.next();
				Dungeons2.log.debug("dungeon config file -> {}", resourceFilePath.toString());
				// check the first file, which is actually the given directory itself
				if (isFirst) {
					folder = Paths.get(
							"mods", 
							Dungeons2.MODID, 
							DUNGEON_CONFIGS_FS_PATH)
							.toAbsolutePath();
				}
				else {
					// test if file exists on the file system
					Path dungeonConfigPath = Paths.get(folder.toString(), resourceFilePath.getFileName().toString()).toAbsolutePath();
					Dungeons2.log.debug("dungeonConfigPath -> {}", dungeonConfigPath.toString());
					
					if(Files.notExists(dungeonConfigPath)) {
						// copy from resource/classpath to file path
						InputStream is = Dungeons2.class.getResourceAsStream(resourceFilePath.toString());
						try (FileOutputStream fos = new FileOutputStream(dungeonConfigPath.toFile())) {
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
				isFirst = false;
			}
		}
		catch(Exception e) {
			Dungeons2.log.error("error:", e);
		}
		finally {
			// close the stream
			if (walk != null) {
				walk.close();
			}
		}
		
		// close the file system
		if (fs != null && fs.isOpen()) {
			try {
				fs.close();
			} catch (IOException e) {
				Dungeons2.log.debug("An error occurred attempting to close the FileSystem:", e);
			}
		}		
	}
	
	/**
	 * 
	 * @param location
	 * @return
	 */
	private static FileSystem getResourceAsFileSystem(String location) {
		FileSystem fs = null;
		Map<String, String> env = new HashMap<>();
		URI uri = null;

		// get the asset resource folder that is unique to this mod
		URL url = Dungeons2.class.getResource(location);
		if (url == null) {
			Dungeons2.log.error("Unable to locate resource {}", location);
			return null;
		}

		// convert to a uri
		try {
			uri = url.toURI();
		}
		catch(URISyntaxException e) {
			Dungeons2.log.error("An error occurred during dungeon config processing:", e);
			return null;
		}

		// split the uri into 2 parts - jar path and folder path within jar
		String[] array = uri.toString().split("!");
		try {
			fs = FileSystems.newFileSystem(URI.create(array[0]), env);
		}
		catch(IOException e) {
			Dungeons2.log.error("An error occurred during dungeon config processing:", e);
			return null;
		}
		
		return fs;
	}
}
