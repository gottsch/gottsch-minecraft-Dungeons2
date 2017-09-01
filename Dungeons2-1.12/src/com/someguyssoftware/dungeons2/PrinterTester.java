/**
 * 
 */
package com.someguyssoftware.dungeons2;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.someguyssoftware.dungeons2.model.Door;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.printer.DungeonPrettyPrinter;
import com.someguyssoftware.dungeons2.printer.RoomPrettyPrinter;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.gottschcore.enums.Alignment;
import com.someguyssoftware.gottschcore.enums.Direction;
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
		Room endRoom = new Room();
		Room entrance = new Room();
		Room r1 = new Room();
		Room r2 = new Room();
		
		Door roomDoor1 = new Door();
		Door hallwayDoor1 = new Door();
		Door hallwayDoor2 = new Door();
		Door room2Door1 = new Door();
		
		roomDoor1.setCoords(new Coords(50, 45, 52));
		roomDoor1.setDirection(Direction.EAST);
		room2Door1.setCoords(new Coords(60, 45, 62));
		room2Door1.setDirection(Direction.WEST);
		hallwayDoor1.setCoords(new Coords(50, 45, 52));
		hallwayDoor1.setDirection(Direction.WEST);
		hallwayDoor1.setRoom(r1);
		hallwayDoor2.setCoords(new Coords(60, 45, 62));
		hallwayDoor2.setDirection(Direction.EAST);
		hallwayDoor2.setRoom(r2);

		
		entrance.setCoords(new Coords(10,64,10));
		startRoom.setCoords(new Coords(24, 45, 49));
		startRoom.setWidth(15);
		startRoom.setDepth(15);
		startRoom.setHeight(15);
		startRoom.setAnchor(true);
		startRoom.setStart(true);
		endRoom.setCoords(new Coords(100, 45, 52));
		endRoom.setAnchor(true);
		endRoom.setEnd(true);
		
		r1.getDoors().add(roomDoor1);
		r2.getDoors().add(room2Door1);
		
		Hallway h1 = new Hallway();
		h1.setCoords(new Coords(50, 45, 50));
		h1.setAlignment(Alignment.HORIZONTAL);
		h1.getDoors().add(hallwayDoor1);
		h1.getDoors().add(hallwayDoor2);
		
		roomDoor1.setHallway(h1);
		room2Door1.setHallway(h1);
		
		Theme theme = new Theme();
		theme.setName("standard");
		

		l.getRooms().add(startRoom);
		l.getRooms().add(endRoom);
		l.getRooms().add(r1);
		l.getRooms().add(r2);
		l.setStartRoom(startRoom);
		l.setEndRoom(endRoom);
		l.getHallways().add(h1);
		l.setMinX(-40);
		l.setMaxX(80);
		d.getLevels().add(l);
		d.setTheme(theme);
		d.setEntrance(entrance);
		d.setMinX(-15);
		d.setMaxX(15);
		
		DungeonPrettyPrinter dp = new DungeonPrettyPrinter();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyymmdd");
		
		String filename = String.format("C:/dump-dungeon-%s-%s.txt", 
				formatter.format(new Date()), 
				d.getEntrance().getBottomCenter().toShortString().replaceAll(" ", "-"));
		
		String s = dp.print(d, filename);
		System.out.println(s);
	}

}
