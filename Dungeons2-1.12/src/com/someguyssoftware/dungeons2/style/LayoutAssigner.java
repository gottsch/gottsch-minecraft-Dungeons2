/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.generator.DungeonGenerator;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.model.Room.Type;

/**
 * @author Mark Gottschling on Aug 31, 2016
 *
 */
public class LayoutAssigner {

	private StyleSheet styleSheet;
	private Multimap<String, Layout> map;

	/**
	 * 
	 * @param styleSheet
	 */
	public LayoutAssigner(StyleSheet styleSheet) {
		map = ArrayListMultimap.create();
		this.styleSheet = styleSheet;
		
		// organize style sheet layouts based on category
		loadStyleSheet(styleSheet);
	}
	
	/**
	 * 
	 * @param styleSheet
	 */
	public void loadStyleSheet(StyleSheet styleSheet) {
		// clear the map
		map.clear();
		// for each layout remap by category into multi map
		for (Entry<String, Layout> e : styleSheet.getLayouts().entrySet()) {
			Layout layout = e.getValue();
			if (layout.getCategory() != null && !layout.getCategory().equals("")) {
				map.put(layout.getCategory().toLowerCase(), layout);
			}
			else {
				map.put(Layout.Type.ROOM.getName().toLowerCase(), layout);
			}
		}
	}
	
	/**
	 * 
	 * @param random
	 * @param room
	 * @param sheet
	 * @param layoutName
	 * @return
	 */
	public Layout assign(Random random, Room room, String layoutName) {
		Layout layout = getStyleSheet().getLayouts().get(layoutName);
		
		if (layout == null) {
			Dungeons2.log.debug("Getting default layout from default stylesheet.");
			layout = DungeonGenerator.getDefaultStyleSheet().getLayouts().get(Layout.DEFAULT_NAME);
		}
		
		// assign the room
		assignLayoutToRoom(random, layout, room);
		
		return layout;
	}
	
	/**
	 * TODO change all Layout.Type to Room.Type and get rid of Layout.Type
	 * @param styleSheet
	 * @param room
	 * @return
	 */
	public Layout assign(Random random, Room room) {
		List<Layout> layouts = null;
		Layout layout = null;

		// determine the category of layouts to grab based on the room
		if (room.getType() == Type.GENERAL) {
			layouts = (List<Layout>) map.get(Layout.Type.ROOM.getName().toLowerCase());
		}
		else if (room.isStart() && room.getType() != Room.Type.ENTRANCE) {
			layouts = (List<Layout>) map.get(Layout.Type.START.getName().toLowerCase());
		}
		else if (room.isEnd() && room.getType() != Room.Type.BOSS) {
			layouts = (List<Layout>) map.get(Layout.Type.END.getName().toLowerCase());
		}
		else if (room.getType() == Room.Type.HALLWAY || room instanceof Hallway) {
			layouts = (List<Layout>) map.get(Layout.Type.HALLWAY.getName().toLowerCase());
			layout = layouts.get(random.nextInt(layouts.size()));
//			Dungeons2.log.debug("Assigning Hallway layout:" + layout.getName());
			room.setLayout(layout);
			return layout;
		}
		else if (room.getType() == Type.ENTRANCE) {
			layouts = (List<Layout>) map.get(Layout.Type.ENTRANCE.getName().toLowerCase());
		}
		else if (room.getType() == Type.TREASURE) {
			layouts = (List<Layout>) map.get(Layout.Type.TREASURE.getName().toLowerCase());
		}
		
		else if (room.getType() == Type.BOSS) {
			layouts = (List<Layout>) map.get(Layout.Type.BOSS.getName().toLowerCase());
		}
		else {
			layouts = (List<Layout>) map.get(Layout.Type.ROOM.getName().toLowerCase());
		}
		
		// select a random layout from the category or use default layout if none found
		if (layouts != null && layouts.size() > 0) {
			layout = layouts.get(random.nextInt(layouts.size()));
		}
		else {
			layout = DungeonGenerator.getDefaultStyleSheet().getLayouts().get(Layout.DEFAULT_NAME);
		}
		
		// assign the room
		assignLayoutToRoom(random, layout, room);
		
		return layout;
	}
	
	/**
	 * @param random
	 * @param layout
	 * @param room
	 */
	private void assignLayoutToRoom(Random random, Layout layout, Room room) {
		// if useAll is set to true, then if the frame exists, activate the element in the room
		if (layout.isUseAll()) {
			Dungeons2.log.debug("Using ALL Frames!");
			// just turn on items that have values
			if (getStyleSheet().hasFrame(layout, DesignElement.CROWN)) {
				room.setHasCrown(true);
			}
			if (getStyleSheet().hasFrame(layout, DesignElement.TRIM)) {
				room.setHasTrim(true);
			}
			if (getStyleSheet().hasFrame(layout, DesignElement.PILLAR)) {
				room.setHasPillar(true);
			}
			if (getStyleSheet().hasFrame(layout, DesignElement.PILASTER) &&
					room.getWidth() >= 5 && room.getDepth() >= 5) {
				room.setHasPilaster(true);
			}
			
			// gutter
			if (getStyleSheet().hasFrame(layout, DesignElement.GUTTER)) {
				room.setHasGutter(true);
			}
			// grate
			if (getStyleSheet().hasFrame(layout, DesignElement.GRATE)) {
				room.setHasGrate(true);
			}
			
			// coffered
			if (getStyleSheet().hasFrame(layout, DesignElement.COFFERED_CROSSBEAM) ||
					getStyleSheet().hasFrame(layout, DesignElement.COFFERED_MIDBEAM)) {
				room.setHasCoffer(true);
			}
			
			// wall base
			if (getStyleSheet().hasFrame(layout, DesignElement.WALL_BASE)) {
				room.setHasWallBase(true);
			}
			
			// wall capital
			if (getStyleSheet().hasFrame(layout, DesignElement.WALL_CAPITAL)) {
				room.setHasWallCapital(true);
			}			
			
			if (room.getType() == Type.ENTRANCE) {
				if (getStyleSheet().hasFrame(layout, DesignElement.COLUMN)) {
					room.setHasColumn(true);
				}
				if (getStyleSheet().hasFrame(layout, DesignElement.CORNICE)) {
					room.setHasCornice(true);
				}
				if (getStyleSheet().hasFrame(layout, DesignElement.CRENELLATION) &&
						room.getWidth() >= 7 && room.getDepth() >= 7) {
					room.setHasCrenellation(true);
					room.setHasMerlon(true);
					room.setHasParapet(true);
				}
				else {
					if (getStyleSheet().hasFrame(layout, DesignElement.MERLON)) {
						room.setHasMerlon(true);
					}
					else if(getStyleSheet().hasFrame(layout, DesignElement.PARAPET)) {
						room.setHasParapet(true);
					}
				}
				if (getStyleSheet().hasFrame(layout, DesignElement.PLINTH)) {
					room.setHasPlinth(true);
				}
			}
		}
		else {
			if (getStyleSheet().hasFrame(layout, DesignElement.CROWN) && random.nextInt(100) < 25) {
				room.setHasCrown(true);
			}

			if (getStyleSheet().hasFrame(layout, DesignElement.TRIM) && random.nextInt(100) < 20) {
				room.setHasTrim(true);
//				Dungeons2.log.debug("Room has Trim @ " + room.getCoords());
			}
			if (getStyleSheet().hasFrame(layout, DesignElement.PILLAR) && random.nextInt(100) < 25) {
				room.setHasPillar(true);
			}
			if (getStyleSheet().hasFrame(layout, DesignElement.PILASTER) && 
					room.getWidth() >= 5 && room.getDepth() >= 5 && random.nextInt(100) < 25) {
				room.setHasPilaster(true);
			}

			// gutter
			if (getStyleSheet().hasFrame(layout, DesignElement.GUTTER) && random.nextInt(100) < 25) {
				room.setHasGutter(true);
			}
			// grate
			if (getStyleSheet().hasFrame(layout, DesignElement.GRATE) && (random.nextInt(100) < 15)) {
				room.setHasGrate(true);
			}
			
			// coffer
			if ((getStyleSheet().hasFrame(layout, DesignElement.COFFERED_CROSSBEAM) ||
					getStyleSheet().hasFrame(layout, DesignElement.COFFERED_MIDBEAM)) 
					&& random.nextInt(100) < 20) {
				room.setHasCoffer(true);
			}
			
			// wall base
			if (getStyleSheet().hasFrame(layout, DesignElement.WALL_BASE) && (random.nextInt(100) <25)) {
				room.setHasWallBase(true);
			}
			
			// wall capital
			if (getStyleSheet().hasFrame(layout, DesignElement.WALL_CAPITAL) && (random.nextInt(100) < 25)) {
				room.setHasWallCapital(true);
			}
			
			// entrance rooms
			if (room.getType() == Type.ENTRANCE) {
				if (room.getWidth() >=7
						&& getStyleSheet().hasFrame(layout, DesignElement.PILASTER) && random.nextInt(100) < 25) {
					room.setHasPilaster(true);
				}
				else {
					room.setHasPilaster(false);
				}
				room.setHasPillar(false);
				
				if (getStyleSheet().hasFrame(layout, DesignElement.COLUMN) && random.nextInt(100) < 50) {
					room.setHasColumn(true);
				}
				if (getStyleSheet().hasFrame(layout, DesignElement.CORNICE) && random.nextInt(100) < 60) {
					room.setHasCornice(true);
				}
				if (getStyleSheet().hasFrame(layout, DesignElement.CRENELLATION) &&
						room.getWidth() >= 7 && room.getDepth() >= 7 && random.nextInt(100) < 50) {
					room.setHasCrenellation(true);
					room.setHasMerlon(true);
					room.setHasParapet(true);
				}
				else {
					if (getStyleSheet().hasFrame(layout, DesignElement.MERLON) && random.nextInt(100) < 60) {
						room.setHasMerlon(true);
					}
					else if(getStyleSheet().hasFrame(layout, DesignElement.PARAPET) && random.nextInt(100) < 70) {
						room.setHasParapet(true);
					}
				}
				if (getStyleSheet().hasFrame(layout, DesignElement.PLINTH) && random.nextInt(100) < 50) {
					room.setHasPlinth(true);
				}
			}
		}
		
		// set the room with the selected layout
		room.setLayout(layout);
	}

	/**
	 * @return the styleSheet
	 */
	public StyleSheet getStyleSheet() {
		return styleSheet;
	}

	/**
	 * @param styleSheet the styleSheet to set
	 */
	public void setStyleSheet(StyleSheet styleSheet) {
		this.styleSheet = styleSheet;
	}
}
