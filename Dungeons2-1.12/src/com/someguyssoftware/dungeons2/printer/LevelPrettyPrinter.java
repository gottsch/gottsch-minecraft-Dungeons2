/**
 * 
 */
package com.someguyssoftware.dungeons2.printer;

import java.util.Arrays;

import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.Room;

/**
 */
public class LevelPrettyPrinter implements IPrettyPrinter {
	private static final String div;
	
	private static String format = "**    %1$-33s: %2$-30s  **\n";
	private static String heading = "**  %1$-67s  **\n";
	
	static {
		// setup a divider line
		char[] chars = new char[75];
		Arrays.fill(chars, '*');
		div = new String(chars) + "\n";	;	
	}
	
	/**
	 * 
	 */
	public LevelPrettyPrinter() {	}
	
	/**
	 * 
	 * @param level
	 * @return
	 */
	public String print(Level level) {
		return print(level, "Level");
	}
	
	/**
	 * 
	 * @param level
	 * @return
	 */
	public String print(Level level, String title) {
		IRoomPrettyPrinter roomPrinter = new RoomPrettyPrinter();
		HallwayPrettyPrinter hallwayPrinter = new HallwayPrettyPrinter();
		
		StringBuilder sb = new StringBuilder();
		
		try {
			sb
			.append(div)
			.append(String.format(heading, title))
			.append(div)
			.append(String.format(heading, "[Config]"))
			.append(String.format(format, "# of Degrees", quantityToString(level.getConfig().getDegrees())))
			.append(String.format(format, "# of Rooms", quantityToString(level.getConfig().getNumberOfRooms())))
			.append(String.format(format, "Room Width", quantityToString(level.getConfig().getWidth())))
			.append(String.format(format, "Room Depth", quantityToString(level.getConfig().getDepth())))
			.append(String.format(format, "Room Height", quantityToString(level.getConfig().getHeight())))			
			.append(String.format(format, "Decay Multiplier", level.getConfig().getDecayMultiplier()))
			.append(String.format(format, "Sea Level", level.getConfig().getSeaLevel()))
			.append(String.format(format, "Chest Categories", level.getConfig().getChestCategories()))
			.append(String.format(format, "Chest Frequency",quantityToString(level.getConfig().getChestFrequency())))
			.append(String.format(format, "Spawner Frequency",quantityToString(level.getConfig().getSpawnerFrequency())))
			.append(String.format(format, "# of Webs", quantityToString(level.getConfig().getNumberOfWebs())))
			.append(String.format(format, "Web Frequncey", quantityToString(level.getConfig().getWebFrequency())))
			.append(String.format(format, "# of Vines", quantityToString(level.getConfig().getNumberOfVines())))
			.append(String.format(format, "Vines Frequncey", quantityToString(level.getConfig().getVineFrequency())))
			.append(String.format(format, "# of Puddles", quantityToString(level.getConfig().getNumberOfPuddles())))
			.append(String.format(format, "Puddle Frequncey", quantityToString(level.getConfig().getPuddleFrequency())))

			.append(String.format(heading, "[Properties]"))
			.append(String.format(format, "ID", level.getId()))
			.append(String.format(format, "Name", level.getName()));
			if (level.getStartRoom() != null)
				sb.append(String.format(format, "Location", level.getStartRoom().getBottomCenter().toShortString()));
			
			if (level.getRooms() != null)
				sb.append(String.format(format, "# of Rooms", level.getRooms().size()));
			if (level.getHallways() != null)
				sb.append(String.format(format, "# of Hallways", level.getHallways().size()));
			if (level.getShafts() != null)
				sb.append(String.format(format, "# of Shafts", level.getShafts().size()));
			
			sb.append(String.format(format, "X Dimensions", String.format("%s <--> %s", level.getMinX(), level.getMaxX())))
			.append(String.format(format, "Y Dimensions", String.format("%s <--> %s", level.getMinY(), level.getMaxY())))
			.append(String.format(format, "Z Dimensions", String.format("%s <--> %s", level.getMinZ(), level.getMaxZ())))			
			.append(div);
			
			// start room			
			String room = null;
			if (level.getStartRoom() != null) {
				room = roomPrinter.print(level.getStartRoom(), title + " > Start Room");
				sb.append(room).append("\n");
			}
			if (level.getEndRoom() != null) {
				room = roomPrinter.print(level.getEndRoom(), title + " > End Room");
				sb.append(room).append("\n");
			}
			
			for (Room r : level.getRooms()) {
				if (!r.isStart() && !r.isEnd()) {
					room = roomPrinter.print(r, String.format(title + " > Room [%d]", r.getId()));
					sb.append(room).append("\n");
				}
			}
			
			for (Hallway h : level.getHallways()) {
				room = hallwayPrinter.print(h, String.format(title + " > Hallway [%d]", h.getId()));
				sb.append(room).append("\n");				
			}
		}
		catch(Exception e) {
			return e.getMessage();
		}
		return sb.toString();
	}
}
