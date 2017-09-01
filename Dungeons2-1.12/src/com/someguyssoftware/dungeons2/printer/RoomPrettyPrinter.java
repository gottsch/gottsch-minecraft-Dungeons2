/**
 * 
 */
package com.someguyssoftware.dungeons2printer;

import java.util.Arrays;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.model.Room;

/**
 * 
 * @author Mark Gottschling on August 25, 2017
 *
 */
public class RoomPrettyPrinter {
	private static final String div;
	
	static {
		// setup a divider line
		char[] chars = new char[75];
		Arrays.fill(chars, '*');
		div = new String(chars) + "\n";	;	
	}
	
	/**
	 * 
	 */
	public RoomPrettyPrinter() {	}
	
	/**
	 * 
	 * @param room
	 * @return
	 */
	public String print(Room room) {
		return print(room, "Room");
	}
	
	/**
	 * 
	 * @param room
	 * @return
	 */
	public String print(Room room, String title) {
		String format = "**    %1$-33s: %2$-30s  **\n";
		String heading = "**  %1$-67s  **\n";
		StringBuilder sb = new StringBuilder();
		
		try {
			sb
			.append(div)
			.append(String.format("**  %-67s  **\n", title))
			.append(div)
			.append(String.format(heading, "[Properties]"))
			.append(String.format(format, "ID", room.getId()))
			.append(String.format(format, "Name", room.getName()))
			.append(String.format(format, "Type", room.getType()))
			.append(String.format(format, "Location", room.getBottomCenter()))
			.append(String.format(format, "X Dimensions", String.format("%s <--> %s", room.getMinX(), room.getMaxX())))
			.append(String.format(format, "Y Dimensions", String.format("%s <--> %s", room.getMinY(), room.getMaxY())))
			.append(String.format(format, "Z Dimensions", String.format("%s <--> %s", room.getMinZ(), room.getMaxZ())))
			.append(div);
		}
		catch(Exception e) {
			return e.getMessage();
		}
		return sb.toString();
	}
}
