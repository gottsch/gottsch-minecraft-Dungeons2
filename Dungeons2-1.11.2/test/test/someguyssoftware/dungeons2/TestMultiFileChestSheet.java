/**
 * 
 */
package test.someguyssoftware.dungeons2;

import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.chest.ChestSheetLoader;
import com.someguyssoftware.dungeons2.config.GeneralConfig;

/**
 * @author Mark Gottschling on Feb 13, 2017
 *
 */
public class TestMultiFileChestSheet {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			GeneralConfig.dungeonsFolder = "E:/Minecraft/mods/dungeons2/";
			ChestSheet sheet = ChestSheetLoader.loadAll();
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
