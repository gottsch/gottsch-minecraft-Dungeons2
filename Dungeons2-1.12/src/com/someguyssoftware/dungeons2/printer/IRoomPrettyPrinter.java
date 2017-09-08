package com.someguyssoftware.dungeons2.printer;

import com.someguyssoftware.dungeons2.model.Room;

public interface IRoomPrettyPrinter {

	/**
	 * 
	 * @param room
	 * @return
	 */
	String print(Room room);

	/**
	 * 
	 * @param room
	 * @return
	 */
	String print(Room room, String title);

}