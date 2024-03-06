/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.maze;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.*;

import java.util.*;
import java.util.function.Consumer;

/**
 *
 * @author Mark Gottschling on Nov 8, 2023
 *
 */
public class MazeLevelGenerator2D {

    ///////////// Default Constants ///////////////
    /*
     * dimensions of the level and rooms have to be odd in order
     * for proper spacing of passage/corridor/halls.
     */
    private static final int DEFAULT_WIDTH = 65;
    private static final int DEFAULT_HEIGHT = 65;
    private static final int DEFAULT_NUMBER_OF_ROOMS = 35;
    private static final int DEFAULT_MIN_SIZE = 7;
    private static final int DEFAULT_MAX_SIZE = 19;
    private static final int DEFAULT_MIN_DEGREES = 3;
    private static final int DEFAULT_MAX_DEGREES = 5;
    private static final double DEFAULT_MEAN_FACTOR = 1.15;
    private static final double DEFAULT_RUN_FACTOR = 0.8; // 0 = anywhere, 1 = tail
    private static final double DEFAULT_CURVE_FACTOR = 0.8; // 0 = curvy, 1 = straight
    private static final int DEFAULT_MAX_ATTEMPTS = 500;
    private static final int DEFAULT_MIN_CORRIDOR_SIZE = 25;
    private static final int DEFAULT_MAX_CORRIDOR_SIZE = 50;
    private static final int DEFAULT_FILL_ATTEMPTS = 3;
    private static final int DEFAULT_FILL_ROOMS_PER_SIZE = 5;
    private static final int MIN_RECTANGLE_WIDTH = 5;
    private static final int MIN_RECTANGLE_HEIGHT = 5;

    ////////////// generator properties ///////////////
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private Rectangle2D levelBoundary = new Rectangle2D(0, 0, this.width, this.height);

    private int numberOfRooms = DEFAULT_NUMBER_OF_ROOMS;
    private int minSize = DEFAULT_MIN_SIZE;
    private int maxSize = DEFAULT_MAX_SIZE;
    private int minDegrees = DEFAULT_MIN_DEGREES;
    private int maxDegrees = DEFAULT_MAX_DEGREES;
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private int minCorridorSize = DEFAULT_MIN_CORRIDOR_SIZE;
    private int maxCorridorSize = DEFAULT_MAX_CORRIDOR_SIZE;
    private double meanFactor = DEFAULT_MEAN_FACTOR;
    private double runFactor = DEFAULT_RUN_FACTOR;
    private double curveFactor = DEFAULT_CURVE_FACTOR;

    private int fillAttempts = DEFAULT_FILL_ATTEMPTS;
    private int fillRoomsPerSize = DEFAULT_FILL_ROOMS_PER_SIZE;

    private Random random = new Random();

    /*
     * An object to generate and keep track of ids.
     */
    private final IdGenerator idGenerator = new IdGenerator(CellType.values().length + 1);

    /*
     * A list of all rooms that are supplied as input to the generator.
     */
    private List<IRoom2D> suppliedRooms;
    private IRoom2D startRoom;
    private IRoom2D endRoom;

    /*
     * working variables
     */
    private List<Connector2D> connectors;
    private Map<Integer, Region2D> regionMap;

    /**
     *
     * @param builder
     */
    public MazeLevelGenerator2D(Builder builder) {
        setWidth(builder.width);
        setHeight(builder.height);
        this.minSize = builder.minSize;
        this.maxSize = builder.maxSize;
        this.minDegrees = builder.minDegrees;
        this.maxDegrees = builder.maxDegrees;
        this.numberOfRooms = builder.numberOfRooms;
        this.maxAttempts = builder.attemptsMax;
        this.meanFactor = builder.meanFactor;
        this.runFactor = builder.runFactor;
        this.curveFactor = builder.curveFactor;
        this.fillAttempts = builder.fillAttempts;
        this.fillRoomsPerSize = builder.fillRoomsPerSize;

        this.startRoom = builder.startRoom;
        this.endRoom = builder.endRoom;

        if (builder.random != null) {
            this.random = builder.random;
        }
    }

    /**
     *
     * @return
     */
    public Optional<ILevel2D> generate() {
        // initialize values
        ILevel2D level = new Level2D(width, height);
        connectors = new ArrayList<>();
        regionMap = new HashMap<>();

        // TODO validate() <-- or this goes in the Builder.build()... maybe not as you can set individual properties in the generator too.
        isValidInitialProperties();

        // generateRooms() includes checking constraints as each room is added
        List<IRoom2D> rooms = addRooms(level, this.meanFactor);

        // NOTE This is moot as rooms are filtered as they are added
        // filter some rooms out. if something like this is re-implemented, add it to generateRooms
//        rooms = removeRoomsBelowAreaMean(rooms, meanFactor);

        // map rooms to regions
        Map<Integer, Region2D> regions = mapRegions(rooms);
        setRegionMap(regions);

        // carve passages
        carve(level);

        // add connectors
        boolean isDiscoverSuccess = discoverConnectors(level);
        if (!isDiscoverSuccess) {
            return Optional.empty();
        }

        // merge regions
        mergeRegions(level, random);

        // back-fill dead-ends
        backFill(level);

        return Optional.of(level);
    }

    /**
     *
     */
    private boolean isValidInitialProperties() {
        // NOTE can't alter values of start, end and custom rooms - just fail

        // TODO if the room is outside the boundary, then fail

        // TODO all the rest

        return true;
    }

    /**
     * TODO figure out a way to seperate generating start, end, and rooms from this method
     * TODO this should be only random room collection. also don't update level from here
     * TODO OR all main method work on level directly, but then have to ensure that nothing is using
     * TODO the values from generator, ie generator.startRoom
     * @param level the level to add rooms to.
     * @param localMeanFactor the mean factor to compare room area against.
     * @return a list of generated rooms.
     */
    public List<IRoom2D> addRooms(ILevel2D level, double localMeanFactor) {
        List<IRoom2D> rooms = new ArrayList<>();
        idGenerator.reset();

        // calculate the random ranges
        int xRange = this.width - this.minSize;
        int yRange = this.height - this.minSize;

        // generate a start room
        Optional<IRoom2D> startRoom = generateStartRoom(xRange, yRange, minSize, maxSize, levelBoundary, random);
        if (startRoom.isEmpty()) {
            return rooms;
        }
        rooms.add(startRoom.get());
        level.setStartRoom(startRoom.get());

        // generate an end room
        Optional<IRoom2D> endRoom = generateEndRoom(xRange, yRange, minSize, maxSize, levelBoundary, rooms, random);
        if (endRoom.isEmpty()) {
            rooms.clear();
            return rooms;
        }
        rooms.add(endRoom.get());
        level.setEndRoom(endRoom.get());

        // supplied rooms
        if (suppliedRooms != null) {
            suppliedRooms.stream()
                    .filter(room -> isRoomValid(room, levelBoundary))
                    .filter(room -> !hasIntersections(room.getBox(), rooms))
                    .forEach(room -> {
                // don't add if they intersect with the start, end or other supplied rooms
//                if (isRoomValid(room, levelBoundary)) {
//                    if (!hasIntersections(room.getBox(), rooms)) {
                        room.setId(idGenerator.next());
                        rooms.add(room);
//                    }
//                }
            });
        }

        // random rooms
        List<IRoom2D> randomRooms = generateRandomRooms(xRange, yRange, minSize, maxSize, levelBoundary, localMeanFactor, rooms, random);
        Dungeons.LOGGER.debug("roomCount -> {}, numberfOfRooms -> {}", rooms.size(), numberOfRooms);

        // TODO level is never updated with random rooms?!

        // update level
        level.setRooms(rooms);
        level.getGrid().add(rooms);

        int deltaRooms = numberOfRooms - rooms.size();
        Dungeons.LOGGER.debug("deltaRooms -> {}", deltaRooms);
        if (deltaRooms > 0) {
            List<IRoom2D> fillRooms = generateFillRooms(deltaRooms, xRange, yRange, minSize, maxSize, levelBoundary, localMeanFactor, rooms, random);
            Dungeons.LOGGER.debug("# fill rooms -> {}", fillRooms.size());
            fillRooms = placeFillRooms(level, fillRooms);

            // add the fill rooms to the rooms list and grid
            level.getRooms().addAll(fillRooms);
            level.getGrid().add(fillRooms);
        }

        return rooms;
    }

    private Optional<IRoom2D> generateStartRoom(int xRange, int yRange, int minSize, int maxSize, Rectangle2D levelBoundary, Random random) {
        IRoom2D room = null;
        if (this.startRoom == null) {
            for (int maxAttempts = 0; maxAttempts < 5; maxAttempts++) {
                room = generateRoom2(xRange, yRange, minSize, maxSize, levelBoundary, 2, maxDegrees, random);
                if (isRoomValid(room, levelBoundary)) {
                    break;
                }
                room = null;
            }
            if (room == null) {
                return Optional.empty();
            }
        } else {
            room = this.startRoom;
        }
        room.setStart(true);
        return Optional.of(room);
    }

    private Optional<IRoom2D> generateEndRoom(int xRange, int yRange, int minSize, int maxSize, Rectangle2D levelBoundary, List<IRoom2D> rooms, Random random) {
        IRoom2D room = null;
        if (this.endRoom == null) {
            for (int maxAttempts = 0; maxAttempts < 5; maxAttempts++) {
                room = generateRoom2(xRange, yRange, minSize, maxSize, levelBoundary, 1, 1, random);
                if (isRoomValid(room, levelBoundary)) {
                    if (!hasIntersections(room.getBox(), rooms)) {
                        break;
                    }
                }
                room = null;
            }
            if (room == null) {
                return Optional.empty();
            }
        } else {
            room = this.endRoom;
        }
        room.setEnd(true);

        return Optional.of(room);
    }

    private List<IRoom2D> generateRandomRooms(int xRange, int yRange, int minSize, int maxSize, Rectangle2D levelBoundary, double localMeanFactor, List<IRoom2D> rooms, Random random) {
        List<IRoom2D> randomRooms = new ArrayList<>();
        int roomCount = 0;

        int localMinSize = minSize;
        int localMaxSize = maxSize;

        for (int attemptCount = 0; attemptCount < maxAttempts; attemptCount++) {

            IRoom2D room = generateRoom2(xRange, yRange, localMinSize, localMaxSize, levelBoundary, minDegrees, maxDegrees, random);
            if (!isRoomValid(room, levelBoundary)) {
                continue;
            }

            //ensure that the box doesn't overlap another existing box
            // NOTE this would be more efficient if using a Interval-BST
            if (hasIntersections(room.getBox(), rooms)) {
                continue;
            }

            rooms.add(room);
            roomCount++;

            if (roomCount >= numberOfRooms) {
                Dungeons.LOGGER.debug("attemptCount -> {}", attemptCount);
                break;
            }
        }

        return randomRooms;
    }

    private List<IRoom2D> generateFillRooms(int numberOfRooms, int xRange, int yRange, int minSize, int maxSize, Rectangle2D levelBoundary, double localMeanFactor, List<IRoom2D> rooms, Random random) {
        // add extra rooms of varying size to fill the gaps
        List<IRoom2D> fillRooms = new ArrayList<>();
        for (int i = 0 ; i < numberOfRooms; i++) {
            // generate some more rooms
            IRoom2D fillRoom = generateRoom2(xRange, yRange, minSize, maxSize, levelBoundary, minDegrees, maxDegrees, random);
            fillRooms.add(fillRoom);
        }

        // add small joiner rooms
        // currently adds 5 * 9x9, 5 * 7x7, 5 * 5x5
        // this part will change in Dungeons2 as we would want something like provideFillRooms() which either builds the rooms or fetchs premade structures
        for (int i = 0; i < this.fillRoomsPerSize; i++) {
            IRoom2D fillRoom = generateRoom2(xRange, yRange, 9, 9, levelBoundary, minDegrees, maxDegrees, random);
            fillRooms.add(fillRoom);
            fillRoom = generateRoom2(xRange, yRange, 7, 7, levelBoundary, minDegrees, maxDegrees, random);
            fillRooms.add(fillRoom);
            fillRoom = generateRoom2(xRange, yRange, 5, 5, levelBoundary, minDegrees, maxDegrees, random);
            fillRooms.add(fillRoom);
        }

        return fillRooms;
    }

    /**
     *
     * @param level
     * @param suppliedRooms
     * @return
     */
    private List<IRoom2D> placeFillRooms(ILevel2D level, List<IRoom2D> suppliedRooms) {
        List<IRoom2D> newRooms = new ArrayList<>();

        Grid2D voidGrid = null;
        try {
            voidGrid = level.getGrid().clone();
        } catch(Exception ignore) {
            Dungeons.LOGGER.error("unable to clone grid - unable to add fill rooms:", ignore);
            return newRooms;
        }

        for (int fillAttemptIndex = 0; fillAttemptIndex < this.fillAttempts; fillAttemptIndex++) {
//            Dungeons.LOGGER.debug("attempt # -> {}", fillAttemptIndex);
            List<IRoom2D> rooms = new ArrayList<>();
            // scan the void grid looking for empty space candidates
            List<Rectangle2D> maximalRectangleList = getMaximalRectangles(voidGrid);
//            Dungeons.LOGGER.debug("size of rectangles -> {}", maximalRectangleList.size());
            // randomize the sort of the list
            Collections.shuffle(maximalRectangleList);
//            Dungeons.LOGGER.debug("size of supplied rooms -> {}", suppliedRooms.size());
            // for each of the supplied rooms
            for (IRoom2D suppliedRoom : suppliedRooms) {
                // a list to manage the rectangles to remove
                List<Rectangle2D> rectangleRemoveList = new ArrayList<>();
                // get the size of the room
                Coords2D size = new Coords2D(suppliedRoom.getWidth(), suppliedRoom.getHeight());
//                Dungeons.LOGGER.debug("testing room -> {}, size -> {}", suppliedRoom.getId(), size);
//                Dungeons.LOGGER.debug("size of rectangles2 -> {}", maximalRectangleList.size());
                // scan all the rectangles
                for (Rectangle2D r : maximalRectangleList) {
//                    Dungeons.LOGGER.debug("testing against rectangle -> {}x{}", r.getWidth(), r.getHeight());
                    if (size.getX() <= r.getWidth() && size.getY() <= r.getHeight()) {
                        // find the delta of x,y between size and r
                        int dx = r.getWidth() - size.getX();
                        int dy = r.getHeight() - size.getY();

                        // randomize an offset for the room
                        int ox = 0;
                        if (dx > 0) {
                            ox = random.nextInt(dx);
                            if (ox % 2 != 0) {
                                ox++;
                            }
                        }
                        int oy = 0;
                        if (dy > 0) {
                            oy = random.nextInt(dy);
                            if (oy % 2 != 0) {
                                oy++;
                            }
                        }

                        // update supplied rooms coords
                        suppliedRoom.getOrigin().setLocation(r.getMinX() + ox, r.getMinY() + oy);
                        rooms.add(suppliedRoom);

                        Dungeons.LOGGER.debug("adding fill room -> {}", suppliedRoom);

                        // add all intersecting rectangle to the remove list
                        for (Rectangle2D r2 : maximalRectangleList) {
                            if (r2.intersects(suppliedRoom.getBox())) {
                                rectangleRemoveList.add(r2);
                            }
                        }
                        break;
                    }
                }

                // remove intersecting rectangles
                maximalRectangleList.removeAll(rectangleRemoveList);
                rectangleRemoveList.clear();

                if (maximalRectangleList.isEmpty()) {
                    break;
                }
            }

            voidGrid.add(rooms);
            newRooms.addAll(rooms);
            suppliedRooms.removeAll(rooms);
            rooms.clear();
        }

        Dungeons.LOGGER.debug("size of added rooms -> {}", newRooms.size());
        return newRooms;
    }

    /**
     * NOTE this method does not assign an id to a room.
     * @param xRange
     * @param yRange
     * @param minSize
     * @param maxSize
     * @param boundary
     * @param minDegrees
     * @param maxDegrees
     * @param random
     * @return
     */
    @Deprecated
    public Optional<IRoom2D> generateRoom(int xRange, int yRange, int minSize, int maxSize,
                  Rectangle2D boundary, int minDegrees, int maxDegrees, Random random) {

        Optional<IRoom2D> result = Optional.empty();

        int x = random.nextInt(xRange);
        int y = random.nextInt(yRange);
        // ensure x and y are even numbers (because the map is 0-indexed ie. a wall will always be on 0)
        if (x % 2 != 0 || y % 2 != 0) {
            return result;
        }
        int xSize = maxSize == minSize ? minSize : random.nextInt(maxSize - minSize) + minSize;
        int ySize = maxSize == minSize ? minSize : random.nextInt(maxSize - minSize) + minSize;

        // ensure that xSize and ySize are odd lengths
        if (x % 2 != 0) x++;
        if (y % 2 != 0) y++;

        // ensure that the box is within the level boundaries
        Rectangle2D box = new Rectangle2D(x, y, xSize, ySize);
        if (!isWithinBoundary(box, boundary)) {
            return result;
        }

        // calculate degrees
        int degrees = (maxDegrees == minDegrees) ? minDegrees : random.nextInt(maxDegrees - minDegrees) + minDegrees;

        IRoom2D room = new Room2D(box);
        room.setDegrees(degrees);

        return Optional.of(room);
    }

    public IRoom2D generateRoom2(int xRange, int yRange, int minSize, int maxSize,
                                 Rectangle2D boundary, int minDegrees, int maxDegrees, Random random) {

        int x = random.nextInt(xRange);
        int y = random.nextInt(yRange);
        // ensure x and y are even numbers (because the map is 0-indexed ie. a wall will always be on 0)
        if (x % 2 != 0) x++;
        if (y % 2 != 0) y++;

        Coords2D size = generateRoomSize(minSize, maxSize);

        // ensure that the box is within the level boundaries
        Rectangle2D box = new Rectangle2D(x, y, size.getX(), size.getY());

        // calculate degrees
        int degrees = (maxDegrees == minDegrees) ? minDegrees : random.nextInt(maxDegrees - minDegrees) + minDegrees;

        IRoom2D room = new Room2D(box);
        room.setId(idGenerator.next());
        room.setDegrees(degrees);

        return room;
    }

    public boolean isRoomValid(IRoom2D room, Rectangle2D boundary) {
        if (room.getOrigin().getX() % 2 != 0) return false;
        if (room.getOrigin().getY() %2 != 0) return false;
        return isWithinBoundary(room.getBox(), boundary);
    }

    /**
     * Detects all the maximal rectangles contained in the 'empty' space of the grid.
     * Based on https://www.researchgate.net/publication/221249132_Object_Descriptors_Based_on_a_List_of_Rectangles_Method_and_Algorithm
     * @param voidGrid
     * @return
     */
    private List<Rectangle2D> getMaximalRectangles(Grid2D voidGrid) {
        List<Rectangle2D> rectangles = new ArrayList<>();

        int[][] dN = new int[voidGrid.getSize().getX()][voidGrid.getSize().getY()];

        // NOTE all levels have a wall border - this counts as empty space
        for (int col = 0; col < voidGrid.getSize().getX(); col++) {
            dN[col][0] = 0;
        }

        // NOTE start at row = 1
        for (int row = 1; row < voidGrid.getSize().getY(); row++) {
            for (int col = 0; col < voidGrid.getSize().getX(); col++) {
                // if empty then set to 0
                if(!isEmptyOrBorder(voidGrid, col, row)) {
                    dN[col][row] = -1;
                } else {
                    dN[col][row] = dN[col][row - 1] + 1;
                }
            }
        }

        int[][] dS = new int[voidGrid.getWidth()][voidGrid.getHeight()];
        for (int col = 0; col < voidGrid.getWidth(); col++) {
            dS[col][voidGrid.getHeight()-1] = isEmptyOrBorder(voidGrid, col, voidGrid.getHeight()-1) ? 0 : -1;
        }
        for (int row = voidGrid.getHeight()-2; row >= 0; row--) {
            for (int col = 0; col < voidGrid.getWidth(); col++) {
                if (!isEmptyOrBorder(voidGrid, col, row)) {
                    dS[col][row] = -1;
                } else {
                    dS[col][row] = dS[col][row + 1] + 1;
                }
            }
        }

        // main loop
        for (int col = voidGrid.getWidth() -1; col >=0; col--) {
            int maxS = voidGrid.getHeight();
            for (int row = voidGrid.getHeight() -1; row >= 0; row--) {
                maxS++;
                if (isEmptyOrBorder(voidGrid, col, row) && (col == 0 || !isEmptyOrBorder(voidGrid, col -1, row))) {
                    int N = dN[col][row];
                    int S = dS[col][row];
                    int width = 1;
                    while(col + width < voidGrid.getWidth() && isEmptyOrBorder(voidGrid, col + width, row)) {
                        int nextN = dN[col + width][row];
                        int nextS = dS[col + width][row];
                        if ((nextN < N) || (nextS < S)) {
                            if (S < maxS) {
                                if (width >= MIN_RECTANGLE_WIDTH && (N + S + 1) >= MIN_RECTANGLE_HEIGHT) {
                                    rectangles.add(new Rectangle2D(col, row - N, width, N + S + 1));
                                }
                            }
                            if (nextN < N) N = nextN;
                            if (nextS < S) S = nextS;
                        }
                        width++;
                    }
                    if (S < maxS) {
                        if (width >= MIN_RECTANGLE_WIDTH && (N + S + 1) >= MIN_RECTANGLE_HEIGHT) {
                            rectangles.add(new Rectangle2D(col, row - N, width, N + S + 1));
                        }
                    }
                    maxS = 0;
                }
            }
        }
        return rectangles;
    }

    /**
     *
     * @param grid
     * @param x
     * @param y
     * @return
     */
    private boolean isEmptyOrBorder(Grid2D grid, int x, int y) {
        if (grid.get(x, y).getType() == CellType.ROCK || (grid.get(x, y).getType() == CellType.WALL)) {
            return true;
        }
        return false;
    }

    private Coords2D generateRoomSize(int minSize, int maxSize) {
        int xSize = maxSize == minSize ? minSize : random.nextInt(maxSize - minSize) + minSize;
        int ySize = maxSize == minSize ? minSize : random.nextInt(maxSize - minSize) + minSize;

        // ensure that xSize and ySize are odd lengths
        if (xSize % 2 != 1) {
            xSize++;
        }
        if (ySize % 2 != 1) {
            ySize++;
        }
        return new Coords2D(xSize, ySize);
    }

    /**
     *
     * @param rooms
     * @param meanFactor
     * @return
     */
    public List<IRoom2D> removeRoomsBelowAreaMean(List<IRoom2D> rooms, final double meanFactor) {
        List<IRoom2D> mainRooms = new ArrayList<>();
        int totalArea = 0;
        for (IRoom2D room : rooms) {
            totalArea += room.getBox().getWidth() * room.getBox().getHeight();
        }

        int meanArea = (int) ((totalArea / rooms.size()) * meanFactor);

        // process each room
        rooms.forEach(room -> {
            if (room.getWidth() * room.getHeight() >= meanArea) {
                mainRooms.add(room);
            }
        });

        return mainRooms;
    }

    /**
     *
     * @param rooms
     * @return
     */
    public Map<Integer, Region2D> mapRegions(List<IRoom2D> rooms) {
        Map<Integer, Region2D> regions = new HashMap<>();
        rooms.forEach(room -> {
            Region2D region = new Region2D(room.getId(), room.getBox());
            region.setType(RegionType.ROOM);
            regions.put(room.getId(), region);
        });
        return regions;
    }

    private boolean isWithinBoundary(Rectangle2D room, Rectangle2D boundary) {
        return room.getMaxX() <= boundary.getMaxX() && room.getMinX() >= boundary.getMinX()
                && room.getMaxY() <= boundary.getMaxY()
                && room.getMinY() >= boundary.getMinY();
    }

    /**
     *
     * @param room
     * @param rooms
     * @return
     */
    public boolean hasIntersections(Rectangle2D room, List<IRoom2D> rooms) {

        for (IRoom2D intersectingRect : rooms) {
            if (!room.equals(intersectingRect.getBox()) && intersectingRect.getBox().intersects(room)) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param level
     */
    public void carve(ILevel2D level) {
        // scan all cells for solid rock
        // NOTE skip border cells as they are "walls"
        for (int x = 1; x < level.getWidth()-1; x+=2) {
            for (int y = 1; y < level.getHeight()-1; y+=2) {
                // find an unvisited rock
                if (level.getGrid().get(x, y).getType() == CellType.ROCK) {
                    // add cell to the active list
                    prims(level, new Coords2D(x, y));
                }
            }
        }
    }

    /**
     *
     * @param level
     */
    public boolean discoverConnectors(ILevel2D level) {

        // scan all rooms to see if they already have doorways
        List<Integer> ignoreIds = new ArrayList<>();
        boolean customConnectorsSuccess = true;
        for(IRoom2D room : level.getRooms()) {
            if (!room.getDoorways().isEmpty()) {
                // add doorways to connectors
                for(Coords2D door : room.getDoorways()) {
                    int x = door.getX();
                    int y = door.getY();
                    customConnectorsSuccess &= generateConnector(level, connectors, x, y);
                }
                // add room/region id to list so they aren't re-processed
                ignoreIds.add(room.getId());
            }
        }

        if (!customConnectorsSuccess) {
            Dungeons.LOGGER.warn("failed to merge custom room to the level.");
            return false;
        }

        // scan all cells for
        // 1. wall
        // 2. adjacent to two regions of different ids.
        // NOTE skip border cells as they will not have connectors
        for (int x = 1; x < level.getWidth()-1; x++) {
            for (int y = 1; y < level.getHeight()-1; y++) {
                // find any wall
                if (level.getGrid().get(x, y).getType() == CellType.WALL) {
                    generateConnector(level, connectors, x, y, ignoreIds);
                }
            }
        }

        return true;
    }

    private boolean generateConnector(ILevel2D level, List<Connector2D> connectors, int x, int y) {
        return generateConnector(level, connectors, x, y, null);
    }

    /**
     *
     * @param level
     * @param x
     * @param y
     */
    private boolean generateConnector(ILevel2D level, List<Connector2D> connectors, int x, int y, List<Integer> ignoreIds) {
        int northId = level.getGrid().get(x, y-1).getRegionId();
        int southId = level.getGrid().get(x, y+1).getRegionId();
        // test east and west
        int eastId = level.getGrid().get(x+1, y).getRegionId();
        int westId = level.getGrid().get(x-1, y).getRegionId();

        if (ignoreIds != null && (ignoreIds.contains((int)northId) || ignoreIds.contains((int)southId) ||
                ignoreIds.contains((int)eastId) || ignoreIds.contains((int)westId))) {
            return false;
        }

        if (northId >= idGenerator.getStart() && southId >= idGenerator.getStart() && northId != southId) {
            // get regions
            Region2D region1 = getRegionMap().get((int)northId);
            Region2D region2 = getRegionMap().get((int)southId);

            // test that x,y is valid position on the x-axis (east-west) ie away from corners
            if (region1.getType() == RegionType.ROOM) {
                if (x < region1.getBox().getMinX() + 2 || x > region1.getBox().getMaxX() - 2) {
                    return false;
                }
            }
            if (region2.getType() == RegionType.ROOM) {
                if (x < region2.getBox().getMinX() + 2 || x > region2.getBox().getMaxX() - 2) {
                    return false;
                }
            }

            Connector2D connector = new Connector2D(x, y, region1, region2);
            // add connector to list
            connectors.add(connector);
            // update grid with id = CONNECTOR
            level.getGrid().get(connector.getCoords()).setType(CellType.CONNECTOR);
        }
        else if (eastId >= idGenerator.getStart() && westId >= idGenerator.getStart() && eastId != westId) {
            Region2D region1 = getRegionMap().get((int)eastId);
            Region2D region2 = getRegionMap().get((int)westId);

            if (region1.getType() == RegionType.ROOM) {
                if (y < region1.getBox().getMinY() + 2 || y > region1.getBox().getMaxY() - 2) {
                    return false;
                }
            }
            if (region2.getType() == RegionType.ROOM) {
                if (y < region2.getBox().getMinY() + 2 || y > region2.getBox().getMaxY() - 2) {
                    return false;
                }
            }

            Connector2D connector = new Connector2D(x, y, region1, region2);
            connectors.add(connector);
            level.getGrid().get(connector.getCoords()).setType(CellType.CONNECTOR);
        }
        return true;
    }

    /**
     * also need to add the selected connectors/doors to the Room list of doors
     * @param level
     */
    public void mergeRegions(ILevel2D level, Random random) {

        // build a map of rooms
        Map<Integer, IRoom2D> roomMap = new HashMap<>();
        level.getRooms().forEach(r -> {
            roomMap.put(Integer.valueOf(r.getId()), r);
        });

        List<Connector2D> localConnectors = new ArrayList<>();

        while (!getConnectors().isEmpty()) {
            localConnectors.clear();
            // randomly select a connector
            Connector2D connector = getConnectors().get(random.nextInt(getConnectors().size()));

            Region2D region1 = (Region2D) regionMap.get(connector.getRegion1().getId());
            Region2D region2 = (Region2D) regionMap.get(connector.getRegion2().getId());

            if (cullRegionsConnectors(level, roomMap, region1, region2)) {
                continue;
            }

            // TODO maybe this should only hold true for the End room, else you could get a disconnected dungeon.
            // TODO may have to implement a path checker to ensure a path exists from start to end
            // and remove any rooms/halls that aren't connected. (only have ensure that a region is visited,
            // not every possible path to that region)

            /*
             * create a door from selected connector
             */
            addDoor(level, connector, roomMap);

            // mark regions as merged (not sure if this serves a purpose yet)
            connector.getRegion1().setMerged(true);
            connector.getRegion2().setMerged(true);

            // remove the door connector first so that it remains a door and not set to wall.
            getConnectors().remove(connector);
            // to prevent room connections to exceed degrees, cull all connectors
            // from each region if # of doors > degrees.
            if (cullRegionsConnectors(level, roomMap, region1, region2)) {
                continue;
            }

            /*
             * gather all connectors that match the regions
             */
            getConnectors().forEach(c -> {
                if ((Objects.equals(c.getRegion1().getId(), connector.getRegion1().getId())
                        && Objects.equals(c.getRegion2().getId(), connector.getRegion2().getId()))
                        || (Objects.equals(c.getRegion1().getId(), connector.getRegion2().getId())
                        && Objects.equals(c.getRegion2().getId(), connector.getRegion1().getId()))) {
                    localConnectors.add(c);
                }
            });

            /*
             * cull extra connectors
             */
            // remove connectors immediately adjacent to connector is any direction
            List<Connector2D> mainDoorAdjacents = selectAdjacentConnectors(connector, localConnectors);
            localConnectors.removeAll(mainDoorAdjacents);
            // remove initial connector from level and working list
            getConnectors().removeAll(mainDoorAdjacents);
            mainDoorAdjacents.remove(connector);
            mainDoorAdjacents.forEach(adjacent -> {
                level.getGrid().get(adjacent.getCoords()).setType(CellType.WALL);
                getConnectors().remove(adjacent); // this is redundant to getConnectors().removeAll(mainDoorAdjacents)
            });

            // TODO (B) test here if either room in connector has met its degrees limit and continue if so.

            // for each of the remaining connectors in the working list
            List<Connector2D> ignoreList = new ArrayList<>();
            int connectorCount = 1;
            for(Connector2D c : localConnectors) {
                IRoom2D room1 = roomMap.get(c.getRegion1().getId());
                IRoom2D room2 = roomMap.get(c.getRegion2().getId());
                int degrees = (room1 != null && room2 != null) ? Math.min(room1.getDegrees(), room2.getDegrees())
                        : room1 == null ? room2 == null ? maxDegrees : room2.getDegrees() : room1.getDegrees();

                if (random.nextDouble() > 0.965 && !ignoreList.contains(c) && connectorCount < degrees) {
                    // build extra door
                    addDoor(level, c, roomMap);
                    // replace the adjacents with walls
                    List<Connector2D> adjacents = selectAdjacentConnectors(c, localConnectors);
                    adjacents.remove(c);
                    adjacents.forEach(c2 -> {
                        level.getGrid().get(c2.getCoords()).setType(CellType.WALL);
                        getConnectors().remove(c2);
                    });
                    // add all the connectors to a temporary remove list so as to not process again
                    ignoreList.addAll(adjacents);
                    connectorCount++;
                } else {
                    // replace with wall
                    level.getGrid().get(c.getCoords()).setType(CellType.WALL);
                }
                // remove connector from level
                getConnectors().remove(c);
            }
        }
    }

    private boolean cullRegionsConnectors(ILevel2D level, Map<Integer, IRoom2D> roomMap, Region2D region1, Region2D region2) {
        boolean shouldMoveToNextConnector = false;
        for (Region2D region : Arrays.asList(region1, region2)) {
//            Dungeons.LOGGER.debug("region -> {} of type -> {}", region.getId(), region.getType());
            if (region.getType() == RegionType.ROOM) {
                IRoom2D room = roomMap.get(region.getId());
//                Dungeons.LOGGER.debug("room -> {} has degrees -> {} and doors -> {}", room.getId(), room.getDegrees(), room.getDoorways().size());

                if (room.getDoorways().size() >= room.getDegrees()) {
//                    Dungeons.LOGGER.debug("room -> {} has met its degrees. moving to next connector.", room.getId());
                    cullRegionConnectors(level, region.getId());
                    shouldMoveToNextConnector = true;
                }
            }
        }
        return shouldMoveToNextConnector;
    }

    private void cullRegionConnectors(ILevel2D level, Integer id) {
        List<Connector2D> localConnectors = new ArrayList<>();

        // cull all connectors for this region
        getConnectors().forEach(c -> {
            if (Objects.equals(c.getRegion1().getId(), id)
                    || Objects.equals(c.getRegion2().getId(), id)) {
                localConnectors.add(c);
                // replace with wall
                level.getGrid().get(c.getCoords()).setType(CellType.WALL);
            }
        });
        getConnectors().removeAll(localConnectors);
    }

    /**
     *
     * @param connector
     * @param connectors
     * @return
     */
    public List<Connector2D> selectAdjacentConnectors(Connector2D connector, List<Connector2D> connectors) {
        List<Connector2D> removeList = new ArrayList<>(connectors.stream()
                .filter(c -> (c.getCoords().getX() == connector.getCoords().getX() && c.getCoords().getY() == connector.getCoords().getY() + 1)
                || (c.getCoords().getX() == connector.getCoords().getX() && c.getCoords().getY() == connector.getCoords().getY() - 1)
                || (c.getCoords().getX() == connector.getCoords().getX() + 1 && c.getCoords().getY() == connector.getCoords().getY())
                || (c.getCoords().getX() == connector.getCoords().getX() - 1 && c.getCoords().getY() == connector.getCoords().getY()))
                .toList());
        removeList.add(connector);
        return removeList;
    }

    /**
     *
     * @param level
     */
    public void backFill(ILevel2D level) {
        // scan all cells for solid rock
        // NOTE skip border cells as they are "walls"
        for (int x = 1; x < level.getWidth()-1; x+=2) {
            for (int y = 1; y < level.getHeight()-1; y+=2) {
                // process at coords
                backFill(level, new Coords2D(x, y));
            }
        }
    }

    /**
     *
     * @param level
     * @param startingCoords
     */
    private void backFill(ILevel2D level, Coords2D startingCoords) {
        Coords2D active = startingCoords;
        List<CellType> elements = Arrays.asList(CellType.ROCK, CellType.WALL);

        while (active != null) {
            int wallCount = 0;
            Coords2D next = null;

            // test for 3 wall
            if (elements.contains(level.getGrid().get(active.getX(), active.getY()-1).getType())) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX(), active.getY()-1);
            }
            if (elements.contains(level.getGrid().get(active.getX(), active.getY()+1).getType())) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX(), active.getY()+1);
            }
            if (elements.contains(level.getGrid().get(active.getX()+1, active.getY()).getType())) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX()+1, active.getY());
            }
            if (elements.contains(level.getGrid().get(active.getX()-1, active.getY()).getType())) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX()-1, active.getY());
            }

            if (wallCount >= 3) {
                // update level with WALL
                level.getGrid().get(active).setType(CellType.ROCK);

                // move to the tile in the open direction
                active = next;
            }
            else {
                active = null;
            }
        }
    }

    /**
     *
     * @param level
     * @param connector
     * @param roomMap
     */
    private void addDoor(ILevel2D level, Connector2D connector, Map<Integer, IRoom2D> roomMap) {
        level.getGrid().get(connector.getCoords()).setType(CellType.DOOR);
        // update the rooms
        IRoom2D room = roomMap.get(connector.getRegion1().getId());
        if (room != null) {
            room.getDoorways().add(connector.getCoords());
        }
        room = roomMap.get(connector.getRegion2().getId());
        if (room != null) {
            room.getDoorways().add(connector.getCoords());
        }
    }

    /**
     *
     * @param level
     * @param startCoords
     */
    private void prims(ILevel2D level, Coords2D startCoords) {
        List<PrimsTile2D> activeList = new ArrayList<>();
        Map<Direction2D, PrimsTile2D> neighbors = new HashMap<>();
        Map<Direction2D, PrimsTile2D> passages = new HashMap<>();
        int maxRun = random.nextInt(maxCorridorSize - minCorridorSize) + minCorridorSize;
        // create tile
        PrimsTile2D tile = new PrimsTile2D(startCoords, Direction2D.SOUTH);

        // add tile to activeList
        activeList.add(tile);

        // create a region
        Region2D region = new Region2D(idGenerator.next());
        region.setType(RegionType.CORRIDOR);

        // add tile to region
//        region.addTile(tile);

        // add region to the level
        getRegionMap().put(region.getId(), region);

        // update the grid with the active region
        level.getGrid().get(tile.getCoords()).setType(CellType.CORRIDOR);
        level.getGrid().get(tile.getCoords()).setRegionId(region.getId());

        int runCount = 0;
        while(!activeList.isEmpty()) {
            // randomly select a cell from the active list
            PrimsTile2D active = null;
            if (random.nextDouble() < runFactor) {
                active = activeList.get(activeList.size()-1);
            } else {
                active = activeList.get(random.nextInt(activeList.size()));
            }

            // init temporary variables
            neighbors.clear();
            passages.clear();

            // TODO how was -1, -1 array index prevented before ???
            // get neighbors of active
            if (level.getGrid().get(active.getX(), active.getY()-1).getType() == CellType.ROCK) {
                if (level.getGrid().get(active.getX(), active.getY()-2).getType() == CellType.ROCK) {
                    neighbors.put(Direction2D.NORTH, new PrimsTile2D(active.getX(), active.getY()-2, Direction2D.NORTH));
                    passages.put(Direction2D.NORTH, new PrimsTile2D(active.getX(), active.getY()-1, Direction2D.NORTH));
                } else {
                    // ineligible space for neighbor, make the in-between as wall
                    level.getGrid().get(active.getX(), active.getY()-1).setType(CellType.WALL);
                }

            }
            if (level.getGrid().get(active.getX(), active.getY()+1).getType() == CellType.ROCK) {
                if (level.getGrid().get(active.getX(), active.getY()+2).getType() == CellType.ROCK) {
                    neighbors.put(Direction2D.SOUTH, new PrimsTile2D(active.getX(), active.getY() + 2, Direction2D.SOUTH));
                    passages.put(Direction2D.SOUTH, new PrimsTile2D(active.getX(), active.getY() + 1, Direction2D.SOUTH));
                } else {
                    level.getGrid().get(active.getX(), active.getY()+1).setType(CellType.WALL);
                }
            }
            if (level.getGrid().get(active.getX()+1, active.getY()).getType() == CellType.ROCK) {
                if (level.getGrid().get(active.getX()+2, active.getY()).getType() == CellType.ROCK) {
                    neighbors.put(Direction2D.EAST, new PrimsTile2D(active.getX()+2, active.getY() , Direction2D.EAST));
                    passages.put(Direction2D.EAST, new PrimsTile2D(active.getX()+1, active.getY(), Direction2D.EAST));
                } else {
                    level.getGrid().get(active.getX()+1, active.getY()).setType(CellType.WALL);
                }
            }
            if (level.getGrid().get(active.getX()-1, active.getY()).getType() == CellType.ROCK) {
                if (level.getGrid().get(active.getX()-2, active.getY()).getType() == CellType.ROCK) {
                    neighbors.put(Direction2D.WEST, new PrimsTile2D(active.getX() - 2, active.getY(), Direction2D.WEST));
                    passages.put(Direction2D.WEST, new PrimsTile2D(active.getX() - 1, active.getY(), Direction2D.WEST));
                } else {
                    level.getGrid().get(active.getX()-1, active.getY()).setType(CellType.WALL);
                }
            }

            if (neighbors.isEmpty()) {
                activeList.remove(active);
                continue;
            }

            PrimsTile2D selected = null;
            PrimsTile2D passage = null;
            // if available pick the same direction 80% of time
            if (random.nextDouble() < curveFactor && neighbors.containsKey(active.getDirection())) {
                // move in the same direction as last time
                selected = neighbors.get(active.getDirection());
                selected.setDirection(active.getDirection());
                passage = passages.get(active.getDirection());
            }
            else {
                // randomly select a direction
                List<Direction2D> directions = neighbors.keySet().stream().toList();
                Direction2D direction = directions.get(random.nextInt(directions.size()));
                if (direction != null) {
                    selected = neighbors.get(direction);
                    selected.setDirection(direction);
                    passage = passages.get(direction);
                }
            }

            if (selected == null) {
                activeList.remove(active);
                continue;
            }

            activeList.add(selected);

            // add neighbor and passage to region
//            region.addTile(selected);
//            region.addTile(passage);

            // update grid with id of region
            level.getGrid().get(selected.getCoords()).setRegionId(region.getId());
            level.getGrid().get(selected.getCoords()).setType(CellType.CORRIDOR);

            level.getGrid().get(passage.getCoords()).setRegionId(region.getId());
            level.getGrid().get(passage.getCoords()).setType(CellType.CORRIDOR);

            // update sides of passage perpendicular to direction needs to turn into wall
//            switch(passage.getDirection()) {
//                case NORTH, SOUTH -> {
//                    level.getGrid().get(passage.getX()-1, passage.getY()).setType(CellType.WALL);
//                    level.getGrid().get(passage.getX()+1, passage.getY()).setType(CellType.WALL);
//                }
//                case EAST, WEST -> {
//                    level.getGrid().get(passage.getX(), passage.getY()-1).setType(CellType.WALL);
//                    level.getGrid().get(passage.getX(), passage.getY()+1).setType(CellType.WALL);
//                }
//            }
            Cell passageCell = level.getGrid().get(passage.getCoords());
            switch(passage.getDirection()) {
                case NORTH, SOUTH -> {
                    // check the far side of walls for same region
                    if (passage.getX()-2 > 0 && level.getGrid().get(passage.getX() - 2, passage.getY()).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX() - 2, passage.getY()).getRegionId() == passageCell.getRegionId()) {
                        level.getGrid().get(passage.getX() - 1, passage.getY()).setRegionId(passageCell.getRegionId());
                        level.getGrid().get(passage.getX() - 1, passage.getY()).setType(CellType.CORRIDOR);
                    } else {
                        level.getGrid().get(passage.getX() - 1, passage.getY()).setType(CellType.WALL);
                    }

                    if (passage.getX()+2 < getWidth() && level.getGrid().get(passage.getX() + 2, passage.getY()).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX() + 2, passage.getY()).getRegionId() == passageCell.getRegionId()) {
                        level.getGrid().get(passage.getX() + 1, passage.getY()).setRegionId(passageCell.getRegionId());
                        level.getGrid().get(passage.getX() + 1, passage.getY()).setType(CellType.CORRIDOR);
                    } else {
                        level.getGrid().get(passage.getX() + 1, passage.getY()).setType(CellType.WALL);
                    }
                }
                case EAST, WEST -> {
                    if(passage.getY()-2 > 0 && level.getGrid().get(passage.getX(), passage.getY()-2).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX(), passage.getY()-2).getRegionId() == passageCell.getRegionId()) {
                        level.getGrid().get(passage.getX(), passage.getY() - 1).setRegionId(passageCell.getRegionId());
                        level.getGrid().get(passage.getX(), passage.getY() - 1).setType(CellType.CORRIDOR);
                    } else {
                        level.getGrid().get(passage.getX(), passage.getY() - 1).setType(CellType.WALL);
                    }

                    if (passage.getY()+2 < getHeight() && level.getGrid().get(passage.getX(), passage.getY()+2).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX(), passage.getY()+2).getRegionId() == passageCell.getRegionId()) {
                        level.getGrid().get(passage.getX(), passage.getY() + 1).setRegionId(passageCell.getRegionId());
                        level.getGrid().get(passage.getX(), passage.getY() + 1).setType(CellType.CORRIDOR);
                    } else {
                        level.getGrid().get(passage.getX(), passage.getY() + 1).setType(CellType.WALL);
                    }
                }
            }

            runCount++;
            if (runCount > maxRun) {
                activeList.clear();
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
        setLevelBoundary(new Rectangle2D(0, 0, width, height));
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
        setLevelBoundary(new Rectangle2D(0, 0, width, height));
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public IRoom2D getStartRoom() {
        return startRoom;
    }

    public void setStartRoom(IRoom2D startRoom) {
        this.startRoom = startRoom;
    }

    public IRoom2D getEndRoom() {
        return endRoom;
    }

    public void setEndRoom(IRoom2D endRoom) {
        this.endRoom = endRoom;
    }

    public List<IRoom2D> getSuppliedRooms() {
        return suppliedRooms;
    }

    public void setSuppliedRooms(List<IRoom2D> suppliedRooms) {
        this.suppliedRooms = suppliedRooms;
    }

    public int getNumberOfRooms() {
        return numberOfRooms;
    }

    public void setNumberOfRooms(int numberOfRooms) {
        this.numberOfRooms = numberOfRooms;
    }

    public double getCurveFactor() {
        return curveFactor;
    }

    public void setCurveFactor(double curveFactor) {
        this.curveFactor = curveFactor;
    }

    public double getRunFactor() {
        return runFactor;
    }

    public int getFillAttempts() {
        return fillAttempts;
    }

    public void setFillAttempts(int fillAttempts) {
        this.fillAttempts = fillAttempts;
    }

    public int getFillRoomsPerSize() {
        return fillRoomsPerSize;
    }

    public void setFillRoomsPerSize(int fillRoomsPerSize) {
        this.fillRoomsPerSize = fillRoomsPerSize;
    }

    public void setRunFactor(double runFactor) {
        this.runFactor = runFactor;
    }

    public Rectangle2D getLevelBoundary() {
        return levelBoundary;
    }

    private void setLevelBoundary(Rectangle2D levelBoundary) {
        this.levelBoundary = levelBoundary;
    }

    public int getMinCorridorSize() {
        return minCorridorSize;
    }

    public void setMinCorridorSize(int minCorridorSize) {
        this.minCorridorSize = minCorridorSize;
    }

    public int getMaxCorridorSize() {
        return maxCorridorSize;
    }

    public void setMaxCorridorSize(int maxCorridorSize) {
        this.maxCorridorSize = maxCorridorSize;
    }

    public List<Connector2D> getConnectors() {
        return connectors;
    }

    private void setConnectors(List<Connector2D> connectors) {
        this.connectors = connectors;
    }

    public Map<Integer, Region2D> getRegionMap() {
        return regionMap;
    }

    private void setRegionMap(Map<Integer, Region2D> regionMap) {
        this.regionMap = regionMap;
    }

    //////////////////////////////////////////////////////////////////
    public static class Builder {
        public int width = DEFAULT_WIDTH;
        public int height = DEFAULT_HEIGHT;
        public int minSize = DEFAULT_MIN_SIZE;
        public int maxSize = DEFAULT_MAX_SIZE;
        public int minDegrees = DEFAULT_MIN_DEGREES;
        public int maxDegrees = DEFAULT_MAX_DEGREES;
        public int numberOfRooms = DEFAULT_NUMBER_OF_ROOMS;
        public int minCorridorSize = DEFAULT_MIN_CORRIDOR_SIZE;
        public int maxCorridorSize = DEFAULT_MAX_CORRIDOR_SIZE;
        public int attemptsMax = DEFAULT_MAX_ATTEMPTS;
        public double meanFactor = DEFAULT_MEAN_FACTOR;
        public double runFactor = DEFAULT_RUN_FACTOR;
        public double curveFactor = DEFAULT_CURVE_FACTOR;
        public int fillAttempts = DEFAULT_FILL_ATTEMPTS;
        public int fillRoomsPerSize = DEFAULT_FILL_ROOMS_PER_SIZE;

        public IRoom2D startRoom;
        public IRoom2D endRoom;
        public List<IRoom2D> suppliedRooms = new ArrayList<>();
        public Random random;

        public Builder with(Consumer<Builder> builder) {
            builder.accept(this);
            return this;
        }

        public MazeLevelGenerator2D build() {
            return new MazeLevelGenerator2D(this);
        }
    }
}
