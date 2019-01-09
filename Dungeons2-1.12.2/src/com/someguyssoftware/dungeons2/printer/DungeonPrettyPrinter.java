package com.someguyssoftware.dungeons2.printer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.Level;

/**
 * 
 * @author Mark Gottschling on Aug 26, 2017
 *
 */
public class DungeonPrettyPrinter {
	private static final String div;
	
	static {
		// setup a divider line
		char[] chars = new char[75];
		Arrays.fill(chars, '*');
		div = new String(chars) + "\n";	
	}
	
	public DungeonPrettyPrinter() {}
	
	/**
	 * Print all the properties of a dungeon in a prettified format out to a file.
	 * @param dungeon
	 * @param path
	 */
	public String print(Dungeon dungeon, String filePath) {
		String s = print(dungeon);
		Path path = Paths.get(filePath).toAbsolutePath();
		try {
			Files.write(path, s.getBytes());
		} catch (IOException e) {
			Dungeons2.log.error("Error writing Dungeon to dump file", e);
		}
		return s;
	}
	
	/**
	 * Print all the properties of a dungeon in a prettified format out to a String
	 * @param dungeon
	 * @return
	 */
	public String print(Dungeon dungeon) {
		StringBuilder sb = new StringBuilder();
		try {
			String format = "**    %1$-33s: %2$-30s  **\n";
			String heading = "**  %1$-67s  **\n";
			sb
			.append(div)
			.append(String.format("**  %-67s  **\n", "DUNGEON"))
			.append(div)
			.append(String.format(heading, "[Config]"))
			.append(String.format(format, "Min. Y Position", dungeon.getConfig().getYBottom()))
			.append(String.format(format, "Max. Y Position", dungeon.getConfig().getYTop()))
			.append(String.format(format, "Surface Buffer", dungeon.getConfig().getSurfaceBuffer()))
			.append(String.format(heading, "[Properties]"))
			.append(String.format(format, "Location", dungeon.getEntrance().getBottomCenter().toShortString()))
			.append(String.format(format, "# of Levels", dungeon.getLevels().size()))
			.append(String.format(format, "X Range", String.format("%s <--> %s", dungeon.getMinX(), dungeon.getMaxX())))
			.append(String.format(format, "Y Range", String.format("%s <--> %s", dungeon.getMinY(), dungeon.getMaxY())))
			.append(String.format(format, "Z Range", String.format("%s <--> %s", dungeon.getMinZ(), dungeon.getMaxZ())))
			.append(div)
			.append("\n");
			
			// entrance room
			RoomPrettyPrinter roomPrinter = new RoomPrettyPrinter();
			String room = roomPrinter.print(dungeon.getEntrance(), "Exit Room");
			sb.append(room).append("\n");
			
			// levels
			LevelPrettyPrinter levelPrinter = new LevelPrettyPrinter();
			
			int levelIndex = 1;
			for (Level l : dungeon.getLevels()) {
				String level = levelPrinter.print(l, "> LEVEL" + levelIndex);
				sb.append(level).append("\n");
//				sb
//				.append(div)
//				.append(String.format("**  %-67s  **\n", String.format("> LEVEL %d", levelIndex)))	
//				.append(div);
				levelIndex++;
			}
			
		}
		catch(Exception e) {
			return e.getMessage();
		}
		return sb.toString();
	}
}
