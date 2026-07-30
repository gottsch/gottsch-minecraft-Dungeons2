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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 *
 * @author Mark Gottschling on Nov 8, 2023
 *
 */
public class MazeLevelGenerator2D {

    private static final Logger LOGGER = LoggerFactory.getLogger(MazeLevelGenerator2D.class);

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

    /**
     * Number of dilation passes applied to corridors after Prim's carve.
     * 0 = classic 1-wide corridors. 1 = 2-wide. 2 = 3-wide. Etc.
     * See {@link #dilateCorridors(ILevel2D, int)}.
     */
    private int corridorDilationPasses = 0;

    /**
     * Room id -&gt; the set of corner quadrants (0=NW,1=NE,2=SW,3=SE) already
     * claimed by a door candidate near that corner, for the CURRENT
     * {@link #discoverConnectors} call (cleared at its start). A room's own
     * per-wall corner exclusion (see {@link #generateConnector}) only keeps a
     * door away from the corners of the ONE wall it's being scanned against; it
     * has no way to see a candidate independently approaching the SAME corner
     * from the perpendicular wall. Two dilated/wide corridors meeting a room at
     * adjacent walls can each pass that per-wall check individually and still
     * both open as doors crammed into one corner. This tracks corner claims
     * across the whole scan so the second candidate near an already-claimed
     * corner is rejected instead.
     */
    private final Map<Integer, Set<Integer>> claimedRoomCorners = new HashMap<>();

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
        this.corridorDilationPasses = builder.corridorDilationPasses;

        this.startRoom = builder.startRoom;
        this.endRoom = builder.endRoom;
        this.suppliedRooms = builder.suppliedRooms;

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

        // If addRooms returned no rooms (start/end could not be placed), bail cleanly
        // rather than NPE-ing in discoverConnectors below on a level with no rooms set.
        if (rooms.isEmpty()) {
            LOGGER.warn("MazeLevelGenerator2D: addRooms produced no rooms; aborting generate()");
            return Optional.empty();
        }

        // NOTE This is moot as rooms are filtered as they are added
        // filter some rooms out. if something like this is re-implemented, add it to generateRooms
//        rooms = removeRoomsBelowAreaMean(rooms, meanFactor);

        // map rooms to regions
        Map<Integer, Region2D> regions = mapRegions(rooms);
        setRegionMap(regions);

        // carve passages
        carve(level);

        // optional corridor widening: must run BEFORE discoverConnectors so doors
        // land on the widened corridor walls, not the original 1-wide walls.
        if (corridorDilationPasses > 0) {
            dilateCorridors(level, corridorDilationPasses);
        }

        // add connectors
        boolean isDiscoverSuccess = discoverConnectors(level);
        if (!isDiscoverSuccess) {
            return Optional.empty();
        }

        // Snapshot every discovered connector BEFORE mergeRegions consumes the
        // working list. ensureConnectivity reuses these as fallback doors.
        List<Connector2D> allConnectors = new ArrayList<>(connectors);

        // merge regions
        mergeRegions(level, random);

        // Guarantee the dungeon is fully connected (start can reach end). The
        // random merge above does not track connected components, so rooms --
        // especially the low-degree start/end anchors -- can be orphaned. Run
        // this BEFORE backFill so any door we add anchors its corridor against
        // dead-end removal.
        ensureConnectivity(level, allConnectors);

        // back-fill dead-ends
        backFill(level);

        // Restore the START anchor walls. The maze protects a room's ROOM
        // interior but not its 1-cell WALL ring, so fill rooms and corridor
        // dilation can breach it. For procedural rooms that is invisible, but the
        // START anchor is an authored entrance template -- a breach shows up as a
        // doorless hole cut into it. Revert any non-DOOR perimeter cell that got
        // turned into corridor/connector back to WALL; real DOORs are preserved.
        // (The END anchor is still a procedural placeholder and has a single
        // fragile entrance, so it is left untouched until it becomes a template.)
        restoreAnchorWalls(level.getStartRoom(), level);

        return Optional.of(level);
    }

    /**
     * Reverts an anchor room's perimeter cells that were breached into
     * corridor/connector back to WALL, leaving authored DOOR openings intact.
     */
    private void restoreAnchorWalls(IRoom2D room, ILevel2D level) {
        if (room == null) {
            return;
        }
        int x0 = room.getOrigin().getX();
        int z0 = room.getOrigin().getY();
        int x1 = x0 + room.getWidth() - 1;
        int z1 = z0 + room.getHeight() - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) {
                    continue; // interior, not perimeter
                }
                if (x < 0 || z < 0 || x >= level.getGrid().getWidth() || z >= level.getGrid().getHeight()) {
                    continue;
                }
                Cell cell = level.getGrid().get(x, z);
                if (cell != null
                        && (cell.getType() == CellType.CORRIDOR || cell.getType() == CellType.CONNECTOR)) {
                    cell.setType(CellType.WALL);
                }
            }
        }
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
        LOGGER.debug("roomCount -> {}, numberfOfRooms -> {}", rooms.size(), numberOfRooms);

        // TODO level is never updated with random rooms?!

        // update level
        level.setRooms(rooms);
        level.getGrid().add(rooms);

        int deltaRooms = numberOfRooms - rooms.size();
        LOGGER.debug("deltaRooms -> {}", deltaRooms);
        if (deltaRooms > 0) {
            List<IRoom2D> fillRooms = generateFillRooms(deltaRooms, xRange, yRange, minSize, maxSize, levelBoundary, localMeanFactor, rooms, random);
            LOGGER.debug("# fill rooms -> {}", fillRooms.size());
            fillRooms = placeFillRooms(level, fillRooms);

            // Keep fill rooms clear of the START anchor (an authored entrance
            // template needs a little clearance). Fill rooms sharing a wall with
            // each other or with other rooms is fine and desirable -- it packs the
            // dungeon densely; only a 1-cell buffer around the entrance is removed.
            fillRooms.removeIf(fr -> withinMargin(fr, level.getStartRoom(), 1));

            // add the fill rooms to the rooms list and grid
            level.getRooms().addAll(fillRooms);
            level.getGrid().add(fillRooms);
        }

        return rooms;
    }

    /**
     * True when {@code fill}'s footprint comes within {@code margin} cells of
     * {@code anchor} (i.e. overlaps the anchor's box inflated by the margin).
     */
    private boolean withinMargin(IRoom2D fill, IRoom2D anchor, int margin) {
        if (anchor == null) {
            return false;
        }
        Rectangle2D inflated = new Rectangle2D(
                anchor.getOrigin().getX() - margin,
                anchor.getOrigin().getY() - margin,
                anchor.getWidth() + 2 * margin,
                anchor.getHeight() + 2 * margin);
        return inflated.intersects(fill.getBox());
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
            // Supplied start room: validate against boundary and assign an id so
            // it participates in the region/connector system. Skipping validation
            // here lets out-of-bounds rooms slip into grid.add() and crash.
            room = this.startRoom;
            if (!isRoomValid(room, levelBoundary)) {
                LOGGER.warn("supplied start room is invalid for level boundary {} -> {}",
                        levelBoundary, room.getBox());
                return Optional.empty();
            }
            if (room.getId() == 0) {
                room.setId(idGenerator.next());
            }
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
            // Supplied end room: same validation + id-assignment rationale as start.
            room = this.endRoom;
            if (!isRoomValid(room, levelBoundary)) {
                LOGGER.warn("supplied end room is invalid for level boundary {} -> {}",
                        levelBoundary, room.getBox());
                return Optional.empty();
            }
            if (hasIntersections(room.getBox(), rooms)) {
                LOGGER.warn("supplied end room intersects existing rooms: {}", room.getBox());
                return Optional.empty();
            }
            if (room.getId() == 0) {
                room.setId(idGenerator.next());
            }
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
                LOGGER.debug("attemptCount -> {}", attemptCount);
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
            LOGGER.error("unable to clone grid - unable to add fill rooms:", ignore);
            return newRooms;
        }

        for (int fillAttemptIndex = 0; fillAttemptIndex < this.fillAttempts; fillAttemptIndex++) {
//            LOGGER.debug("attempt # -> {}", fillAttemptIndex);
            List<IRoom2D> rooms = new ArrayList<>();
            // scan the void grid looking for empty space candidates
            List<Rectangle2D> maximalRectangleList = getMaximalRectangles(voidGrid);
//            LOGGER.debug("size of rectangles -> {}", maximalRectangleList.size());
            // randomize the sort of the list (use the seeded random for determinism)
            Collections.shuffle(maximalRectangleList, random);
//            LOGGER.debug("size of supplied rooms -> {}", suppliedRooms.size());
            // for each of the supplied rooms
            for (IRoom2D suppliedRoom : suppliedRooms) {
                // a list to manage the rectangles to remove
                List<Rectangle2D> rectangleRemoveList = new ArrayList<>();
                // get the size of the room
                Coords2D size = new Coords2D(suppliedRoom.getWidth(), suppliedRoom.getHeight());
//                LOGGER.debug("testing room -> {}, size -> {}", suppliedRoom.getId(), size);
//                LOGGER.debug("size of rectangles2 -> {}", maximalRectangleList.size());
                // scan all the rectangles
                for (Rectangle2D r : maximalRectangleList) {
//                    LOGGER.debug("testing against rectangle -> {}x{}", r.getWidth(), r.getHeight());
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

                        LOGGER.debug("adding fill room -> {}", suppliedRoom);

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

        LOGGER.debug("size of added rooms -> {}", newRooms.size());
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
     * Finds the large empty rectangles in the grid &mdash; the open pockets where
     * {@link #placeFillRooms} can drop extra rooms.
     *
     * <p>Implements the "list of rectangles" maximal-rectangle algorithm
     * (Based on https://www.researchgate.net/publication/221249132_Object_Descriptors_Based_on_a_List_of_Rectangles_Method_and_Algorithm).
     * The idea, in two phases:</p>
     * <ol>
     *     <li><strong>Span tables.</strong> For every empty cell, precompute how
     *         far the empty run extends upward ({@code dN}, "distance north") and
     *         downward ({@code dS}, "distance south"). A non-empty cell stores
     *         {@code -1}. This makes the vertical extent of any column an O(1)
     *         lookup instead of a re-scan.</li>
     *     <li><strong>Sweep.</strong> Scan columns right-to-left. At each empty
     *         cell that starts a new horizontal run (its left neighbor is solid),
     *         grow a candidate rectangle rightward, shrinking its vertical extent
     *         (the running {@code N}/{@code S}) to the most restrictive column seen
     *         so far. Whenever the extent would shrink, the current span is a
     *         maximal rectangle &mdash; emit it if it clears the minimum size.</li>
     * </ol>
     *
     * <p>Here "empty" means rock or wall (see {@link #isEmptyOrBorder}); the wall
     * border counts as empty so edge pockets are detected too.</p>
     */
    private List<Rectangle2D> getMaximalRectangles(Grid2D voidGrid) {
        List<Rectangle2D> rectangles = new ArrayList<>();

        // dN[col][row] = number of contiguous empty cells directly ABOVE (and
        // including) this cell; -1 if this cell is occupied.
        int[][] dN = new int[voidGrid.getSize().getX()][voidGrid.getSize().getY()];

        // NOTE all levels have a wall border - this counts as empty space
        for (int col = 0; col < voidGrid.getSize().getX(); col++) {
            dN[col][0] = 0;
        }

        // Fill dN top-down: each empty cell extends the run from the cell above.
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

        // dS[col][row] = the same, but counting DOWNWARD. Filled bottom-up.
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

        // Sweep columns right-to-left.
        for (int col = voidGrid.getWidth() -1; col >=0; col--) {
            // maxS tracks the tallest rectangle already emitted ending at/below
            // this row, so we don't re-emit a rectangle that's contained in a
            // taller one. It's reset whenever we start a fresh run.
            int maxS = voidGrid.getHeight();
            for (int row = voidGrid.getHeight() -1; row >= 0; row--) {
                maxS++;
                // Only start a rectangle at the LEFT edge of a run (cell is empty
                // and the cell to its left is solid or out of bounds) -- this is
                // what makes each maximal rectangle get found exactly once.
                if (isEmptyOrBorder(voidGrid, col, row) && (col == 0 || !isEmptyOrBorder(voidGrid, col -1, row))) {
                    // N/S = how far the candidate can extend up/down. They only
                    // ever shrink as we widen rightward to the most limiting column.
                    int N = dN[col][row];
                    int S = dS[col][row];
                    int width = 1;
                    // Grow rightward while the next column is still empty.
                    while(col + width < voidGrid.getWidth() && isEmptyOrBorder(voidGrid, col + width, row)) {
                        int nextN = dN[col + width][row];
                        int nextS = dS[col + width][row];
                        // The next column is shorter in some direction: the current
                        // width is maximal for the current height, so record it
                        // (if it's tall enough to be new and meets the min size).
                        if ((nextN < N) || (nextS < S)) {
                            if (S < maxS) {
                                if (width >= MIN_RECTANGLE_WIDTH && (N + S + 1) >= MIN_RECTANGLE_HEIGHT) {
                                    rectangles.add(new Rectangle2D(col, row - N, width, N + S + 1));
                                }
                            }
                            // Clamp the running extent to the new limit before
                            // continuing to widen.
                            if (nextN < N) N = nextN;
                            if (nextS < S) S = nextS;
                        }
                        width++;
                    }
                    // Emit the final (widest) rectangle for this starting cell.
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
        claimedRoomCorners.clear();

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
            LOGGER.warn("failed to merge custom room to the level.");
            return false;
        }

        // Candidate doorways: a room may mark a SET of "possible" door cells (e.g.
        // from a template's jigsaw markers). Restrict that room's connectors to
        // those cells and skip the room in the perimeter scan below, so doors only
        // ever appear at marked cells. Generated permissively -- a candidate with
        // no differing region across it simply yields no connector (it's a
        // *possible* door, not a forced one) -- and NOT pre-counted as an opened
        // door, so mergeRegions still opens at most `degrees` of them via its
        // normal culling. Skipped for rooms that already supplied explicit
        // doorways above (those take precedence).
        for (IRoom2D room : level.getRooms()) {
            if (room.getDoorways().isEmpty() && !room.getCandidateDoorways().isEmpty()) {
                for (Coords2D candidate : room.getCandidateDoorways()) {
                    generateConnector(level, connectors, candidate.getX(), candidate.getY());
                }
                ignoreIds.add(room.getId());
            }
        }

        // scan all cells for
        // 1. wall
        // 2. adjacent to two regions of different ids.
        // NOTE skip border cells as they will not have connectors
        for (int x = 1; x < level.getWidth()-1; x++) {
            for (int y = 1; y < level.getHeight()-1; y++) {
                // find any wall
                if (level.getGrid().get(x, y).getType() == CellType.WALL) {
                    // requireFrame=true: only the generic wall scan is exposed to
                    // wide/dilated corridors, where a wall cell can bridge two
                    // regions yet not be flanked by solid wall on its own row/
                    // column (see hasSolidDoorFrame). Explicit/candidate doorways
                    // (authored template positions) are exempt -- see below.
                    generateConnector(level, connectors, x, y, ignoreIds, true);
                }
            }
        }

        return true;
    }

    private boolean generateConnector(ILevel2D level, List<Connector2D> connectors, int x, int y) {
        return generateConnector(level, connectors, x, y, null, false);
    }

    /**
     *
     * @param level
     * @param x
     * @param y
     */
    private boolean generateConnector(ILevel2D level, List<Connector2D> connectors, int x, int y, List<Integer> ignoreIds) {
        return generateConnector(level, connectors, x, y, ignoreIds, false);
    }

    /**
     * @param requireFrame if true, a candidate wall cell that bridges two
     *                      regions is only accepted when its frame axis (the
     *                      row/column the door itself would sit in, perpendicular
     *                      to the regions it bridges) is solid on both sides —
     *                      see {@link #hasSolidDoorFrame}. Without this, a door
     *                      placed on a wide/dilated corridor's divider can end up
     *                      with open corridor space beside it instead of a wall,
     *                      reading as a "floating" door you can just walk around.
     */
    private boolean generateConnector(ILevel2D level, List<Connector2D> connectors, int x, int y,
                                      List<Integer> ignoreIds, boolean requireFrame) {
        // The generic wall-scan caller pre-filters to WALL cells before calling
        // this, but the explicit/candidate-doorway callers (room.getDoorways() /
        // room.getCandidateDoorways(), e.g. the jigsaw-assembled entrance's
        // dungeons2:door markers -- what floor 0 uses on EVERY real dungeon) do
        // not: they hand this whatever grid position the marker happened to map
        // to, with no guarantee it's actually a wall between two regions. Without
        // this check, a candidate that lands on a CORRIDOR/ROOM cell (or bare
        // ROCK) gets blindly overwritten into a CONNECTOR/DOOR anyway, either
        // punching a hole through a real corridor/room or creating a door with
        // nothing legitimate on one or both sides.
        if (level.getGrid().get(x, y).getType() != CellType.WALL) {
            return true;
        }
        // Bounds-checked: the generic wall-scan caller only ever passes x/y from
        // [1, width-2]/[1, height-2] so its neighbors are always in-bounds, but the
        // candidateDoorways callers (room.getCandidateDoorways(), e.g. a jigsaw-
        // assembled room/transition/entrance marker) hand this whatever grid
        // position the marker mapped to -- including, in principle, a cell flush
        // against the grid's own boundary (x=0/z=0/width-1/height-1), whose
        // neighbor would be out of bounds. Treat an out-of-bounds neighbor the
        // same as an unassigned cell (regionId 0, always < idGenerator.getStart()),
        // matching isFrameOpen's existing OOB-is-not-open convention below.
        int northId = regionIdAt(level.getGrid(), x, y - 1);
        int southId = regionIdAt(level.getGrid(), x, y + 1);
        // test east and west
        int eastId = regionIdAt(level.getGrid(), x + 1, y);
        int westId = regionIdAt(level.getGrid(), x - 1, y);

        if (ignoreIds != null && (ignoreIds.contains((int)northId) || ignoreIds.contains((int)southId) ||
                ignoreIds.contains((int)eastId) || ignoreIds.contains((int)westId))) {
            return false;
        }

        if (northId >= idGenerator.getStart() && southId >= idGenerator.getStart() && northId != southId
                && isRenderedRegionCell(level.getGrid(), x, y - 1)
                && isRenderedRegionCell(level.getGrid(), x, y + 1)) {
            // get regions
            Region2D region1 = getRegionMap().get((int)northId);
            Region2D region2 = getRegionMap().get((int)southId);
            // A region id can reference a region no longer in the map (e.g. a
            // corridor cell adjacent to a candidate doorway whose region was not
            // registered). Without this guard the type check below NPEs.
            if (region1 == null || region2 == null) {
                return false;
            }

            // test that x,y is valid position on the x-axis (east-west) ie away from corners.
            // Only for the generic perimeter scan -- see awayFromRoomCorner.
            if (requireFrame && region1.getType() == RegionType.ROOM
                    && !awayFromRoomCorner(x, region1.getBox().getMinX(), region1.getBox().getMaxX())) {
                return false;
            }
            if (requireFrame && region2.getType() == RegionType.ROOM
                    && !awayFromRoomCorner(x, region2.getBox().getMinX(), region2.getBox().getMaxX())) {
                return false;
            }

            // frame axis is east-west (perpendicular to the north-south split)
            if (requireFrame && !hasSolidDoorFrame(level.getGrid(), x - 1, y, x + 1, y)) {
                return true; // no valid door here; leave the cell as WALL
            }
            if (requireFrame && !claimRoomCorners(region1, region2, x, y)) {
                return true; // a room corner here is already claimed by another candidate
            }

            Connector2D connector = new Connector2D(x, y, region1, region2);
            // add connector to list
            connectors.add(connector);
            // update grid with id = CONNECTOR
            level.getGrid().get(connector.getCoords()).setType(CellType.CONNECTOR);
        }
        else if (eastId >= idGenerator.getStart() && westId >= idGenerator.getStart() && eastId != westId
                && isRenderedRegionCell(level.getGrid(), x + 1, y)
                && isRenderedRegionCell(level.getGrid(), x - 1, y)) {
            Region2D region1 = getRegionMap().get((int)eastId);
            Region2D region2 = getRegionMap().get((int)westId);
            if (region1 == null || region2 == null) {
                return false;
            }

            if (requireFrame && region1.getType() == RegionType.ROOM
                    && !awayFromRoomCorner(y, region1.getBox().getMinY(), region1.getBox().getMaxY())) {
                return false;
            }
            if (requireFrame && region2.getType() == RegionType.ROOM
                    && !awayFromRoomCorner(y, region2.getBox().getMinY(), region2.getBox().getMaxY())) {
                return false;
            }

            // frame axis is north-south (perpendicular to the east-west split)
            if (requireFrame && !hasSolidDoorFrame(level.getGrid(), x, y - 1, x, y + 1)) {
                return true; // no valid door here; leave the cell as WALL
            }
            if (requireFrame && !claimRoomCorners(region1, region2, x, y)) {
                return true; // a room corner here is already claimed by another candidate
            }

            Connector2D connector = new Connector2D(x, y, region1, region2);
            connectors.add(connector);
            level.getGrid().get(connector.getCoords()).setType(CellType.CONNECTOR);
        }
        return true;
    }

    /**
     * The other open connectors belonging to the same <strong>authored doorway</strong>
     * as {@code connector} — i.e. cells a template marked with
     * {@code dungeons2:door} / {@code dungeons2:connector} that form one contiguous
     * run with this one. Empty for an ordinary maze-scanned connector, and empty for
     * an authored marker that stands alone, so this only ever widens what a template
     * explicitly asked for.
     *
     * <p>Authored cells arrive as {@link IRoom2D#getCandidateDoorways()} (every
     * caller in {@code DungeonStackPlanner} uses that setter), which is also why
     * {@code getDoorways()} can't be used to identify them — {@link #addDoor}
     * appends to it, so it stops being a record of what the author marked.</p>
     *
     * <p>Flood-filled over 4-adjacency rather than just taking immediate neighbours,
     * so a 3-cell-wide authored opening works whichever of its cells the maze
     * happens to pick first.</p>
     *
     * <p><strong>Degree accounting:</strong> each cell of the run is a separate entry
     * in {@code getDoorways()}, so a 2-wide door counts as 2 against the room's
     * {@code degrees}. That is deliberate — a wide opening is more connection — but
     * it does mean a room needs {@code degrees} headroom for the width, not just the
     * count of doorways.</p>
     */
    private List<Connector2D> authoredRunSiblings(Connector2D connector, Map<Integer, IRoom2D> roomMap) {
        Set<Coords2D> run = new LinkedHashSet<>();
        for (Region2D region : Arrays.asList(connector.getRegion1(), connector.getRegion2())) {
            IRoom2D room = roomMap.get(region.getId());
            if (room == null || room.getCandidateDoorways() == null) {
                continue;
            }
            Set<Coords2D> authored = new HashSet<>(room.getCandidateDoorways());
            if (!authored.contains(connector.getCoords())) {
                continue;
            }
            // Flood from this cell through the room's own authored cells only, so
            // two different rooms' runs can never be joined into one.
            Deque<Coords2D> pending = new ArrayDeque<>();
            pending.add(connector.getCoords());
            while (!pending.isEmpty()) {
                Coords2D cell = pending.poll();
                if (!run.add(cell)) {
                    continue;
                }
                for (Coords2D step : List.of(
                        new Coords2D(cell.getX() + 1, cell.getY()),
                        new Coords2D(cell.getX() - 1, cell.getY()),
                        new Coords2D(cell.getX(), cell.getY() + 1),
                        new Coords2D(cell.getX(), cell.getY() - 1))) {
                    if (authored.contains(step) && !run.contains(step)) {
                        pending.add(step);
                    }
                }
            }
        }
        if (run.size() <= 1) {
            return List.of();
        }
        return getConnectors().stream()
                .filter(c -> !c.getCoords().equals(connector.getCoords()))
                .filter(c -> run.contains(c.getCoords()))
                .toList();
    }

    /**
     * A door must sit at least 2 cells in from a room's corner along the wall it
     * is on, or the doorway has no wall beside it to frame against.
     *
     * <p><strong>Only applied to the generic perimeter scan.</strong> The
     * author-supplied paths ({@code room.getDoorways()} /
     * {@code getCandidateDoorways()}, i.e. {@code dungeons2:door} and
     * {@code dungeons2:connector} jigsaw markers) are exempt, alongside the two
     * checks that were already gated the same way ({@link #hasSolidDoorFrame} and
     * {@link #claimRoomCorners}).
     *
     * <p><strong>Why the exemption is necessary, not just permissive:</strong> a
     * marker's position is authored relative to <em>its own template piece</em>,
     * but the reserved region a multi-piece jigsaw chain produces is the
     * <em>union</em> bounding rect of every piece in that chain. Those differ, and
     * the author cannot know the union in advance — it depends on which pieces
     * the chain happened to assemble. The `stairs_2` transition is the worked
     * example (2026-07-29): its double doors sit mid-wall on the 4-wide bottom and
     * top pieces, but the 3-piece chain sprawls sideways to a 7-wide union, which
     * left exactly one cell of each pair 1 short of the corner margin. A double
     * door needs both cells, so both ends came out sealed.
     */
    private static boolean awayFromRoomCorner(int along, int min, int max) {
        return along >= min + 2 && along <= max - 2;
    }

    /**
     * True if (x,y) is CURRENTLY a CORRIDOR or ROOM cell — i.e. a real, actually-
     * rendered region, as opposed to a stale regionId left behind on a cell
     * whose type was later reverted to WALL/ROCK/CONNECTOR. {@link Cell#setType}
     * never clears {@code regionId}, so every pass that reclassifies a cell away
     * from CORRIDOR/ROOM (backFill pruning a dead end, mergeRegions/
     * cullRegionConnectors reverting an unopened connector, dilation's wall
     * rebuild, ...) leaves that cell's old regionId sitting there, still
     * numerically >= idGenerator.getStart() and therefore indistinguishable from
     * a live region UNLESS the cell's current type is also checked. Without this,
     * a door can be created against a cell that used to belong to a region but no
     * longer renders anything there at all — a door leading into bare,
     * unmodified terrain.
     */
    private boolean isRenderedRegionCell(Grid2D grid, int x, int y) {
        if (x < 0 || y < 0 || x >= grid.getWidth() || y >= grid.getHeight()) {
            return false;
        }
        CellType t = grid.get(x, y).getType();
        return t == CellType.CORRIDOR || t == CellType.ROOM;
    }

    /**
     * Bounds-checked region-id lookup: an out-of-bounds cell returns {@code 0},
     * matching an unassigned {@link Cell}'s default {@code regionId} (always
     * {@code < idGenerator.getStart()}, so it's never mistaken for a real region).
     */
    private int regionIdAt(Grid2D grid, int x, int y) {
        if (x < 0 || y < 0 || x >= grid.getWidth() || y >= grid.getHeight()) {
            return 0;
        }
        return grid.get(x, y).getRegionId();
    }

    /**
     * For each of region1/region2 that's a ROOM, checks whether (x,y) sits near
     * one of that room's 4 corners (same margin as the existing per-wall corner
     * exclusion above) and, if so, atomically claims it. Returns false — without
     * claiming anything — the moment ANY involved room-corner is already claimed,
     * so a caller can reject the whole candidate rather than leave a partial
     * claim behind.
     */
    private boolean claimRoomCorners(Region2D region1, Region2D region2, int x, int y) {
        int corner1 = region1.getType() == RegionType.ROOM ? roomCornerIndex(region1.getBox(), x, y) : -1;
        int corner2 = region2.getType() == RegionType.ROOM ? roomCornerIndex(region2.getBox(), x, y) : -1;
        if (corner1 >= 0 && isCornerClaimed(region1.getId(), corner1)) {
            return false;
        }
        if (corner2 >= 0 && isCornerClaimed(region2.getId(), corner2)) {
            return false;
        }
        if (corner1 >= 0) {
            claimedRoomCorners.computeIfAbsent(region1.getId(), k -> new HashSet<>()).add(corner1);
        }
        if (corner2 >= 0) {
            claimedRoomCorners.computeIfAbsent(region2.getId(), k -> new HashSet<>()).add(corner2);
        }
        return true;
    }

    private boolean isCornerClaimed(int roomId, int corner) {
        Set<Integer> claimed = claimedRoomCorners.get(roomId);
        return claimed != null && claimed.contains(corner);
    }

    /**
     * Which of a room's 4 corners (0=NW, 1=NE, 2=SW, 3=SE) the point (x,y) sits
     * near, using the same 2-cell margin as the existing per-wall corner
     * exclusion in {@link #generateConnector}. Returns -1 if not near any corner.
     * Works for both a fixed-row candidate (a north/south door, where y sits
     * just outside the box and x varies) and a fixed-column candidate (an east/
     * west door, where x sits just outside the box and y varies) — the door's
     * own coordinate on its wall's axis is always comfortably within the margin
     * of that axis, so no branch on door orientation is needed here.
     */
    private int roomCornerIndex(Rectangle2D box, int x, int y) {
        int margin = 2;
        boolean west = x <= box.getMinX() + margin;
        boolean east = x >= box.getMaxX() - margin;
        boolean north = y <= box.getMinY() + margin;
        boolean south = y >= box.getMaxY() - margin;
        if (west && north) return 0;
        if (east && north) return 1;
        if (west && south) return 2;
        if (east && south) return 3;
        return -1;
    }

    /**
     * True if both frame-axis neighbors of a candidate door cell are non-
     * walkable (i.e. still solid wall/rock), so a door placed there is actually
     * flanked by wall rather than standing beside open corridor/room space.
     * Cells outside the grid count as solid (the level border).
     */
    private boolean hasSolidDoorFrame(Grid2D grid, int x1, int z1, int x2, int z2) {
        return !isFrameOpen(grid, x1, z1) && !isFrameOpen(grid, x2, z2);
    }

    private boolean isFrameOpen(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return false;
        }
        CellType t = grid.get(x, z).getType();
        return t == CellType.CORRIDOR || t == CellType.ROOM || t == CellType.DOOR || t == CellType.CONNECTOR;
    }

    /**
     * Turns the raw set of {@link Connector2D candidate connectors} (every wall
     * cell that touches two different regions) into the dungeon's actual doors.
     *
     * <p>Conceptually this is "carve openings between regions until the dungeon
     * is joined, but don't over-connect." The algorithm:</p>
     * <ol>
     *     <li>Repeatedly pick a random remaining connector and turn it into a
     *         {@link CellType#DOOR door} ({@link #addDoor}).</li>
     *     <li>Discard the connectors immediately adjacent to that new door (you
     *         don't want two doors side by side) and the duplicate connectors
     *         between the same two regions (you don't want ten doors between one
     *         pair of rooms) &mdash; with a small random chance ({@code > 0.965})
     *         of keeping a duplicate as an extra door, up to each room's
     *         {@code degrees} limit, to create the occasional loop/branch.</li>
     *     <li>Skip/cull connectors for any room that has already reached its
     *         {@code degrees} cap (see {@link #cullRegionsConnectors}).</li>
     * </ol>
     *
     * <p><strong>Important limitation:</strong> this pass does <em>not</em> track
     * connected components, so it can leave a region orphaned (this is the bug
     * {@link #ensureConnectivity} was added to fix &mdash; see the TODO below).
     * It also mutates the working connector list ({@link #getConnectors()}) as it
     * goes; that list is empty by the time this method returns.</p>
     */
    public void mergeRegions(ILevel2D level, Random random) {

        // id -> room lookup so we can read each region's degree cap and record
        // the doors we open back onto the rooms they touch.
        Map<Integer, IRoom2D> roomMap = new HashMap<>();
        level.getRooms().forEach(r -> {
            roomMap.put(Integer.valueOf(r.getId()), r);
        });

        // Reused scratch list of "connectors between the same region pair as the
        // door we just opened" (cleared at the top of every iteration).
        List<Connector2D> localConnectors = new ArrayList<>();

        // Process connectors until none remain. Each iteration opens at most one
        // primary door (plus the occasional random extra) and removes a batch of
        // now-irrelevant connectors, so the list strictly shrinks and this halts.
        while (!getConnectors().isEmpty()) {
            localConnectors.clear();
            // randomly select a connector
            Connector2D connector = getConnectors().get(random.nextInt(getConnectors().size()));

            Region2D region1 = (Region2D) regionMap.get(connector.getRegion1().getId());
            Region2D region2 = (Region2D) regionMap.get(connector.getRegion2().getId());

            // If either region is already "full" (a room at its degree cap), don't
            // open this door -- cull that region's connectors and pick another.
            if (cullRegionsConnectors(level, roomMap, region1, region2)) {
                continue;
            }

            // TODO maybe this should only hold true for the End room, else you could get a disconnected dungeon.
            // TODO may have to implement a path checker to ensure a path exists from start to end
            // and remove any rooms/halls that aren't connected. (only have ensure that a region is visited,
            // not every possible path to that region)
            // NOTE: this gap is now backstopped by ensureConnectivity(), which runs
            // after this method and guarantees start can reach end.

            /*
             * create a door from selected connector
             */
            addDoor(level, connector, roomMap);

            // mark regions as merged (not sure if this serves a purpose yet)
            connector.getRegion1().setMerged(true);
            connector.getRegion2().setMerged(true);

            // remove the door connector first so that it remains a door and not set to wall.
            getConnectors().remove(connector);

            // An AUTHORED doorway may be more than one cell wide -- a template's
            // double door is two adjacent dungeons2:door / dungeons2:connector
            // markers. The anti-side-by-side culling further down would wall one
            // half of it, leaving a two-cell opening with only one cell connected.
            // Open the whole authored run as a single doorway, and do it BEFORE the
            // degree check below so a `continue` can never leave one half-open.
            for (Connector2D sibling : authoredRunSiblings(connector, roomMap)) {
                addDoor(level, sibling, roomMap);
                sibling.getRegion1().setMerged(true);
                sibling.getRegion2().setMerged(true);
                getConnectors().remove(sibling);
            }

            // to prevent room connections to exceed degrees, cull all connectors
            // from each region if # of doors > degrees.
            if (cullRegionsConnectors(level, roomMap, region1, region2)) {
                continue;
            }

            /*
             * gather all connectors that match the regions
             */
            // Collect every other candidate connector that bridges the SAME two
            // regions we just doored (in either order). These are redundant for
            // basic connectivity; the block below culls most and randomly keeps a
            // few as extra doors.
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

            // Walk the remaining same-pair connectors. Most become wall; a rare
            // few become extra doors so the dungeon isn't a strict tree (it gets
            // the occasional loop / double-connection between two regions).
            List<Connector2D> ignoreList = new ArrayList<>();
            int connectorCount = 1;
            for(Connector2D c : localConnectors) {
                IRoom2D room1 = roomMap.get(c.getRegion1().getId());
                IRoom2D room2 = roomMap.get(c.getRegion2().getId());
                // Effective degree cap for this pair = the stricter of the two
                // rooms' caps (corridors aren't in roomMap -> treated as maxDegrees).
                int degrees = (room1 != null && room2 != null) ? Math.min(room1.getDegrees(), room2.getDegrees())
                        : room1 == null ? room2 == null ? maxDegrees : room2.getDegrees() : room1.getDegrees();

                // ~3.5% chance to keep this as an extra door, but only if we
                // haven't already ignored it and the pair is under its degree cap.
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
//            LOGGER.debug("region -> {} of type -> {}", region.getId(), region.getType());
            if (region.getType() == RegionType.ROOM) {
                IRoom2D room = roomMap.get(region.getId());
//                LOGGER.debug("room -> {} has degrees -> {} and doors -> {}", room.getId(), room.getDegrees(), room.getDoorways().size());

                if (room.getDoorways().size() >= room.getDegrees()) {
//                    LOGGER.debug("room -> {} has met its degrees. moving to next connector.", room.getId());
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
     * Guarantees the dungeon is fully connected after the (component-unaware)
     * {@link #mergeRegions} pass.
     *
     * <p>Builds a union-find of regions from the doors that were actually placed,
     * then runs a Kruskal-style sweep over {@code allConnectors} (the full set of
     * candidate doorways discovered earlier): for any connector that still joins
     * two disconnected components, it re-opens that connector as a door. Because
     * the region-adjacency graph of a contiguous grid is itself connected, this
     * yields a single spanning tree &mdash; so the start room can always reach the
     * end room.</p>
     *
     * <p>The end room is capped at <strong>one</strong> door: once it has a
     * doorway, no further connectors touching it are opened. That keeps the
     * terminal room's "single entrance" design intact while still guaranteeing it
     * is reachable. For already-connected dungeons this method adds nothing.</p>
     */
    public void ensureConnectivity(ILevel2D level, List<Connector2D> allConnectors) {
        IRoom2D start = level.getStartRoom();
        IRoom2D end = level.getEndRoom();
        if (start == null || end == null) {
            return;
        }

        // Region id -> room, so addDoor can record the new doorway on the rooms
        // a connector touches (corridors aren't in this map; that's fine).
        Map<Integer, IRoom2D> roomMap = new HashMap<>();
        level.getRooms().forEach(r -> roomMap.put(r.getId(), r));

        // ---- Step 1: rebuild the current connectivity as a union-find. ----
        // Each region (every room and every corridor) starts in its own set.
        UnionFind uf = new UnionFind();
        for (Integer id : getRegionMap().keySet()) {
            uf.add(id);
        }
        // Every door mergeRegions already placed joins two regions, so union the
        // pair on each side of every DOOR cell. After this loop, two regions are
        // in the same set iff there's already a path between them. We rebuild this
        // from the grid (rather than tracking it during mergeRegions) so this pass
        // stays a self-contained, independently-testable add-on.
        for (int x = 1; x < level.getWidth() - 1; x++) {
            for (int y = 1; y < level.getHeight() - 1; y++) {
                if (level.getGrid().get(x, y).getType() == CellType.DOOR) {
                    int[] regions = doorRegions(level, x, y);
                    if (regions != null) {
                        uf.union(regions[0], regions[1]);
                    }
                }
            }
        }

        int endId = end.getId();
        boolean endHasDoor = !end.getDoorways().isEmpty();

        // ---- Step 2: Kruskal-style spanning sweep over the candidate doors. ----
        // allConnectors is every doorway position discovered before mergeRegions
        // (most were culled back to wall). We walk them once and, like building a
        // minimum spanning tree, open one as a real door whenever it bridges two
        // regions that are NOT yet connected. Opening it merges their sets, so we
        // never create a redundant loop. Since the region-adjacency graph of a
        // contiguous grid is connected, this leaves every region in one set --
        // i.e. the whole dungeon becomes reachable, start room included.
        for (Connector2D connector : allConnectors) {
            int r1 = connector.getRegion1().getId();
            int r2 = connector.getRegion2().getId();

            boolean touchesEnd = (r1 == endId || r2 == endId);
            // The terminal room is allowed exactly one entrance. Once it has a
            // door, skip any further connectors touching it -- the rest of the
            // sweep will route around it via corridors instead.
            if (touchesEnd && endHasDoor) {
                continue;
            }
            // Both regions already reachable from each other -> opening this door
            // would just add a redundant loop. Leave it as wall.
            if (uf.connected(r1, r2)) {
                continue;
            }
            // This connector bridges two separate components: re-open it as a door
            // and merge the components.
            addDoor(level, connector, roomMap);
            uf.union(r1, r2);
            if (touchesEnd) {
                endHasDoor = true;
            }
        }

        if (!uf.connected(start.getId(), endId)) {
            // The connector graph itself was disconnected: the end room's cluster
            // is walled off from the rest by solid rock, so no candidate door can
            // bridge it. Carve a fresh tunnel through the rock to guarantee a path.
            forceConnect(level, start, end);
        }
    }

    /**
     * Last-resort connectivity: carves a brand-new corridor through solid rock
     * from the end room's cluster to the start room's cluster.
     *
     * <p>Only invoked when {@link #ensureConnectivity}'s connector sweep can't
     * join the two (the candidate-connector graph is itself split). It runs a
     * breadth-first search outward from the end cluster, stepping through
     * <em>any</em> cell &mdash; rock and wall included &mdash; until it touches a
     * cell already reachable from the start. The shortest such path is then carved
     * to corridor, which makes the route walkable. Cells already passable along
     * the way are left as-is, so the tunnel naturally stitches through any
     * intermediate pockets too.</p>
     */
    private void forceConnect(ILevel2D level, IRoom2D start, IRoom2D end) {
        Grid2D grid = level.getGrid();
        // Cells already reachable from the start room (our BFS target set).
        Set<Long> startComponent = floodPassable(grid,
                start.getOrigin().getX() + start.getWidth() / 2,
                start.getOrigin().getY() + start.getHeight() / 2);
        // Cells in the end room's (currently isolated) cluster (our BFS sources).
        Set<Long> endComponent = floodPassable(grid,
                end.getOrigin().getX() + end.getWidth() / 2,
                end.getOrigin().getY() + end.getHeight() / 2);
        if (startComponent.isEmpty() || endComponent.isEmpty()) {
            return;
        }

        // parent[] records how each cell was reached, so once we hit the start
        // component we can walk the path back to a source and carve it.
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        // Seed sources in sorted order so the BFS (and thus the carved tunnel) is
        // deterministic -- a HashSet's iteration order is not.
        endComponent.stream().sorted().forEach(cellKey -> {
            parent.put(cellKey, cellKey); // a source is its own parent
            queue.add(new int[]{unpackX(cellKey), unpackY(cellKey)});
        });

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            long ck = packKey(c[0], c[1]);
            for (int[] d : dirs) {
                int nx = c[0] + d[0];
                int ny = c[1] + d[1];
                // Never tunnel into the outer border (it's the level's wall).
                if (nx < 1 || ny < 1 || nx >= grid.getWidth() - 1 || ny >= grid.getHeight() - 1) {
                    continue;
                }
                long nk = packKey(nx, ny);
                if (startComponent.contains(nk)) {
                    // Path found: carve the rock/wall cells from c back to a source.
                    carveTunnel(grid, parent, ck, start, end);
                    return;
                }
                if (!parent.containsKey(nk)) {
                    parent.put(nk, ck);
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        // Should be unreachable on a contiguous grid (the level interior is one
        // solid block of cells), but log rather than silently leave it split.
        LOGGER.warn("forceConnect: no tunnel route from end {} to start {}",
                end.getId(), start.getId());
    }

    /**
     * Carves every not-yet-walkable cell on the BFS path (walking parent links
     * from {@code fromKey} back to an end-cluster source) into corridor. Cells
     * that are already corridor/door/room are left untouched.
     *
     * <p>Logs a permanent (not TEMP-diagnostic) summary on completion —
     * {@code start}/{@code end} are the dungeon's overall start/end rooms (the
     * ones {@link #ensureConnectivity} is trying to join), the id/cell-count/
     * grid-local bounding box identify the tunnel itself, and {@code doorCells}
     * flags the awkward case: a very short tunnel (few cells, small bbox) that
     * enters a room right next to where it entered rock reads as a small
     * "closet" with a door that doesn't obviously lead anywhere — this is the
     * one connectivity path in the generator that skips every other quality
     * check added since (frame requirement, corner-claim tracking, divider
     * preservation), because its only job is guaranteeing a path exists at all.</p>
     */
    private void carveTunnel(Grid2D grid, Map<Long, Long> parent, long fromKey, IRoom2D start, IRoom2D end) {
        // A fresh region id so convertLevel groups the tunnel as one corridor.
        Region2D tunnel = new Region2D(idGenerator.next());
        tunnel.setType(RegionType.CORRIDOR);
        getRegionMap().put(tunnel.getId(), tunnel);

        int cellCount = 0;
        int doorCells = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        long current = fromKey;
        while (true) {
            int x = unpackX(current);
            int y = unpackY(current);
            Cell cell = grid.get(x, y);
            if (cell.getType() == CellType.ROCK) {
                cell.setType(CellType.CORRIDOR);
                cell.setRegionId(tunnel.getId());
            } else if (cell.getType() == CellType.WALL) {
                // A wall that borders a room is the room's perimeter -- the tunnel
                // is entering/leaving that room here, so this cell is a DOORWAY,
                // not a corridor punched through the wall. Carving it to corridor
                // would leave a corridor cell touching the room interior (a wall
                // rendered cutting through the room). Pure rock-region walls (no
                // room neighbor) become corridor as before.
                cell.setType(hasRoomNeighbor(grid, x, y) ? CellType.DOOR : CellType.CORRIDOR);
                if (cell.getType() == CellType.CORRIDOR) {
                    cell.setRegionId(tunnel.getId());
                } else {
                    doorCells++;
                }
            }
            cellCount++;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, y);
            maxZ = Math.max(maxZ, y);
            long next = parent.get(current);
            if (next == current) {
                break; // reached a source (already part of the end cluster)
            }
            current = next;
        }

        LOGGER.warn("forceConnect: carved tunnel region={} joining start={} end={} cells={} doorCells={} "
                        + "gridBounds=x[{}..{}] z[{}..{}]",
                tunnel.getId(), start.getId(), end.getId(), cellCount, doorCells, minX, maxX, minZ, maxZ);
    }

    /** True if any orthogonal neighbor of (x,z) is a room interior cell. */
    private boolean hasRoomNeighbor(Grid2D grid, int x, int z) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + d[0], nz = z + d[1];
            if (nx < 0 || nz < 0 || nx >= grid.getWidth() || nz >= grid.getHeight()) {
                continue;
            }
            Cell n = grid.get(nx, nz);
            if (n != null && n.getType() == CellType.ROOM) {
                return true;
            }
        }
        return false;
    }

    /**
     * Flood-fills the set of walkable cells (corridor / door / room interior)
     * reachable from {@code (startX, startY)}, as packed x/y keys.
     */
    private Set<Long> floodPassable(Grid2D grid, int startX, int startY) {
        Set<Long> visited = new HashSet<>();
        if (!isWalkable(grid, startX, startY)) {
            return visited;
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited.add(packKey(startX, startY));
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0];
                int ny = c[1] + d[1];
                if (isWalkable(grid, nx, ny) && visited.add(packKey(nx, ny))) {
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return visited;
    }

    private boolean isWalkable(Grid2D grid, int x, int y) {
        if (x < 0 || y < 0 || x >= grid.getWidth() || y >= grid.getHeight()) {
            return false;
        }
        CellType t = grid.get(x, y).getType();
        return t == CellType.CORRIDOR || t == CellType.DOOR || t == CellType.ROOM;
    }

    /** Packs non-negative x/y grid coords into one long key for visited/parent maps. */
    private static long packKey(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
    private static int unpackX(long key) {
        return (int) (key >> 32);
    }
    private static int unpackY(long key) {
        return (int) (key & 0xffffffffL);
    }

    /**
     * Resolves the two region ids a DOOR cell connects, mirroring the adjacency
     * logic in {@link #generateConnector}. Returns null if the cell doesn't sit
     * between two distinct regions.
     */
    private int[] doorRegions(ILevel2D level, int x, int y) {
        Grid2D grid = level.getGrid();
        int north = grid.get(x, y - 1).getRegionId();
        int south = grid.get(x, y + 1).getRegionId();
        if (north >= idGenerator.getStart() && south >= idGenerator.getStart() && north != south) {
            return new int[]{north, south};
        }
        int east = grid.get(x + 1, y).getRegionId();
        int west = grid.get(x - 1, y).getRegionId();
        if (east >= idGenerator.getStart() && west >= idGenerator.getStart() && east != west) {
            return new int[]{east, west};
        }
        return null;
    }

    /** Minimal region-id union-find for the connectivity guarantee. */
    private static final class UnionFind {
        private final Map<Integer, Integer> parent = new HashMap<>();

        void add(int x) {
            parent.putIfAbsent(x, x);
        }

        int find(int x) {
            parent.putIfAbsent(x, x);
            // First walk up parent links until we reach the set's root (a node
            // that is its own parent).
            int root = x;
            while (parent.get(root) != root) {
                root = parent.get(root);
            }
            // Path compression: walk the chain again, repointing every node we
            // passed directly at the root. This flattens the tree so future
            // find() calls on these nodes are O(1).
            while (parent.get(x) != root) {
                int next = parent.get(x);
                parent.put(x, root);
                x = next;
            }
            return root;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra != rb) {
                parent.put(ra, rb);
            }
        }

        boolean connected(int a, int b) {
            return find(a) == find(b);
        }
    }

    /**
     *
     * @param level
     */
    public void backFill(ILevel2D level) {
        // Doors are placed (discoverConnectors/mergeRegions/ensureConnectivity)
        // BEFORE this runs, so a corridor cell that's a legitimate dead-end tip
        // can also be the exact cell a door was placed against. backFill has no
        // awareness of doors -- a DOOR cell isn't ROCK/WALL, so from an adjacent
        // corridor cell's perspective it just reads as "open" -- and would
        // happily eat that cell anyway, leaving the door opening onto whatever
        // backFill converts it to (a plain WALL). Protect any CORRIDOR cell
        // directly adjacent to a DOOR so a door's far side always survives.
        Set<Coords2D> doorAdjacentCorridors = collectDoorAdjacentCorridors(level);
        // scan all cells for solid rock
        // NOTE skip border cells as they are "walls"
        for (int x = 1; x < level.getWidth()-1; x+=2) {
            for (int y = 1; y < level.getHeight()-1; y+=2) {
                // process at coords
                backFill(level, new Coords2D(x, y), doorAdjacentCorridors);
            }
        }
    }

    /** Every CORRIDOR cell cardinal-adjacent to a DOOR cell, at the point backFill runs. */
    private Set<Coords2D> collectDoorAdjacentCorridors(ILevel2D level) {
        Grid2D grid = level.getGrid();
        Set<Coords2D> out = new HashSet<>();
        for (int x = 1; x < grid.getWidth() - 1; x++) {
            for (int y = 1; y < grid.getHeight() - 1; y++) {
                if (grid.get(x, y).getType() != CellType.DOOR) continue;
                for (int[] off : CARDINALS) {
                    int nx = x + off[0];
                    int ny = y + off[1];
                    if (grid.get(nx, ny).getType() == CellType.CORRIDOR) {
                        out.add(new Coords2D(nx, ny));
                    }
                }
            }
        }
        return out;
    }

    /**
     *
     * @param level
     * @param startingCoords
     */
    private void backFill(ILevel2D level, Coords2D startingCoords, Set<Coords2D> doorAdjacentCorridors) {
        Coords2D active = startingCoords;
        List<CellType> elements = Arrays.asList(CellType.ROCK, CellType.WALL);

        while (active != null) {
            // Only a CORRIDOR cell can legitimately be a dead-end tip. The outer
            // scan calls this for every odd grid position regardless of type, so
            // most calls land on WALL/ROCK/ROOM/DOOR cells that were never a real
            // dead end -- without this guard, a WALL cell that happens to have 3
            // ROCK/WALL neighbors (e.g. a divider between two wide corridors that
            // stops short of a room) is misread as a dead-end tip and eaten too.
            if (level.getGrid().get(active).getType() != CellType.CORRIDOR) {
                break;
            }
            if (doorAdjacentCorridors.contains(active)) {
                break;
            }
            int wallCount = 0;
            Coords2D next = null;

            // test for 3 wall. Out-of-bounds counts as a wall (matches the
            // border convention used elsewhere in this class) -- without this,
            // a dead-end walk that reaches x/y 0 or 1 can step to -1 and index
            // out of bounds, since the border itself is never touched by any
            // other pass and this loop has no innate reason to stop there.
            if (isWallOrOutOfBounds(level, elements, active.getX(), active.getY()-1)) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX(), active.getY()-1);
            }
            if (isWallOrOutOfBounds(level, elements, active.getX(), active.getY()+1)) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX(), active.getY()+1);
            }
            if (isWallOrOutOfBounds(level, elements, active.getX()+1, active.getY())) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX()+1, active.getY());
            }
            if (isWallOrOutOfBounds(level, elements, active.getX()-1, active.getY())) {
                wallCount++;
            }
            else {
                next = new Coords2D(active.getX()-1, active.getY());
            }

            if (wallCount >= 3) {
                // A pruned dead-end tip must become WALL, not bare ROCK: the
                // surviving junction cell it retreats from can end up with this
                // as its ONLY non-corridor neighbor (common once corridors are
                // wide enough to have interior cells), and ROCK doesn't count as
                // a boundary the way WALL/DOOR/ROOM do. ROCK and WALL render and
                // behave identically everywhere else in this class, so this is
                // a pure classification fix, not a behavior change.
                level.getGrid().get(active).setType(CellType.WALL);

                // move to the tile in the open direction
                active = next;
            }
            else {
                active = null;
            }
        }
    }

    private boolean isWallOrOutOfBounds(ILevel2D level, List<CellType> elements, int x, int y) {
        if (x < 0 || y < 0 || x >= level.getWidth() || y >= level.getHeight()) {
            return true;
        }
        return elements.contains(level.getGrid().get(x, y).getType());
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
     * Carves one corridor "region" into the solid rock using a randomized
     * Prim's-style flood, starting from {@code startCoords}.
     *
     * <p>The maze lives on a grid where corridors occupy <em>even</em> spacing:
     * a corridor cell, then a shared "passage" cell, then the next corridor cell
     * &mdash; so we always step <strong>two</strong> cells at a time and convert
     * the cell in between into corridor too. The cell on either side of a passage
     * (perpendicular to travel) becomes {@link CellType#WALL}, which is what keeps
     * parallel corridors separated.</p>
     *
     * <p>Behavioural knobs:</p>
     * <ul>
     *     <li>{@code runFactor} &mdash; chance of extending from the most recently
     *         added cell (depth-first, long straight runs) vs. a random active
     *         cell (breadth-first, bushier mazes).</li>
     *     <li>{@code curveFactor} &mdash; chance of continuing in the same
     *         direction (straighter corridors) vs. turning.</li>
     *     <li>{@code maxRun} &mdash; a random length cap so a single corridor
     *         region doesn't sprawl across the whole level.</li>
     * </ul>
     *
     * <p>Each call produces a single connected corridor with its own region id;
     * {@link #carve} calls this repeatedly to fill all remaining rock.</p>
     */
    private void prims(ILevel2D level, Coords2D startCoords) {
        // activeList = the "frontier": cells we can still grow out of.
        List<PrimsTile2D> activeList = new ArrayList<>();
        // Scratch maps (per iteration): the eligible next cell in each direction
        // and the passage cell bridging to it.
        Map<Direction2D, PrimsTile2D> neighbors = new HashMap<>();
        Map<Direction2D, PrimsTile2D> passages = new HashMap<>();
        // Random length cap for this corridor region.
        int maxRun = random.nextInt(maxCorridorSize - minCorridorSize) + minCorridorSize;
        // create tile
        PrimsTile2D tile = new PrimsTile2D(startCoords, Direction2D.SOUTH);

        // add tile to activeList
        activeList.add(tile);

        // Each prims() call is one corridor region with a fresh id; this is how
        // discoverConnectors later tells corridors apart.
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
            // Pick the cell to grow from. runFactor biases toward the newest
            // frontier cell (long winding runs); otherwise pick one at random
            // (more branching).
            PrimsTile2D active = null;
            if (random.nextDouble() < runFactor) {
                active = activeList.get(activeList.size()-1);
            } else {
                active = activeList.get(random.nextInt(activeList.size()));
            }

            // init temporary variables
            neighbors.clear();
            passages.clear();

            // Scan all four directions. A direction is eligible only if BOTH the
            // passage cell (1 away) and the landing cell (2 away) are still solid
            // ROCK -- that guarantees we never carve into an existing corridor/room.
            // If the landing cell is blocked, the passage cell is walled off so it
            // can't later become a stray opening.
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

            // Dead end: nowhere left to grow from this cell, drop it from the
            // frontier and move on.
            if (neighbors.isEmpty()) {
                activeList.remove(active);
                continue;
            }

            PrimsTile2D selected = null;
            PrimsTile2D passage = null;
            // Prefer continuing straight (curveFactor) when the current heading is
            // still available; otherwise turn toward a random eligible direction.
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

            // The landing cell joins the frontier so we can keep growing from it.
            activeList.add(selected);

            // add neighbor and passage to region
//            region.addTile(selected);
//            region.addTile(passage);

            // Carve both the landing cell and the passage cell between it and the
            // current cell into corridor, tagged with this region's id.
            level.getGrid().get(selected.getCoords()).setRegionId(region.getId());
            level.getGrid().get(selected.getCoords()).setType(CellType.CORRIDOR);

            level.getGrid().get(passage.getCoords()).setRegionId(region.getId());
            level.getGrid().get(passage.getCoords()).setType(CellType.CORRIDOR);

            // Wall off the two cells flanking the passage (perpendicular to travel)
            // so adjacent parallel corridors stay separated by a wall.
            switch(passage.getDirection()) {
                case NORTH, SOUTH -> {
                    level.getGrid().get(passage.getX()-1, passage.getY()).setType(CellType.WALL);
                    level.getGrid().get(passage.getX()+1, passage.getY()).setType(CellType.WALL);
                }
                case EAST, WEST -> {
                    level.getGrid().get(passage.getX(), passage.getY()-1).setType(CellType.WALL);
                    level.getGrid().get(passage.getX(), passage.getY()+1).setType(CellType.WALL);
                }
            }

            // NOTE this section isn't working at expected
//            Cell passageCell = level.getGrid().get(passage.getCoords());
//            switch(passage.getDirection()) {
//                case NORTH, SOUTH -> {
//                    // check the far side of walls for same region
//                    if (passage.getX()-2 > 0 && level.getGrid().get(passage.getX() - 2, passage.getY()).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX() - 2, passage.getY()).getRegionId() == passageCell.getRegionId()) {
//                        level.getGrid().get(passage.getX() - 1, passage.getY()).setRegionId(passageCell.getRegionId());
//                        level.getGrid().get(passage.getX() - 1, passage.getY()).setType(CellType.CORRIDOR);
//                    } else {
//                        level.getGrid().get(passage.getX() - 1, passage.getY()).setType(CellType.WALL);
//                    }
//
//                    if (passage.getX()+2 < getWidth() && level.getGrid().get(passage.getX() + 2, passage.getY()).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX() + 2, passage.getY()).getRegionId() == passageCell.getRegionId()) {
//                        level.getGrid().get(passage.getX() + 1, passage.getY()).setRegionId(passageCell.getRegionId());
//                        level.getGrid().get(passage.getX() + 1, passage.getY()).setType(CellType.CORRIDOR);
//                    } else {
//                        level.getGrid().get(passage.getX() + 1, passage.getY()).setType(CellType.WALL);
//                    }
//                }
//                case EAST, WEST -> {
//                    if(passage.getY()-2 > 0 && level.getGrid().get(passage.getX(), passage.getY()-2).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX(), passage.getY()-2).getRegionId() == passageCell.getRegionId()) {
//                        level.getGrid().get(passage.getX(), passage.getY() - 1).setRegionId(passageCell.getRegionId());
//                        level.getGrid().get(passage.getX(), passage.getY() - 1).setType(CellType.CORRIDOR);
//                    } else {
//                        level.getGrid().get(passage.getX(), passage.getY() - 1).setType(CellType.WALL);
//                    }
//
//                    if (passage.getY()+2 < getHeight() && level.getGrid().get(passage.getX(), passage.getY()+2).getType() == CellType.CORRIDOR && level.getGrid().get(passage.getX(), passage.getY()+2).getRegionId() == passageCell.getRegionId()) {
//                        level.getGrid().get(passage.getX(), passage.getY() + 1).setRegionId(passageCell.getRegionId());
//                        level.getGrid().get(passage.getX(), passage.getY() + 1).setType(CellType.CORRIDOR);
//                    } else {
//                        level.getGrid().get(passage.getX(), passage.getY() + 1).setType(CellType.WALL);
//                    }
//                }
//            }

            // Length cap: once this corridor has grown maxRun steps, abandon the
            // frontier so the region stops here (the rest of the rock is left for
            // subsequent prims() calls to become other corridor regions).
            runCount++;
            if (runCount > maxRun) {
                activeList.clear();
            }
        }
    }

    /**
     * Widens every Prim's-carved corridor by {@code passes} cells in each
     * cardinal direction. After one pass, a 1-wide corridor becomes 2 cells
     * wide; after two passes, 3 cells wide; and so on.
     *
     * <p><strong>Safety rules:</strong></p>
     * <ul>
     *     <li>Never carves into a {@link CellType#ROOM} cell &mdash; rooms
     *         retain their full interior.</li>
     *     <li>Never carves into a {@link CellType#WALL} cell that's adjacent
     *         to a {@link CellType#ROOM} &mdash; preserves the room's outer
     *         wall ring (so corridors can touch rooms but never breach them).</li>
     *     <li>Never carves into a cell that touches CORRIDOR cells of more
     *         than one distinct region &mdash; that cell is the divider
     *         between two separate corridors, and carving it would silently
     *         fuse them into one blob. The two corridors stay visually
     *         distinct (and, if the maze needs them connected, get a proper
     *         door there via {@link #discoverConnectors} instead).</li>
     * </ul>
     *
     * <p>After dilation, walks the grid once more to rebuild
     * {@link CellType#WALL} cells around the now-fat corridors (the old wall
     * positions are now mid-corridor and need to be promoted/demoted
     * appropriately).</p>
     *
     * <p>Purely deterministic &mdash; no RNG &mdash; so dilation does not
     * affect the per-seed determinism guarantee.</p>
     *
     * <p>Must run <strong>before</strong> {@link #discoverConnectors} so doors
     * are placed on the widened corridor walls.</p>
     */
    public void dilateCorridors(ILevel2D level, int passes) {
        if (passes <= 0) return;
        Grid2D grid = level.getGrid();
        // Protect the FULL rectangle of every room (interior + border + corners).
        // Dilation can never enter a room footprint, which keeps room walls
        // intact even for adjacent or overlapping rooms.
        Set<Coords2D> protectedRoomCells = collectRoomFootprintCells(level);

        for (int pass = 0; pass < passes; pass++) {
            // Two-step, over TWO snapshots: first find which CORRIDOR region(s)
            // touch each candidate ROCK/WALL cell, then only carve candidates
            // touched by exactly one region. A cell touched by two (or more)
            // distinct regions is the shared divider between separate corridors
            // and must stay a wall, regardless of carve order.
            Map<Coords2D, Set<Integer>> touchingRegions = new HashMap<>();
            for (int x = 1; x < grid.getWidth() - 1; x++) {
                for (int z = 1; z < grid.getHeight() - 1; z++) {
                    Cell cell = grid.get(x, z);
                    if (cell.getType() != CellType.CORRIDOR) continue;
                    int regionId = cell.getRegionId();
                    for (int[] off : CARDINALS) {
                        int nx = x + off[0];
                        int nz = z + off[1];
                        Cell neighbor = grid.get(nx, nz);
                        CellType nType = neighbor.getType();
                        if ((nType == CellType.ROCK || nType == CellType.WALL)
                                && !protectedRoomCells.contains(new Coords2D(nx, nz))) {
                            touchingRegions.computeIfAbsent(new Coords2D(nx, nz), k -> new HashSet<>())
                                    .add(regionId);
                        }
                    }
                }
            }
            // Process candidates in a deterministic (x, then z) order, independent
            // of HashMap iteration order, since the live-grid re-check below makes
            // carve order observable: two candidates from DIFFERENT regions can
            // each look single-region-touched in the snapshot above yet be
            // cardinal-adjacent to EACH OTHER (growing toward each other from
            // opposite sides in the same pass). The snapshot alone can't see that;
            // re-checking against the live grid at carve time can.
            List<Map.Entry<Coords2D, Set<Integer>>> candidates = new ArrayList<>(touchingRegions.entrySet());
            candidates.sort(Comparator.<Map.Entry<Coords2D, Set<Integer>>>comparingInt(e -> e.getKey().getX())
                    .thenComparingInt(e -> e.getKey().getY()));
            for (Map.Entry<Coords2D, Set<Integer>> e : candidates) {
                if (e.getValue().size() != 1) continue; // divider between 2+ regions — keep as wall
                Coords2D pos = e.getKey();
                int regionId = e.getValue().iterator().next();
                Cell c = grid.get(pos.getX(), pos.getY());
                if (c.getType() == CellType.CORRIDOR) continue;
                if (touchesForeignCorridor(grid, pos.getX(), pos.getY(), regionId)) continue;
                c.setType(CellType.CORRIDOR);
                c.setRegionId(regionId);
            }
        }
        rebuildCorridorWalls(grid, protectedRoomCells);
    }

    /** 4 cardinal offsets for neighbor checks (used for carving/dilation steps). */
    private static final int[][] CARDINALS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /**
     * True if (x,z) is cardinal-adjacent, on the LIVE grid, to a CORRIDOR cell
     * belonging to a region other than {@code regionId}. Used at dilation carve
     * time (not just from the pre-pass snapshot) to catch two candidates from
     * different regions growing into direct contact with each other within the
     * same pass.
     */
    private boolean touchesForeignCorridor(Grid2D grid, int x, int z, int regionId) {
        for (int[] off : CARDINALS) {
            int nx = x + off[0];
            int nz = z + off[1];
            if (nx < 0 || nz < 0 || nx >= grid.getWidth() || nz >= grid.getHeight()) continue;
            Cell n = grid.get(nx, nz);
            if (n != null && n.getType() == CellType.CORRIDOR && n.getRegionId() != regionId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects every cell inside any room's footprint rectangle (origin to
     * origin+width/depth). These cells &mdash; the room interior, its border
     * walls, and its corners &mdash; are all protected from corridor dilation,
     * so corridors can touch a room but never breach or erode it.
     */
    private Set<Coords2D> collectRoomFootprintCells(ILevel2D level) {
        Set<Coords2D> out = new HashSet<>();
        for (IRoom2D room : level.getRooms()) {
            int ox = room.getOrigin().getX();
            int oz = room.getOrigin().getY();
            for (int x = 0; x < room.getWidth(); x++) {
                for (int z = 0; z < room.getHeight(); z++) {
                    out.add(new Coords2D(ox + x, oz + z));
                }
            }
        }
        return out;
    }

    /**
     * After dilation, any non-corridor / non-room cell that now sits adjacent
     * to a corridor must be promoted to {@link CellType#WALL}. Cells inside a
     * room footprint (collected before dilation) are left alone so room walls
     * stay intact.
     */
    private void rebuildCorridorWalls(Grid2D grid, Set<Coords2D> protectedRoomCells) {
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getHeight(); z++) {
                Cell c = grid.get(x, z);
                CellType t = c.getType();
                if (t == CellType.CORRIDOR || t == CellType.ROOM || t == CellType.DOOR) continue;
                if (protectedRoomCells.contains(new Coords2D(x, z))) continue;
                if (hasCorridorNeighbor(grid, x, z)) {
                    c.setType(CellType.WALL);
                }
            }
        }
    }

    private boolean hasCorridorNeighbor(Grid2D grid, int x, int z) {
        for (int[] off : CARDINALS) {
            int nx = x + off[0];
            int nz = z + off[1];
            if (nx < 0 || nz < 0 || nx >= grid.getWidth() || nz >= grid.getHeight()) continue;
            if (grid.get(nx, nz).getType() == CellType.CORRIDOR) return true;
        }
        return false;
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
        public int corridorDilationPasses = 0;

        public IRoom2D startRoom;
        public IRoom2D endRoom;
        public List<IRoom2D> suppliedRooms = new ArrayList<>();
        public Random random;

        public Builder with(Consumer<Builder> builder) {
            builder.accept(this);
            return this;
        }

        /**
         * Convenience: pick corridor width in cells (1 = classic 1-wide,
         * 2 = 2-wide, 3 = 3-wide, etc.). Internally translates to dilation
         * passes (cells - 1).
         */
        public Builder corridorWidth(int cells) {
            this.corridorDilationPasses = Math.max(0, cells - 1);
            return this;
        }

        /**
         * Convenience: seed the planner's RNG deterministically.
         *
         * <p>Equivalent to {@code this.random = new Random(seed)}. Callers that
         * want byte-identical output across runs (e.g. {@code DungeonStackPlanner})
         * must use this method (or pre-build their own seeded {@link Random})
         * &mdash; the default field initializer creates an unseeded one.</p>
         */
        public Builder seed(long seed) {
            this.random = new Random(seed);
            return this;
        }

        public MazeLevelGenerator2D build() {
            return new MazeLevelGenerator2D(this);
        }
    }
}
