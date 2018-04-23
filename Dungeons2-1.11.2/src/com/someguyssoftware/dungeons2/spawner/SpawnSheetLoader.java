/**
 * 
 */
package com.someguyssoftware.dungeons2.spawner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.gottschcore.json.JSMin;

/**
 * 
 * @author Mark Gottschling on Jan 16, 2017
 *
 */
public class SpawnSheetLoader {
	public static final String BUILT_IN_SPAWN_SHEET_PATH = "/spawnsheet.json";
	public static final String BUILT_IN_SPAWN_SHEET_SUB_FOLDER = "/spawnSheets/";	
	private static final SpawnSheet EMPTY_SHEET = new SpawnSheet();
	
	/**
	 * Load built-in spawnsheet from classpath
	 * @return
	 */
	public static SpawnSheet load() throws Exception {
		SpawnSheet spawnSheet = null;
		
		// read json sheet in and minify it
		InputStream is = Dungeons2.instance.getClass().getResourceAsStream(BUILT_IN_SPAWN_SHEET_PATH);	
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		JSMin minifier = new JSMin(is, out);
		minifier.jsmin();
	
		// out minified json into a json reader
		ByteArrayInputStream in  = new ByteArrayInputStream(out.toByteArray());
		Reader reader = new InputStreamReader(in);
		JsonReader jsonReader = new JsonReader(reader);
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		Gson gson = gsonBuilder.create();
		
		try {
			spawnSheet = gson.fromJson(jsonReader, SpawnSheet.class);
			Dungeons2.log.debug("Loaded spawnSheet:" + spawnSheet);
		}
		catch(JsonIOException | JsonSyntaxException e) {
			// TODO change to custom exception
			throw new Exception("Unable to load spawn sheet.");
		}
		finally {
			// close objects
			try {
				jsonReader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading built-in spawn sheet.");
			}
		}
		return spawnSheet;
	}
	
	/**
	 * Loads all the spawn sheets from the path specified in the config
	 * @return
	 * @throws Exception
	 */
	public static SpawnSheet loadAll() throws Exception {
		// get the path to the default style sheet
		Path defaultSheetPath = Paths.get(ModConfig.dungeonsFolder, "spawnSheet.json").toAbsolutePath();
		Dungeons2.log.debug("Sheets Folder:" + defaultSheetPath.toString());

		// load the default sheet
		final SpawnSheet sheet = load(defaultSheetPath.toString());

		if (sheet == null || sheet == EMPTY_SHEET) {
			Dungeons2.log.warn("Unable to locate default spawn sheet at location " + defaultSheetPath.toString());
			return EMPTY_SHEET;		
		}

		// load any additional spawn sheets
		Files.newDirectoryStream(Paths.get(ModConfig.dungeonsFolder, BUILT_IN_SPAWN_SHEET_SUB_FOLDER), path -> path.toString().endsWith(".json"))	
		.forEach((path) -> {
			try {
				// load the sheet
				SpawnSheet s = load(path.toString());
				// add everything from sheet into default sheet
				if (sheet.getGroups() != null && s.getGroups() != null) {
					sheet.getGroups().putAll(s.getGroups());
				}
			} catch (Exception e) {
				Dungeons2.log.error("Unable to load additional spawn sheet " + path.toString(), e);
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
	public static SpawnSheet load(String filePath) throws Exception {
		SpawnSheet spawnSheet = null;
		
		// ensure that the path actually points to a file
		Path path = Paths.get(filePath).toAbsolutePath();
		if (Files.notExists(path)) {
			Dungeons2.log.warn("Unable to locate spawn sheet using path: " + filePath);
			// TODO change to throw custom exception ?
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
		Gson gson = gsonBuilder.create();
		
//		Reader reader = new FileReader(path.toString());
		
		try {
			spawnSheet = gson.fromJson(jsonReader, SpawnSheet.class);
			Dungeons2.log.debug("Loaded spawnSheet:" + spawnSheet);
		}
		catch(JsonIOException | JsonSyntaxException e) {
			// TODO change to custom exception
			throw new Exception("Unable to load spawn sheet.");
		}
		finally {
			// close objects
			try {
				reader.close();
			} catch (IOException e) {
				Dungeons2.log.warn("Unable to close JSON Reader when reading built-in spawn sheet.");
			}
		}
		
		return spawnSheet;
	}
	
	/**
	 * 
	 * @param filePath
	 * @return
	 */
	public static boolean hasSpawnSheet(String filePath) {
		Path path = Paths.get(ModConfig.spawnSheetFile).toAbsolutePath();
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
	public static boolean hasSpawnSheet(Path path) {
		if (Files.exists(path.toAbsolutePath())) {
			return true;
		}
		return false;
	}

	/**
	 * @param spawnSheetFile
	 */
	public static void exposeSpawnSheet(String filePath) {
		Path path = Paths.get(filePath).toAbsolutePath();
		if (Files.notExists(path)) {				
			// copy from resource/classpath to file path
			InputStream is = Dungeons2.instance.getClass().getResourceAsStream(SpawnSheetLoader.BUILT_IN_SPAWN_SHEET_PATH);
			try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
				byte[] buf = new byte[2048];
				int r;
				while ((r = is.read(buf)) != -1) {
					fos.write(buf,  0,  r);
				}
			}
			catch(IOException e) {
				Dungeons2.log.error("Error exposing spawnsheet resource to file system.");
			}
		}
	}
}
