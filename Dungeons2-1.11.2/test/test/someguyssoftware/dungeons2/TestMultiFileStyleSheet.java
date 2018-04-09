/**
 * 
 */
package test.someguyssoftware.dungeons2;

import com.someguyssoftware.dungeons2.config.GeneralConfig;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.StyleSheetLoader;

/**
 * @author Mark Gottschling on Feb 13, 2017
 *
 */
public class TestMultiFileStyleSheet {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			GeneralConfig.dungeonsFolder = "E:/Minecraft/mods/dungeons2/";
			StyleSheet sheet = StyleSheetLoader.loadAll();
			assert sheet != null;
			System.out.println("Sheet: " + sheet);
			assert sheet.getStyles().containsKey("test");
			System.out.println(sheet.getStyles().get("test"));
			
			assert sheet.getStyles().containsKey("slab-crown");
			System.out.println(sheet.getStyles().get("slab-crown"));	
			
			assert sheet.getThemes().containsKey("standard");
			assert sheet.getThemes().get("standard").getAliases().containsKey("crown-2");
			System.out.println(sheet.getThemes().get("standard").getAliases().get("crown-2"));
			assert sheet.getThemes().get("standard").getAliases().containsKey("pilaster-1");
			System.out.println(sheet.getThemes().get("standard").getAliases().get("pilaster-1"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Complete.");
	}

}
