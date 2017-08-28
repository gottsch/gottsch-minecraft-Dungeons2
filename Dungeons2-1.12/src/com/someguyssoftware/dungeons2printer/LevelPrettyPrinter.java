/**
 * 
 */
package com.someguyssoftware.dungeons2printer;

import java.util.Arrays;

import com.someguyssoftware.dungeons2.model.Level;

/**
 */
public class LevelPrettyPrinter implements IPrettyPrinter {
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
		String format = "**    %1$-33s: %2$-30s  **\n";
		String heading = "**  %1$-67s  **\n";
		StringBuilder sb = new StringBuilder();
		
		try {
			sb
			.append(div)
			.append(String.format("**  %-67s  **\n", title))
			.append(div)
			.append(String.format(heading, "[Config]"))
			.append(String.format(format, "Depth", toString(level.getConfig().getDepth())))
			.append(String.format(format, "# of Rooms", toString(level.getConfig().getNumberOfRooms())))
			.append(String.format(format, "# of Degrees", toString(level.getConfig().getDegrees())))
			.append(String.format(heading, "[Properties]"))
			.append(String.format(format, "ID", level.getId()))
			.append(String.format(format, "Name", level.getName()))
			.append(String.format(format, "# of Rooms", level.getRooms().size()))
			.append(String.format(format, "Location", level.getStartRoom().getBottomCenter().toShortString()))
			.append(String.format(format, "X Dimensions", String.format("%s <--> %s", level.getMinX(), level.getMaxX())))
			.append(String.format(format, "Y Dimensions", String.format("%s <--> %s", level.getMinY(), level.getMaxY())))
			.append(String.format(format, "Z Dimensions", String.format("%s <--> %s", level.getMinZ(), level.getMaxZ())))
			.append(div);
		}
		catch(Exception e) {
			return e.getMessage();
		}
		return sb.toString();
	}
}
