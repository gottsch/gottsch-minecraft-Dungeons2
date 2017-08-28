/**
 * 
 */
package com.someguyssoftware.dungeons2;

import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeons2printer.DungeonPrettyPrinter;
import com.someguyssoftware.dungeons2printer.RoomPrettyPrinter;
import com.someguyssoftware.gottschcore.positional.Coords;


/**
 * @author Mark
 *
 */
public class PrinterTester {

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		
		DungeonConfig dc = new DungeonConfig();
		Dungeon d = new Dungeon(dc);
		
		LevelConfig lc = new LevelConfig();
		Level l = new Level();
		l.setConfig(lc);
		
		Room r = new Room();
		Room startRoom = new Room();
		
		Room entrance = new Room();
		entrance.setCoords(new Coords(10,64,10));
		startRoom.setCoords(new Coords(24, 45, 49));
		
		Theme theme = new Theme();
		theme.setName("standard");
		
		l.getRooms().add(r);
		l.getRooms().add(startRoom);
		l.setStartRoom(startRoom);
		l.setMinX(-40);
		l.setMaxX(80);
		d.getLevels().add(l);
		d.setTheme(theme);
		d.setEntrance(entrance);
		d.setMinX(-15);
		d.setMaxX(15);
		
		DungeonPrettyPrinter dp = new DungeonPrettyPrinter();
		String s = dp.print(d, "C:/");
		System.out.println(s);
	}

}
