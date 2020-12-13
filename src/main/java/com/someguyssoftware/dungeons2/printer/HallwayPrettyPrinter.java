/**
 * 
 */
package com.someguyssoftware.dungeons2.printer;

import java.util.Arrays;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.model.Door;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.Room;

/**
 * 
 * @author Mark Gottschling on Aug 30, 2017
 *
 */
public class HallwayPrettyPrinter {
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
	public HallwayPrettyPrinter() {	}
	
	/**
	 * 
	 * @param hallway
	 * @return
	 */
	public String print(Hallway hallway) {
		return print(hallway, "Hallway");
	}
	
	/**
	 * 
	 * @param hallway
	 * @return
	 */
	public String print(Hallway hallway, String title) {

		StringBuilder sb = new StringBuilder();
		
		try {
			sb
			.append(div)
			.append(String.format(heading, title))
			.append(div)
			.append(String.format(heading, "[Properties]"))
			.append(String.format(format, "ID", hallway.getId()))
			.append(String.format(format, "Name", hallway.getName()));
			if (hallway.getCoords() != null)
				sb.append(String.format(format, "Location", hallway.getBottomCenter().toShortString()));
			
			sb.append(String.format(format, "Type", hallway.getType()))
			.append(String.format(format, "Alignment", hallway.getAlignment()));	
			
			if (hallway.getLayout() != null)
				sb.append(String.format(format, "Layout", hallway.getLayout().getName()));
			
			sb.append(String.format(format, "X Dimensions", String.format("%s <--> %s", hallway.getMinX(), hallway.getMaxX())))
			.append(String.format(format, "Y Dimensions", String.format("%s <--> %s", hallway.getMinY(), hallway.getMaxY())))
			.append(String.format(format, "Z Dimensions", String.format("%s <--> %s", hallway.getMinZ(), hallway.getMaxZ())));
			
			if (hallway.getDoors() != null) {
				sb.append(String.format(format, "# of Doors", hallway.getDoors().size()));
				for (Door d : hallway.getDoors()) {
					sb.append(sub)
					.append(String.format(heading2, "[Door]"))
					.append(String.format(format2, "Location", d.getCoords().toShortString()))
					.append(String.format(format2, "Direction", d.getDirection()));
					if (d.getRoom() != null) {
						sb.append(String.format(format2, "Leads To Room", d.getRoom().getId()));
					}
				}
			}
			if (hallway.getHallway() != null) {
				sb.append(String.format(format2,  "Leads to Hallway", hallway.getHallway().getId()));
			}
			
			sb.append(div);
		}
		catch(Exception e) {
			return e.getMessage();
		}
		return sb.toString();
	}
}
