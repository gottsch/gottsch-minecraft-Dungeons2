/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2020 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.astar.AStarOrthogonal;

import mod.gottsch.forge.dungeons2.core.generator.AbstractGraphLevelGenerator;
import mod.gottsch.forge.dungeons2.core.generator.Axis;
import mod.gottsch.forge.dungeons2.core.generator.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.ILevel;
import mod.gottsch.forge.dungeons2.core.generator.ILevelGenerator;
import mod.gottsch.forge.dungeons2.core.generator.INode;
import mod.gottsch.forge.dungeons2.core.generator.NodeType;
import mod.gottsch.forge.dungeons2.core.generator.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.graph.mst.Edge;

/**
 * This is a 2D level generator. Therfore dimensions are described as x-aix
 * [length] and y-axis [width] and the observer is on the z-axis [height],
 * looking down. So when something is assigned a dimension value that adds
 * volumn to a plane, it will be on the z-axis. This is different from a 3D view
 * where the Y is the height and Z is the width (or depth). Overlapping
 * rectangle code based on
 * https://stackoverflow.com/questions/3265986/an-algorithm-to-space-out-overlapping-rectangles
 * 
 * @author Mark Gottschling on Sep 15, 2020
 *
 */
public class DungeonLevelGenerator extends AbstractGraphLevelGenerator {

	protected static final Logger LOGGER = LogManager.getLogger(DungeonLevelGenerator.class);

	private static final ILevel EMPTY_LEVEL = new DungeonLevel();

	private static final int MIN_ROOM_OVERLAP = 3;

	// TODO w,h needs to be passed in. determined by size - large, medium, small
	private int width = 96;
	private int height = 96;
	// TODO probably provided by Config
	private int spawnBoxWidth = 30;
	private int spawnBoxHeight = 30;
	// TODO rest of these prpoerties are provided by a config
	private int numberOfRooms = 15;
	private int minRoomSize = 5;
	private int maxRoomSize = 15;
	private int movementFactor = 1;
	private double meanFactor = 1.15;
	private double pathFactor = 0.25;
	// TODO this could be a config option. but the boundary would have to be updated
	// after all the rooms are generated (which happens now anyways)
	private boolean hardBoundary = true;

	// TODO this will need to be pass in a config or context
	@Override
	public ILevel init() {
		DungeonLevel dungeonData = new DungeonLevel();
		Random random = new Random();
		// cellmap is only needed by the visualizer, which can be created by the visualizer
//		boolean[][] cellMap = new boolean[width][height];	//initMap(random);

		List<IRoom> rooms = initRooms(random);
		Map<Integer, IRoom> roomMap = mapRooms(rooms);
//		cellMap = updateCellMap(cellMap, rooms, null);

//		dungeonData.setCellMap(cellMap);
		dungeonData.setRooms(rooms);
		dungeonData.setRoomMap(roomMap);
		dungeonData.setEdges(new ArrayList<Edge>());
		dungeonData.setPaths(new ArrayList<Edge>());
		dungeonData.setWaylines(new ArrayList<Wayline>());
		dungeonData.setCorridors(new ArrayList<Corridor>());
		return dungeonData;
	}

	/// TODO this will need to be passed in a config or context or not - set with the .with()
	// methods
	@Override
	public ILevel generate() {
		DungeonLevel dungeonData = (DungeonLevel) init();
		Random random = new Random();
//		boolean[][] cellMap = dungeonData.getCellMap();
		List<IRoom> rooms = dungeonData.getRooms();

		// separate rooms
		rooms = separateRooms(rooms, movementFactor);

		// remove rooms outside boundary
		rooms = checkConstraints(rooms);

		// select main rooms
		List<IRoom> mainRooms = selectMainRooms(rooms, meanFactor);

		/*
		 * reset ids add main rooms to an ordered list. (because of how
		 * DelaunayTriangulator works, the main rooms need to in a ordered list AND
		 * their ids need to be reset according to their position in the list)
		 */
		IRoom start = null;
		IRoom end = null; // TODO can be a list (multiple end rooms, one is primary the others are decoys)
		List<IRoom> orderedRooms = new LinkedList<>();
		for (IRoom room : mainRooms) {
			orderedRooms.add(room);
			// reset the ids of the rooms to remain < size of the array
			room.setId(orderedRooms.size() - 1);
//			LOGGER.debug("room id -> {} changed to -> {}", id, room.getId());
			if (room.getType() == NodeType.START) {
				start = room;
			} else if (room.getType() == NodeType.END) {
				end = room;
			}
		}

		// map the rooms
//		Map<Integer, IDungeonRoom> roomMap = mapRooms(orderedMainRooms);

		// triangulate valid rooms
		Optional<List<Edge>> edges = triangulate(orderedRooms);
		if (!edges.isPresent()) {
			return EMPTY_LEVEL;
		}

		// get the paths using minimum spanning tree
		List<Edge> paths = getPaths(random, edges.get(), orderedRooms, getPathFactor());

		// test if start room can reach the end room
		if (!breadthFirstSearch(start.getId(), end.getId(), orderedRooms, paths)) {
			LOGGER.debug("A path doesn't exist from start room to end room on level.");
			return EMPTY_LEVEL;
		}

		// add waylines
		List<Wayline> waylines = getWaylines(random, paths, orderedRooms);

		// add exits to main rooms
		orderedRooms = addExits(waylines, orderedRooms);

		// reintroduce minor rooms into ordered list. these are not auxiliary rooms
		// until it is determine that they intersect with a corridor
		orderedRooms = selectAuxiliaryRooms(waylines, rooms, orderedRooms);

		// add corridors based on waylines
		List<Corridor> corridors = getCorridors(waylines);
		
		// TODO should this be part of getCorridors(), which then must take in the list of rooms
		// update corridors with room intersections
		for(Corridor corridor : corridors) {
			corridor.findIntersections(orderedRooms);
		}
		
		// TODO add elevation variance
		

		// TODO will take the corridor edges as well
//		cellMap = updateCellMap(cellMap, orderedRooms, corridors);

		// save cell map and rooms to the level
//		dungeonData.setCellMap(cellMap);
		dungeonData.setRooms(orderedRooms);
//		dungeonData.setRoomMap(roomMap);
		dungeonData.setEdges(edges.get());
		dungeonData.setPaths(paths);
		dungeonData.getWaylines().addAll(waylines);
		dungeonData.getCorridors().addAll(corridors);
		return dungeonData;
	}

	/**
	 * 
	 * @param room
	 * @return
	 */
	public Coords2D getCenter(List<IRoom> room) {
		Rectangle2D boundingBox = getBoundingBox(room);
		Coords2D center = boundingBox.getCenter();
		return center;
	}

	/**
	 * 
	 * @param rooms
	 * @return
	 */
	private Rectangle2D getBoundingBox(List<IRoom> rooms) {
		Coords2D topLeft = null;
		Coords2D bottomRight = null;

		for (IRoom room : rooms) {
			if (topLeft == null) {
				topLeft = new Coords2D(room.getOrigin().getX(), room.getOrigin().getY());
			} else {
				if (room.getOrigin().getX() < topLeft.getX()) {
					topLeft.setLocation(room.getOrigin().getX(), topLeft.getY());
				}

				if (room.getOrigin().getY() < topLeft.getY()) {
					topLeft.setLocation(topLeft.getX(), room.getOrigin().getY());
				}
			}

			if (bottomRight == null) {
				bottomRight = new Coords2D(room.getMaxX(), (int) room.getMaxY());
			} else {
				if (room.getMaxX() > bottomRight.getX()) {
					bottomRight.setLocation((int) room.getMaxX(), bottomRight.getY());
				}

				if (room.getMaxY() > bottomRight.getY()) {
					bottomRight.setLocation(bottomRight.getX(), (int) room.getMaxY());
				}
			}
		}
		return new Rectangle2D(topLeft.getX(), topLeft.getY(), bottomRight.getX() - topLeft.getX(),
				bottomRight.getY() - topLeft.getY());
	}

	/**
	 * 
	 * @param rooms
	 * @return
	 */
	public boolean iterateSeparationStep(Coords2D center, List<IRoom> rooms, int movementFactor) {
		boolean hasIntersections = false;
		int totalMovement = 0;

		for (IRoom room : rooms) {

			if (room.getFlags().contains(RoomFlag.ANCHOR)) {
				continue;
			}

			// find all the rectangles R' that overlap R.
			List<IRoom> intersectingRooms = findIntersections(room, rooms);

			if (intersectingRooms.size() > 0) {

				// Define a movement vector v.
				Coords2D movementVector = new Coords2D(0, 0);

				Coords2D centerR = new Coords2D(room.getCenter());

				// for each rectangle R that overlaps another.
				for (IRoom rPrime : intersectingRooms) {
					Coords2D centerRPrime = new Coords2D(rPrime.getCenter());

					int translatedX = centerR.getX() - centerRPrime.getX();
					int translatedY = centerR.getY() - centerRPrime.getY();

					// TODO this statement is not exactly true. it is not "proportional", but
					// increments by 1 (or the movementFactor)
					// add a vector to v proportional to the vector between the center of R and R'.
					movementVector.translate(translatedX < 0 ? -movementFactor : movementFactor,
							translatedY < 0 ? -movementFactor : movementFactor);
				}

				int translatedX = centerR.getX() - center.getX();
				int translatedY = centerR.getY() - center.getY();

				// add a vector to v proportional to the vector between C and the center of R.
				movementVector.translate(translatedX < 0 ? -movementFactor : movementFactor,
						translatedY < 0 ? -movementFactor : movementFactor);

				// translate R by v.
				room.setOrigin(new Coords2D(room.getOrigin().getX() + movementVector.getX(),
						room.getOrigin().getY() + movementVector.getY()));

				// repeat until nothing overlaps.
				hasIntersections = true;
				// add movement to the total
				totalMovement += (movementVector.getX() + movementVector.getY());
			}

		}

		// now do a check
		if (hasIntersections && totalMovement == 0) {
			List<IRoom> roomsToRemove = new ArrayList<>();
			// loop through all the rooms
			rooms.forEach(room -> {
				// don't process if room is already in the roomsToRemove
				if (roomsToRemove.contains(room)) {
					return;
				}
				List<IRoom> intersectingRooms = findIntersections(room, rooms);
				if (intersectingRooms.size() > 0) {
					intersectingRooms.forEach(intersectRoom -> {
						/*
						 * Remove the intersecting room if: 1) it is NOT a START or END room 2) a) the
						 * main IS a START or END room b) OR the intersecting room is NOT an ANCHOR c)
						 * OR both main room and intersecting rooms are ANCHOR
						 */
						if ((intersectRoom.getType() != NodeType.START && intersectRoom.getType() != NodeType.END)) {
							LOGGER.debug("intersect room {} is not START nor END", intersectRoom.getId());
							if ((room.getType() == NodeType.START || room.getType() == NodeType.END)
									|| !intersectRoom.getFlags().contains(RoomFlag.ANCHOR)
									|| (room.getFlags().contains(RoomFlag.ANCHOR)
											&& intersectRoom.getFlags().contains(RoomFlag.ANCHOR))) {
								roomsToRemove.add(intersectRoom);
								LOGGER.debug("adding room.id -> {} to remove", intersectRoom.getId());
							}
						}

					});
				}
			});
			// NOTE modifies rooms list here
			rooms.removeAll(roomsToRemove);
		}

		return hasIntersections;
	}

	/**
	 * 
	 * @param rooms
	 * @return
	 */
	private List<IRoom> checkConstraints(List<IRoom> rooms) {
		List<IRoom> validRooms = new ArrayList<IRoom>();
		Rectangle2D boundary = new Rectangle2D(0, 0, getWidth(), getHeight());

		rooms.forEach(room -> {
			if (boundaryConstraint(boundary, room)) {
				validRooms.add(room);
			} else {
				LOGGER.debug("room at {} [w:{}, h:{}] failed boundaries test", room.getBox().getOrigin(),
						room.getBox().getWidth(), room.getBox().getHeight());
			}
		});

		return validRooms;
	}

	/**
	 * 
	 * @param waylines
	 * @return
	 */
	public List<Corridor> getCorridors(List<Wayline> waylines) {
		List<Corridor> corridors = new ArrayList<>();
		waylines.forEach(wayline -> {
			Axis axis = (wayline.getConnector1().getCoords().getX() == wayline.getConnector2().getCoords().getX())
					? Axis.Y
					: Axis.X;

			// setup
			WayConnector connector1 = null;
			WayConnector connector2 = null;
			Coords2D coords1 = null;
			Coords2D coords2 = null;
			List<Coords2D> exits = new ArrayList<>();

			// TODO FUTURE determine the max width of a corridor (by odd numbers: 3, 5, 7)
			// (probably not bigger than 5 else it isn't a corridor anymore)
			int width = 3;

			switch (axis) {
			case X:
				// order the connectors
				if (wayline.getConnector1().getCoords().getX() <= wayline.getConnector2().getCoords().getX()) {
					connector1 = wayline.getConnector1();
					connector2 = wayline.getConnector2();
				} else {
					connector1 = wayline.getConnector2();
					connector2 = wayline.getConnector1();
				}

				// new coords so as to not override current waylines connector coords
				coords1 = new Coords2D(connector1.getCoords());
				coords2 = new Coords2D(connector2.getCoords());

				// adjust the position/widths. ie. the corridor is adjusted to not overlap
				// connecting into rooms, but instead if joint so the corridor is properly drawn
				if (connector1.getRoom() != null) {
					// edge of corridor equals edge of room
					coords1.setX(connector1.getRoom().getMaxX());
					/*
					 * add exit to corridor add before altering the width of the corridor so it
					 * aligns with the room
					 */
					exits.add(new Coords2D(coords1.getX(), coords1.getY()));
				} else {
					// grow length by at least 1
					coords1.setX(coords1.getX() - 1);
				}

				if (connector2.getRoom() != null) {
					// edge of corridor equals edge of room
					coords2.setX(connector2.getRoom().getMinX());
					/*
					 * add exit to corridor add before altering the width of the corridor so it
					 * aligns with the room
					 */
					exits.add(new Coords2D(coords2.getX(), coords2.getY()));
				} else {
					// grow length by at least 1
					coords2.setX(coords2.getX() + 1);
				}

				// grow width by 2 to make at least 3 wide (TODO able to have wider corridors)
				coords1.setY(coords1.getY() - 1);
				coords2.setY(coords2.getY() + 1);
				break;

			case Y:
				if (wayline.getConnector1().getCoords().getY() <= wayline.getConnector2().getCoords().getY()) {
					connector1 = wayline.getConnector1();
					connector2 = wayline.getConnector2();
				} else {
					connector1 = wayline.getConnector2();
					connector2 = wayline.getConnector1();
				}

				// new coords so as to not override current waylines connector coords
				coords1 = new Coords2D(connector1.getCoords());
				coords2 = new Coords2D(connector2.getCoords());

				if (connector1.getRoom() != null) {
					coords1.setY(connector1.getRoom().getMaxY());
					exits.add(new Coords2D(coords1.getX(), coords1.getY()));
				} else {
					coords1.setY(coords1.getY() - 1);
				}

				if (connector2.getRoom() != null) {
					coords2.setY(connector2.getRoom().getMinY());
					exits.add(new Coords2D(coords2.getX(), coords2.getY()));
				} else {
					coords2.setY(coords2.getY() + 1);
				}
				coords1.setX(coords1.getX() - 1);
				coords2.setX(coords2.getX() + 1);
			}

			// create corridor
			Corridor corridor = new Corridor(new Rectangle2D(coords1, coords2));
			corridor.setAxis(axis);
			corridor.setExits(exits);
			if (connector1.getRoom() != null) {
				corridor.getConnectsTo().add(connector1.getRoom());
			}
			if (connector2.getRoom() != null) {
				corridor.getConnectsTo().add(connector2.getRoom());
			}

			// add corridor to list
			corridors.add(corridor);
		});

		// addExits between corridors
		corridors.forEach(corridor1 -> {
			corridors.forEach(corridor2 -> {
				if (corridor1 == corridor2) {
					return;
				}
				if (corridor1.getBox().intersects(corridor2.getBox())) {
					// typical/expected scenario - opposite axis; same axis we will want to ignore
					if (corridor1.getAxis() == Axis.X && corridor2.getAxis() == Axis.Y) {
						if (corridor2.getBox().getMinX() > corridor1.getBox().getMinX()
								&& corridor2.getBox().getMinX() < corridor1.getBox().getMaxX()) {
							corridor1.getExits()
									.add(new Coords2D(corridor2.getBox().getMinX(), corridor1.getBox().getCenterY()));
						} 
//						else {
//							corridor1.getExits()
//									.add(new Coords2D(corridor2.getBox().getMaxX(), corridor1.getBox().getCenterY()));
//						}
						if (corridor2.getBox().getMaxX() > corridor1.getBox().getMinX()
								&& corridor2.getBox().getMaxX() < corridor1.getBox().getMaxX()) {
							corridor1.getExits()
									.add(new Coords2D(corridor2.getBox().getMaxX(), corridor1.getBox().getCenterY()));
						} 						
					} else if (corridor1.getAxis() == Axis.Y && corridor2.getAxis() == Axis.X) {
						if (corridor2.getBox().getMinY() > corridor1.getBox().getMinY()
								&& corridor2.getBox().getMinY() < corridor1.getBox().getMaxY()) {
							corridor1.getExits()
									.add(new Coords2D(corridor1.getBox().getCenterX(), corridor2.getBox().getMinY()));
						}
//						else {
//							corridor1.getExits()
//									.add(new Coords2D(corridor1.getBox().getCenterX(), corridor2.getBox().getMaxY()));
//						}
						if (corridor2.getBox().getMaxY() > corridor1.getBox().getMinY()
								&& corridor2.getBox().getMaxY() < corridor1.getBox().getMaxY()) {
							corridor1.getExits()
									.add(new Coords2D(corridor1.getBox().getCenterX(), corridor2.getBox().getMaxY()));
						}						
					}
				}

			});
		});
		return corridors;
	}

	/**
	 * 
	 * @param waylines
	 * @param rooms
	 * @param orderedRooms
	 * @return
	 */
	private List<IRoom> selectAuxiliaryRooms(List<Wayline> waylines, List<IRoom> rooms, List<IRoom> orderedRooms) {
		List<IRoom> newOrderedRooms = new LinkedList<>(orderedRooms);
		rooms.forEach(room -> {
//			LOGGER.debug("processing room -> {}, role -> {}\n", room.getId(), room.getRole());
			if (room.getRole() != RoomRole.MAIN) {
//				LOGGER.debug("Not main -> {}\n", room.getId());
				room.setId(orderedRooms.size());
				if (intersects(room, waylines, orderedRooms)) {
					room.setRole(RoomRole.AUXILIARY);
					addExits(room, waylines, orderedRooms);
				}
				room.setId(newOrderedRooms.size());
				newOrderedRooms.add(room);
			}
		});
		return newOrderedRooms;
	}

	/**
	 * 
	 * @param waylines
	 * @param orderedRooms
	 * @return
	 */
	private List<IRoom> addExits(List<Wayline> waylines, List<IRoom> orderedRooms) {
		final List<IRoom> newOrderedRooms = new LinkedList<>(orderedRooms);
		newOrderedRooms.forEach(room -> {
			addExits(room, waylines, orderedRooms);
		});
		return newOrderedRooms;
	}

	/**
	 * 
	 * @param room
	 * @param waylines
	 * @param orderedRooms
	 */
	private void addExits(IRoom room, List<Wayline> waylines, List<IRoom> orderedRooms) {
		waylines.forEach(wayline -> {
			addExits(room, wayline, orderedRooms);
		});
	}

	/**
	 * 
	 * @param room
	 * @param wayline
	 * @param orderedRooms
	 */
	private void addExits(IRoom room, Wayline wayline, List<IRoom> orderedRooms) {
		if (intersects(room, wayline)) {
			Coords2D coords1 = wayline.getConnector1().getCoords();
			Coords2D coords2 = wayline.getConnector2().getCoords();
//			LOGGER.debug("exit: using coords1 -> {}, coords2 -> {}", coords1, coords2);
			Axis axis = (coords1.getX() == coords2.getX()) ? Axis.Y : Axis.X;
			// TODO exclude any exits that aren't on room edges
			switch (axis) {
			case X:
				if (coords1.getX() < room.getMinX() || coords2.getX() < room.getMinX()) {
					if ((coords1.getX() >= room.getMinX() && coords1.getX() <= room.getMaxX())
							|| (coords2.getX() >= room.getMinX() && coords2.getX() <= room.getMaxX())) {
						room.getExits().add(new Coords2D(room.getMinX(), coords1.getY()));
//						LOGGER.debug("adding exit at -> {}", (new Coords2D(room.getMinX(), coords1.getY())));
					} else {
						room.getExits().add(new Coords2D(room.getMinX(), coords1.getY()));
						room.getExits().add(new Coords2D(room.getMaxX(), coords1.getY()));
//						LOGGER.debug("adding 2 exits at -> {}", (new Coords2D(room.getMinX(), coords1.getY())));
//						LOGGER.debug(" and at -> {}", (new Coords2D(room.getMaxX(), coords1.getY())));
					}
				} else {
					room.getExits().add(new Coords2D(room.getMaxX(), coords1.getY()));
//					LOGGER.debug("adding exit at -> {}", (new Coords2D(room.getMaxX(), coords1.getY())));
				}
				break;
			case Y:
				if (coords1.getY() < room.getMinY() || coords2.getY() < room.getMinY()) {
					if ((coords1.getY() >= room.getMinY() && coords1.getY() <= room.getMaxY())
							|| (coords2.getY() >= room.getMinY() && coords2.getY() <= room.getMaxY())) {
						room.getExits().add(new Coords2D(coords1.getX(), room.getMinY()));
					} else {
						room.getExits().add(new Coords2D(coords1.getX(), room.getMinY()));
						room.getExits().add(new Coords2D(coords1.getX(), room.getMaxY()));
					}
				} else {
					room.getExits().add(new Coords2D(coords1.getX(), room.getMaxY()));
				}
				break;
			}
		}
	}

	/**
	 * 
	 * @param room
	 * @param waylines
	 * @param rooms
	 * @return
	 */
	private boolean intersects(IRoom room, List<Wayline> waylines, List<? extends IRoom> rooms) {
//		LOGGER.debug("room to intersect with -> {}", room.getBox());
		for (Wayline wayline : waylines) {
			if (intersects(room, wayline)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 * @param room
	 * @param wayline
	 * @return
	 */
	private boolean intersects(IRoom room, Wayline wayline) {
		Rectangle2D rectangle = wayline.getBox();
		if (rectangle.intersects(room.getBox()) || room.getBox().intersects(rectangle)) {
			return true;
		}
		return false;
	}

	/**
	 * NOTE if the rooms overlap each other on a single axis, they are "close"
	 * enough that a single wayline (not-elbow) can be used to connect them.
	 * ex. a = (1,1) -> (10, 10), b = (5, 15) - (15,25): the room don't intersect
	 * but the x-axis values do, allowing a single vertical wayline to connect them.
	 * of INodes
	 * 
	 * @param rand
	 * @param paths
	 * @param rooms
	 * @return
	 */
	public List<Wayline> getWaylines(Random rand, List<Edge> paths, List<IRoom> rooms) {

		/*
		 * a list of a the waylines constructed from paths
		 */

		List<Wayline> waylines = new ArrayList<>();

		for (Edge path : paths) {
			// get the rooms
			IRoom room1 = rooms.get(path.v);
			IRoom room2 = rooms.get(path.w);

			// horizontal wayline (east-west)
			if (checkHorizontalConnectivity(room1, room2)) {
				Wayline wayline = getHorizontalWayline(room1, room2);
//				LOGGER.debug("horizontal wayline final -> {}", wayline);
				Optional<List<IRoom>> intersectRooms = getIntersects(wayline, rooms, room1, room2);
				if (intersectRooms.isPresent()) {
					Optional<List<Wayline>> aStarWaylines = findAStarPath(wayline, intersectRooms.get());
					if (aStarWaylines.isPresent()) {
						waylines.addAll(aStarWaylines.get());
						// TEMP
						aStarWaylines.get().forEach(w -> {
							LOGGER.debug("horizontal w: c1 -> {}, c2-> {}", w.getConnector1().getCoords(), w.getConnector2().getCoords());
						});
					}
				}
				else {
					waylines.add(wayline);
				}
			}
			// vertical wayline (north-south)
			else if (checkVerticalConnectivity(room1, room2)) {
				Wayline wayline = getVerticalWayline(room1, room2);
				Optional<List<IRoom>> intersectRooms = getIntersects(wayline, rooms, room1, room2);
				if (intersectRooms.isPresent()) {
					Optional<List<Wayline>> aStarWaylines = findAStarPath(wayline, intersectRooms.get());
					if (aStarWaylines.isPresent()) {
						waylines.addAll(aStarWaylines.get());
						// TEMP
						aStarWaylines.get().forEach(w -> {
							LOGGER.debug(" vertical w: c1 -> {}, c2-> {}", w.getConnector1().getCoords(), w.getConnector2().getCoords());
						});				
					}
				}
				else {
					waylines.add(wayline);
				}
			}
			// elbow wayline
			else {
				Coords2D node1Center = room1.getCenter();

				WayConnector connector1 = null;
				// node2 is to the right/east/positive-x of node1
				if (room2.getCenter().getX() > room1.getCenter().getX()) {
					connector1 = new WayConnector(new Coords2D(room1.getMaxX() - 1, node1Center.getY()), (IRoom) room1);
				}
				// node2 is to the left/west/negative-x of node1
				else {
					// NOTE only thing that is differences from if() is that node1p uses minX as
					// opposed to maxX and the +1/-1
					connector1 = new WayConnector(new Coords2D(room1.getMinX() + 1, node1Center.getY()), (IRoom) room1);
				}

				// NOTE connector2 is the "destination" or "joint" node, so it should be shared
				// with both segments
				WayConnector connector2 = new WayConnector(new Coords2D(room2.getCenter().getX(), node1Center.getY()),
						null);
				Wayline wayline1 = new Wayline(connector1, connector2);

				// room2 is up (postivie-y) of room 1
				if (room2.getCenter().getY() > room1.getCenter().getY()) {
					connector1 = new WayConnector(new Coords2D(room2.getCenter().getX(), room2.getMinY() + 1),
							(IRoom) room2);
				}
				// room2 is down (negative-y) of room 1
				else {
					connector1 = new WayConnector(new Coords2D(room2.getCenter().getX(), room2.getMaxY() - 1),
							(IRoom) room2);
				}
				Wayline wayline2 = new Wayline(connector1, connector2);
				if (wayline1 != null && wayline2 != null) {
					Optional<List<IRoom>> intersectRooms1 = getIntersects(wayline1, rooms, room1, room2);
					Optional<List<IRoom>> intersectRooms2 = getIntersects(wayline2, rooms, room1, room2);
					if (intersectRooms1.isPresent() || intersectRooms2.isPresent()) {
						// create a new single list
						Set<IRoom> combinedSet = new HashSet<>(intersectRooms1.orElse(new ArrayList<>()));
						combinedSet.addAll(intersectRooms2.orElse(new ArrayList<>()));
						List<IRoom> combinedRooms = new ArrayList<>(combinedSet);

						// create a new wayline from room to room
						if (!combinedRooms.isEmpty()) {
							Wayline wayline = new Wayline(
									wayline1.getConnector1() == null ? wayline1.getConnector2() : wayline1.getConnector1(),
									wayline2.getConnector1() == null ? wayline2.getConnector2() : wayline2.getConnector1()
									);
							Optional<List<Wayline>> aStarWaylines = findAStarPath(wayline, combinedRooms);
							if (aStarWaylines.isPresent()) {
								waylines.addAll(aStarWaylines.get());
								// TEMP
								aStarWaylines.get().forEach(w -> {
									LOGGER.debug("elbow w: c1 -> {}, c2-> {}", w.getConnector1().getCoords(), w.getConnector2().getCoords());
								});
							}
						}
					}
					else {
						waylines.add(wayline1);
						waylines.add(wayline2);
						wayline1.setNext(wayline2);
						wayline2.setNext(null);
					}
				}
			}
		}
		return waylines;
	}

	/**
	 * 
	 * @param wayline
	 * @param rooms
	 * @param room1
	 * @param room2
	 * @return
	 */
	private Optional<List<IRoom>> getIntersects(final Wayline wayline, final List<IRoom> rooms, final IRoom room1, final IRoom room2) {
		List<IRoom> intersectRooms = new ArrayList<>();
		rooms.forEach(room -> {
			if (room == room1 || room == room2 ) {
				return;
			}
			if (intersects(room, wayline)) {
				if(room.hasFlag(RoomFlag.NO_INTERSECTION)) {
					LOGGER.debug("room -> {} is flagged as no itersection from room -> {} to room -> {}", room.getId(), room1.getId(), room2.getId());
					intersectRooms.add(room);
				}
			}
		});
		if (intersectRooms.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(intersectRooms);
	}

	/**
	 * 
	 * @param waylineIn
	 * @param intersectRooms
	 * @return
	 */
	private Optional<List<Wayline>> findAStarPath(Wayline waylineIn, List<IRoom> intersectRooms) {
		// convert list of rooms into a boolean map
		boolean [][] map = new boolean[width][height];
		intersectRooms.forEach(room -> {
			for (int x = 0; x < room.getBox().getWidth(); x++) {
				for (int y = 0; y < room.getBox().getHeight(); y++) {
					map[room.getOrigin().getX() + x][room.getOrigin().getY() + y] = true;
				}
			}
		});
		// add the target rooms with perforated outlines as blocked in the path map
		List<IRoom> targetRooms = new ArrayList<>();
		targetRooms.add(waylineIn.getConnector1().getRoom());
		targetRooms.add(waylineIn.getConnector2().getRoom());
		targetRooms.forEach(room -> {
			for (int x = 0; x < room.getBox().getWidth(); x++) {
				for (int y = 0; y < room.getBox().getHeight(); y++) {
					if ((x ==0 && y % 2 == 1) || (y == 0 && x % 2 ==1)) {
						map[room.getOrigin().getX() + x][room.getOrigin().getY() + y] = true;
					}
				}
			}
		});		
		
		AStarOrthogonal aStar = new AStarOrthogonal(map);
		Optional<List<com.ai.astar.Node>> nodes = aStar.findPath(
				new com.ai.astar.Node(waylineIn.getConnector1().getCoords().getX(), waylineIn.getConnector1().getCoords().getY()), 
				new com.ai.astar.Node(waylineIn.getConnector2().getCoords().getX(), waylineIn.getConnector2().getCoords().getY()));
		
		if (nodes.isPresent()) {
			List<Wayline> waylines = new ArrayList<>();
			com.ai.astar.Node startNode = null;
			com.ai.astar.Node lastNode = null;
			short direction = -1;
			short lastDirection = -1;
			for (com.ai.astar.Node node : nodes.get()) {
				LOGGER.debug("astar node -> {}", node.toString());
				if (startNode == null) {
					startNode = node;
					lastNode = node;
				}
				else {
					// travelling along x-axis
					if (node.getCol() == lastNode.getCol()) {
						direction = 0;
						if (lastDirection == -1) {
							lastDirection = direction;
						}
						if (direction != lastDirection) {
							// create wayline from startNode to lastNode
							Wayline wayline = new Wayline(
									new WayConnector(	new Coords2D(startNode.getRow(), startNode.getCol())), 
									new WayConnector(new Coords2D(lastNode.getRow(), lastNode.getCol()))
									);
							if(!insideRooms(wayline, targetRooms)) {
								waylines.add(wayline);
							}
							startNode = lastNode;
						}
					}
					else {
						// travelling along y-axis
						direction = 1;
						if (lastDirection == -1) {
							lastDirection = direction;
						}
						if (direction != lastDirection) {
							// create wayline from start to lastNode
							Wayline wayline = new Wayline(
									new WayConnector(	new Coords2D(startNode.getRow(), startNode.getCol())), 
									new WayConnector(new Coords2D(lastNode.getRow(), lastNode.getCol()))
									);
							if(!insideRooms(wayline, targetRooms)) {
								waylines.add(wayline);
							}

							startNode = lastNode;
						}
					}
					lastNode = node;
					lastDirection = direction;
				}
			}
			// create a wayline for the last element
			Wayline wayline = new Wayline(
					new WayConnector(	new Coords2D(startNode.getRow(), startNode.getCol())), 
					new WayConnector(new Coords2D(lastNode.getRow(), lastNode.getCol()))
					);
			if(!insideRooms(wayline, targetRooms)) {
				waylines.add(wayline);
			}
			return Optional.of(waylines);
		}

		return Optional.empty();
	}

	private boolean insideRooms(Wayline wayline, List<IRoom> targetRooms) {
		for (IRoom room : targetRooms) {
			Coords2D coords1 = wayline.getConnector1().getCoords();
			Coords2D coords2 = wayline.getConnector2().getCoords();
			if (coords1.getX() > room.getMinX() && coords1.getX() < room.getMaxX()
					&& coords1.getY() > room.getMinY() && coords1.getY() < room.getMaxY()
				&& coords2.getX() > room.getMinX() && coords2.getX() < room.getMaxX()
				&& coords2.getY() > room.getMinY() && coords2.getY() < room.getMaxY()) {
				LOGGER.debug("not adding wayline because it is entirely within room -> {}", room.getId());
				return true;
			}
		};
		return false;
	}

	/**
	 * Dungeon Method
	 * 
	 * @param node1
	 * @param node2
	 * @return
	 */
	private boolean checkHorizontalConnectivity(INode node1, INode node2) {
		if ((node1.getMaxY() <= node2.getMaxY() && node1.getMaxY() > (node2.getMinY() + MIN_ROOM_OVERLAP))
				|| (node2.getMaxY() <= node1.getMaxY() && node2.getMaxY() > (node1.getMinY() + MIN_ROOM_OVERLAP))
				|| (node1.getMinY() >= node2.getMinY() && node1.getMinY() < (node2.getMaxY() - MIN_ROOM_OVERLAP))
				|| (node2.getMinY() >= node1.getMinY() && node2.getMinY() < (node1.getMaxY() - MIN_ROOM_OVERLAP))) {
			return true;
		}
		return false;
	}

	/**
	 * Dungeon Method
	 * 
	 * @param node1
	 * @param node2
	 * @return
	 */
	private boolean checkVerticalConnectivity(INode node1, INode node2) {
		// vertical wayline (north-south)
		if ((node1.getMaxX() <= node2.getMaxX() && node1.getMaxX() > (node2.getMinX() + MIN_ROOM_OVERLAP))
				|| (node2.getMaxX() <= node1.getMaxX() && node2.getMaxX() > (node1.getMinX() + MIN_ROOM_OVERLAP))
				|| (node1.getMinX() > node2.getMinX() && node1.getMinX() <= (node2.getMaxX() - MIN_ROOM_OVERLAP))
				|| (node2.getMinX() > node1.getMinX() && node2.getMinX() <= (node1.getMaxX() - MIN_ROOM_OVERLAP))) {
			return true;
		}
		return false;
	}

	/**
	 * Dungeon method
	 * 
	 * @param node1
	 * @param node2
	 * @return
	 */
	public Wayline getHorizontalWayline(final INode node1, final INode node2) {
		/*
		 * get the min of the max's of x-axis from the 2 nodes AND get the max of the
		 * min's of x-axis from the 2 nodes
		 */
		int innerMaxX = Math.min(node1.getMaxX(), node2.getMaxX());
		int innerMinX = Math.max(node1.getMinX(), node2.getMinX());
		int innerMaxY = Math.min(node1.getMaxY(), node2.getMaxY());
		int innerMinY = Math.max(node1.getMinY(), node2.getMinY());
		int waylineY = (innerMaxY + innerMinY) / 2;
		// order and map nodes for connetors
		INode wc1 = node1.getMinX() == innerMinX ? node1 : node2;
		INode wc2 = node1.getMaxX() == innerMaxX ? node1 : node2;

		WayConnector connector1 = new WayConnector(new Coords2D(innerMinX + 1, waylineY), (IRoom) wc1);
		WayConnector connector2 = new WayConnector(new Coords2D(innerMaxX - 1, waylineY), (IRoom) wc2);
		Wayline wayline = new Wayline(connector1, connector2);
//		LOGGER.debug("adding horizontal wayline -> {}", wayline);
		return wayline;
	}

	/**
	 * Dungeon method
	 * 
	 * @param node1
	 * @param node2
	 * @return
	 */
	public Wayline getVerticalWayline(final INode node1, final INode node2) {
		/*
		 * get the min of the max's of x-axis from the 2 nodes AND get the max of the
		 * min's of x-axis from the 2 nodes
		 */
		int innerMaxX = Math.min(node1.getMaxX(), node2.getMaxX());
		int innerMinX = Math.max(node1.getMinX(), node2.getMinX());
		int innerMaxY = Math.min(node1.getMaxY(), node2.getMaxY());
		int innerMinY = Math.max(node1.getMinY(), node2.getMinY());
		int waylineX = (innerMaxX + innerMinX) / 2;

		// order and map nodes for connetors
		INode wc1 = node1.getMinY() == innerMinY ? node1 : node2;
		INode wc2 = node1.getMaxY() == innerMaxY ? node1 : node2;

		WayConnector connector1 = new WayConnector(new Coords2D(waylineX, innerMinY + 1), (IRoom) wc1);
		WayConnector connector2 = new WayConnector(new Coords2D(waylineX, innerMaxY - 1), (IRoom) wc2);
		Wayline wayline = new Wayline(connector1, connector2);
//		LOGGER.debug("adding vertical wayline -> {}", wayline);
		return wayline;
	}

	/**
	 * Returns a subset of rooms that meet the mean factor criteria. Start and End
	 * rooms are included as main rooms. Note that the original list is updated as
	 * well.
	 * 
	 * @param rooms
	 * @param meanFactor
	 * @return
	 */
	public List<IRoom> selectMainRooms(List<IRoom> rooms, final double meanFactor) {
		List<IRoom> mainRooms = new ArrayList<>();
		int totalArea = 0;
		for (IRoom room : rooms) {
			totalArea += room.getBox().getWidth() * room.getBox().getHeight();
		}

		int meanArea = (int) totalArea / rooms.size();

		rooms.forEach(room -> {
			if (room.getRole() == RoomRole.MAIN || room.getType() == NodeType.START || room.getType() == NodeType.END
					|| room.getBox().getWidth() * room.getBox().getHeight() > meanArea) {
				room.setRole(RoomRole.MAIN);
				mainRooms.add(room);
			}
		});

		return mainRooms;
	}

	// TODO currently this is updating the map that is passed in, not creating a new
	// map
	public boolean[][] updateCellMap(boolean cellMap[][], List<IRoom> rooms, List<Corridor> corridors) {
		rooms.forEach(room -> {
//			System.out.println("generating room @ (" + room.getOrigin().getX() + ", " + room.getOrigin().getY() + "), width=" + room.getBox().getWidth() + ", height=" + room.getBox().getHeight());
			for (int w = 0; w < room.getBox().getWidth(); w++) {
				for (int d = 0; d < room.getBox().getHeight(); d++) {
					int x = room.getOrigin().getX() + w;
					int y = room.getOrigin().getY() + d;
					if ( x >= 0 && y >= 0 && x < getWidth() && y < getHeight()) {
						cellMap[room.getOrigin().getX() + w][room.getOrigin().getY() + d] = true;
					}
				}
			}
		});
		
		if (corridors == null || corridors.isEmpty()) return cellMap;
		
		corridors.forEach(corridor -> {
			for (int w = 0; w < corridor.getBox().getWidth(); w++) {
				for (int d = 0; d < corridor.getBox().getHeight(); d++) {
					int x = corridor.getBox().getOrigin().getX() + w;
					int y = corridor.getBox().getOrigin().getY() + d;
					if ( x >= 0 && y >= 0 && x < getWidth() && y < getHeight()) {
						// turn on only cells that aren't turned on yet (necessary check? - yes, beause in future it won't be boolean but tile stack)
						if (!cellMap[corridor.getBox().getOrigin().getX() + w][corridor.getBox().getOrigin().getY() + d])
						cellMap[corridor.getBox().getOrigin().getX() + w][corridor.getBox().getOrigin().getY() + d] = true;
					}
				}
			}
		});
		
		return cellMap;
	}

	
	/**
	 * 
	 * @param sourceRooms
	 * @param movementFactor dictates how much to move a R at a time. lower number
	 *                       mean the Rs will be closer together but more iterations
	 *                       performed
	 * @return
	 */
	public List<IRoom> separateRooms(List<IRoom> sourceRooms, int movementFactor) {
		// duplicate the soure rooms
		List<IRoom> workingRooms = new ArrayList<>(sourceRooms);

		// find the center C of the bounding box of all the rooms.
		Coords2D center = getCenter(workingRooms);

		boolean hasIntersections = true;
		while (hasIntersections) {
			hasIntersections = iterateSeparationStep(center, workingRooms, movementFactor);
		}

		return workingRooms;
	}

	/**
	 * 
	 */
	public List<IRoom> findIntersections(IRoom room, List<IRoom> rooms) {
		ArrayList<IRoom> intersections = new ArrayList<>();

		for (IRoom intersectingRect : rooms) {
			if (!room.equals(intersectingRect) && intersectingRect.getBox().intersects(room.getBox())) {
				intersections.add(intersectingRect);
			}
		}
		return intersections;
	}

	/**
	 * 
	 * @param random
	 * @return
	 */
	protected boolean[][] initMap(Random random) {
		return initMap(this.width, this.height, random);
	}

	/**
	 * 
	 * @param width
	 * @param height
	 * @param random
	 * @return
	 */
	private boolean[][] initMap(int width, int height, Random random) {
		boolean[][] map = new boolean[width][height];
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				map[x][y] = true;
			}
		}
		return map;
	}

	/**
	 * 
	 * @param random
	 * @return
	 */
	private List<IRoom> initRooms(Random random) {
		return initRooms(random, this.width, this.height, this.minRoomSize, this.maxRoomSize);
	}

	/**
	 * 
	 * @param random
	 * @param width
	 * @param height
	 * @return
	 */
	private List<IRoom> initRooms(Random random, final int width, final int height, final int minRoomSize,
			final int maxRoomSize) {
		List<IRoom> rooms = new ArrayList<>();
		Coords2D centerPoint = new Coords2D(width / 2, height / 2);

		// TODO needs to be centered on level
		Rectangle2D levelBoundingBox = new Rectangle2D((centerPoint.getX() - spawnBoxWidth / 2) - 8,
				(centerPoint.getY() - spawnBoxWidth / 2) - 8, spawnBoxWidth + 16, spawnBoxHeight + 16);
		Rectangle2D spawnBoundingBox = new Rectangle2D(0, 0, spawnBoxWidth, spawnBoxHeight);

		IRoom startRoom = generateRoom(random, centerPoint, spawnBoundingBox, minRoomSize, maxRoomSize);
		// TEST
//		startRoom.setOrigin(new Coords2D(80, 20));
		startRoom.setRole(RoomRole.MAIN).setType(NodeType.START).setMaxDegrees(5).setId(0);
		startRoom.getFlags().add(RoomFlag.NO_INTERSECTION);
		rooms.add(startRoom);

		// TEST[i like it] seed the level with small rooms that will be removed (but
		// will bring down the mean size)
		for (int roomIndex = 0; roomIndex < 5; roomIndex++) { // TODO make the number of seed rooms variable
			IRoom room = generateRoom(random, centerPoint, spawnBoundingBox, 5, 5); // TODO 5 -> make variable
			// constrainsts check
			if (!boundaryConstraint(levelBoundingBox, room)) {
				LOGGER.debug("room at {} [w:{}, h:{}] failed boundaries test", room.getBox().getOrigin(),
						room.getBox().getWidth(), room.getBox().getHeight());
				continue;
			}
			// set the id
			room.setId(rooms.size());
			room.setMaxDegrees(5); // TODO get from generator
			// add to list
			rooms.add(room);
			LOGGER.debug("seed room id -> {}, size -> {}", room.getId(), room.getBox());
		}

		// TODO need to add start room and flagged
		for (int roomIndex = 0; roomIndex < numberOfRooms; roomIndex++) {
			IRoom room = generateRoom(random, centerPoint, spawnBoundingBox, minRoomSize, maxRoomSize);
			// constrainsts check
			if (!boundaryConstraint(levelBoundingBox, room)) {
				LOGGER.debug("room at {} [w:{}, h:{}] failed boundaries test", room.getBox().getOrigin(),
						room.getBox().getWidth(), room.getBox().getHeight());
				continue;
			}
			// set the id
			room.setId(rooms.size());
			room.setMaxDegrees(5); // TODO get from generator
			// add to list
			rooms.add(room);
//			LOGGER.debug("room.id -> {}", room.getId());
		}

		// have to have at least one end room
		IRoom endRoom = generateRoom(random, centerPoint, spawnBoundingBox, minRoomSize, maxRoomSize);
		// TEST
//		endRoom.setOrigin(new Coords2D(20, 80));
		endRoom.setRole(RoomRole.MAIN).setType(NodeType.END).setMaxDegrees(1).setId(rooms.size());
		endRoom.getFlags().add(RoomFlag.NO_INTERSECTION);
		rooms.add(endRoom);
		LOGGER.debug("end room id -> {}, size of list -> {}", endRoom.getId(), rooms.size());

//		// TEST: add a secret room
//		IRoom secretRoom = generateRoom(random, centerPoint, spawnBoundingBox, minRoomSize, maxRoomSize);
//		secretRoom.setRole(RoomRole.MAIN).setType(NodeType.STANDARD).setMaxDegrees(1).setId(rooms.size());
//		secretRoom.getFlags().add(RoomFlag.ANCHOR);
//		rooms.add(secretRoom);
//		LOGGER.debug("secret room id -> {}, size of list -> {}", secretRoom.getId(), rooms.size());

		// TEST add anchor room
//		IRoom anchorRoom = generateRoom(random, centerPoint, spawnBoundingBox, minRoomSize, maxRoomSize);
//			anchorRoom.setRole(RoomRole.MAIN)	
//			.setMaxDegrees(5)
//			.setId(rooms.size()); 
//		anchorRoom.getFlags().add(RoomFlag.ANCHOR);
//		rooms.add(anchorRoom);
		
//		// TEST
//		anchorRoom = generateRoom(random, centerPoint, levelBoundingBox, minRoomSize, maxRoomSize);
//			anchorRoom.setRole(RoomRole.MAIN)	
//			.setMaxDegrees(5)
//			.setId(rooms.size());
//		anchorRoom.getFlags().add(RoomFlag.ANCHOR);
//		rooms.add(anchorRoom);
//		LOGGER.debug("anchor room id -> {}, size of list -> {}", anchorRoom.getId(), rooms.size());

		return rooms;
	}

	/**
	 * 
	 * @param boundary
	 * @param room
	 * @return
	 */
	private boolean boundaryConstraint(Rectangle2D boundary, IRoom room) {
		if (room.getMinX() >= boundary.getMaxX() || room.getMaxX() > boundary.getMaxX()
				|| room.getMaxX() <= boundary.getMinX() || room.getMinX() < boundary.getMinX()
				|| room.getMinY() >= boundary.getMaxY() || room.getMaxY() > boundary.getMaxY()
				|| room.getMaxY() <= boundary.getMinY() || room.getMinY() < boundary.getMinY()) {
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @param random
	 * @param centerPoint
	 * @param boundingBox
	 * @param minRoomSize
	 * @param maxRoomSize
	 * @return
	 */
	private IRoom generateRoom(Random random, Coords2D centerPoint, Rectangle2D boundingBox, int minRoomSize,
			int maxRoomSize) {

		int sizeX = maxRoomSize == minRoomSize ? minRoomSize : random.nextInt(maxRoomSize - minRoomSize) + minRoomSize;
		int sizeY = maxRoomSize == minRoomSize ? minRoomSize : random.nextInt(maxRoomSize - minRoomSize) + minRoomSize;
		int offsetX = (random.nextInt(boundingBox.getWidth()) - (boundingBox.getWidth() / 2)) - (sizeX / 2);
		int offsetY = (random.nextInt(boundingBox.getHeight()) - (boundingBox.getHeight() / 2)) - (sizeY / 2);
		IRoom room = new Room(new Coords2D(centerPoint.getX() + offsetX, centerPoint.getY() + offsetY), sizeX, sizeY);
		return room;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getSpawnBoxWidth() {
		return spawnBoxWidth;
	}

	public int getSpawnBoxHeight() {
		return spawnBoxHeight;
	}

	public int getNumberOfRooms() {
		return numberOfRooms;
	}

	public int getMinRoomSize() {
		return minRoomSize;
	}

	public int getMaxRoomSize() {
		return maxRoomSize;
	}

	public int getMovementFactor() {
		return movementFactor;
	}

	public double getMeanFactor() {
		return meanFactor;
	}

	public double getPathFactor() {
		return pathFactor;
	}

	@Override
	public ILevelGenerator withWidth(int width) {
		this.width = width;
		return this;
	}

	@Override
	public ILevelGenerator withHeight(int height) {
		this.height = height;
		return this;
	}

	public DungeonLevelGenerator withSpawnBoxWidth(int width) {
		this.spawnBoxWidth = width;
		return this;
	}

	public DungeonLevelGenerator withSpawnBoxHeight(int height) {
		this.spawnBoxHeight = height;
		return this;
	}

	public DungeonLevelGenerator withNumberOfRooms(int num) {
		this.numberOfRooms = num;
		return this;
	}

	public DungeonLevelGenerator withMinRoomSize(int num) {
		this.minRoomSize = num;
		return this;
	}

	public DungeonLevelGenerator withMaxRoomSize(int num) {
		this.maxRoomSize = num;
		return this;
	}

	public DungeonLevelGenerator withMovementFactor(int factor) {
		this.movementFactor = factor;
		return this;
	}

	public DungeonLevelGenerator withMeanFactor(double factor) {
		this.meanFactor = factor;
		return this;
	}

	public DungeonLevelGenerator withPathFactor(double factor) {
		this.pathFactor = factor;
		return this;
	}

	public boolean isHardBoundary() {
		return hardBoundary;
	}

	public void setHardBoundary(boolean hardBoundary) {
		this.hardBoundary = hardBoundary;
	}

	/// TEMP
	/**
	 * 
	 * @param rooms
	 * @return
	 */
	public Map<Integer, IRoom> mapRooms(List<IRoom> rooms) {
		Map<Integer, IRoom> map = new HashMap<>();
		rooms.forEach(room -> {
			map.put(Integer.valueOf(room.getId()), room);
		});
		return map;
	}
}
