/**
 * 
 */
package test.someguyssoftware.dungeons2;

import com.someguyssoftware.dungeons2.config.GeneralConfig;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.dungeons2.spawner.SpawnSheetLoader;

/**
 * @author Mark Gottschling on Feb 13, 2017
 *
 */
public class TestMultiFileSpawnSheet {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			GeneralConfig.dungeonsFolder = "E:/Minecraft/mods/dungeons2/";
			SpawnSheet sheet = SpawnSheetLoader.loadAll();
			assert sheet != null;
			System.out.println("Sheet: " + sheet);
			assert sheet.getGroups().containsKey("test");
			System.out.println(sheet.getGroups().get("test"));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Complete.");
	}

}
