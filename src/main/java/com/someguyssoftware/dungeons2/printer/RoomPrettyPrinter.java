/**
 * 
 */
package com.someguyssoftware.dungeons2.printer;

import java.util.Arrays;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.model.Door;
import com.someguyssoftware.dungeons2.model.Room;

/**
 * 
 * @author Mark Gottschling on August 25, 2017
 *
 */
public class RoomPrettyPrinter {
	private static final String div;
	private static final String sub;
	
	private static String format = "**    %1$-33s: %2$-30s  **\n";
	private static String format2 = "**++    %1$-31s: %2$-28s  ++**\n";
	private static String heading = "**  %1$-67s  **\n";
	private static String heading2 = "**++  %1$-63s  ++**\n";
	
	static {
		// setup a divider line
		char[] chars = new char[75];
		Arrays.fill(chars, '*');
		div = new String(chars) + "\n";
		Arrays.fill(chars, '+');
		chars[0] = chars[1] = chars[73] = chars[74] = '*';
		sub = new String(chars) + "\n";
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

		StringBuilder sb = new StringBuilder();
		
		try {
			sb
			.append(div)
			.append(String.format(heading, title))
			.append(div)
			.append(String.format(heading, "[Properties]"))
			.append(String.format(format, "ID", room.getId()))
			.append(String.format(format, "Name", room.getName()));
			if (room.getCoords() != null)
				sb.append(String.format(format, "Location", room.getBottomCenter().toShortString()));
			
			sb.append(String.format(format, "Type", room.getType()))			
			.append(String.format(format, "Direction", room.getDirection()))
			.append(String.format(format, "Degrees", room.getDegrees()))
			.append(String.format(format, "Is Anchor", room.isAnchor()))
			.append(String.format(format, "Is Obstacle", room.isObstacle()))
			.append(String.format(format, "Is Rejected", room.isReject()));
			
			if (room.getLayout() != null)
				sb.append(String.format(format, "Layout", room.getLayout().getName()));
			
			sb.append(String.format(format, "X Dimensions", String.format("%s <--> %s", room.getMinX(), room.getMaxX())))
			.append(String.format(format, "Y Dimensions", String.format("%s <--> %s", room.getMinY(), room.getMaxY())))
			.append(String.format(format, "Z Dimensions", String.format("%s <--> %s", room.getMinZ(), room.getMaxZ())));
			
			if (room.getDoors() != null) {
				sb.append(String.format(format, "# of Doors", room.getDoors().size()));
				for (Door d : room.getDoors()) {
					sb.append(sub)
					.append(String.format(heading2, "[Door]"))
					.append(String.format(format2, "Location", d.getCoords().toShortString()))
					.append(String.format(format2, "Direction", d.getDirection()));
					if (d.getHallway() != null) {
						sb.append(String.format(format2, "Leads To Hallway", d.getHallway().getId()));
											
						if (d.getHallway().getDoors().size() > 1) {
							sb.append(String.format(heading2, "[Hallway]"));
							// get the other door
							Door d2 = null;
							if (d.getHallway().getDoors().get(0).getRoom() != room) d2 = d.getHallway().getDoors().get(0);
							else d2 = d.getHallway().getDoors().get(1);
							sb.append(String.format(format2, "Leads To Room", d2.getRoom().getId()));
						}
						else {
							sb.append("Error with hallway.");
						}
					}
				}
			}
			
			sb.append(div);
		}
		catch(Exception e) {
			return e.getMessage();
		}
		return sb.toString();
	}
}
