/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.blockprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.block.NullBlock;
import com.someguyssoftware.dungeons2.generator.Arrangement;
import com.someguyssoftware.dungeons2.generator.DungeonGenerator;
import com.someguyssoftware.dungeons2.generator.Location;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.rotate.RotatorHelper;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.Frame;
import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.dungeons2.style.Style;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.gottschcore.block.CardinalDirectionFacadeBlock;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.enums.Rotate;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/**
 * @author Mark Gottschling on Aug 27, 2016
 *
 */
public interface IDungeonsBlockProvider {
	
	public static String NULL_BLOCK_NAME = "null";
	public static IBlockState NULL_BLOCK = new NullBlock().getDefaultState();

	/**
	 * 
	 * @param random
	 * @param worldCoords
	 * @param room
	 * @param arrangement
	 * @param theme
	 * @param styleSheet
	 * @param config
	 * @return
	 */
	default public IBlockState getBlockState(Random random, ICoords worldCoords, Room room, Arrangement arrangement,
			Theme theme, StyleSheet styleSheet, LevelConfig config)  {
		IBlockState blockState = null;
		DesignElement elem = arrangement.getElement();
		
		// apply decay if not air and style dicates
		int decayIndex = -1;
		if (elem != DesignElement.AIR && elem.getFamily() != DesignElement.SURFACE_AIR) {
			// get the style for the element
			Style style = getStyle(elem, room.getLayout(), theme, styleSheet);
			// get the decayed index
			decayIndex = getDecayIndex(random, config.getDecayMultiplier(), style);
			// get calculated blockstate
			blockState = getBlockState(arrangement, style, decayIndex);

		}
		else {
			blockState = Blocks.AIR.getDefaultState();
		}
		return blockState;
	}
	
	/**
	 * 
	 * @param arrangement
	 * @param style
	 * @param decayIndex
	 * @return
	 */
	default public IBlockState getBlockState(Arrangement arrangement, Style style, int decayIndex) {
		String block = "";
		IBlockState blockState = Blocks.AIR.getDefaultState();
		
		if (style == Style.NO_STYLE) return blockState;
		// ensure the decayIndex is right size for selected style
		decayIndex = (decayIndex < style.getDecayBlocks().size()) ? decayIndex : style.getDecayBlocks().size()-1;

		// get the block according to style and decay index
		block = decayIndex > -1 ? style.getDecayBlocks().get(decayIndex) : style.getBlock();

		if (block == null || block == "") return blockState;
		
		// check for special case "null"
		if (block.equals(NULL_BLOCK_NAME)) {
			return NULL_BLOCK;
		}
		
		// get the block based on meta
		String[] blockAndMeta = block.split("@");
//		Dungeons2.log.debug("Style:" + style.getName());
//		Dungeons2.log.debug("Block:" + block);
//		Dungeons2.log.debug("blockAndMeta[0]:" + blockAndMeta[0]);
		int meta = 0;
		// TODO could add additional checks here to ensure the string only contains 1 @ or that the value after @ is numeric
		if (blockAndMeta.length > 1) meta = Integer.valueOf(blockAndMeta[1]);
		blockState = Block.getBlockFromName(blockAndMeta[0]).getStateFromMeta(meta);

		// rotate block to the direction of Arrangement if rotatable type
		DesignElement elem = arrangement.getElement();
		// TODO FUTURE add property to DesignElement enum, ROTATABLE
		if (elem == DesignElement.CROWN || elem == DesignElement.TRIM ||
				elem == DesignElement.CORNICE || elem == DesignElement.PLINTH  ||
				elem == DesignElement.COLUMN || elem == DesignElement.CAPITAL || elem == DesignElement.BASE ||
				elem == DesignElement.PILASTER ||
				elem == DesignElement.LADDER ||
				elem == DesignElement.GUTTER) {			
			 /*
			  *  NOTE gutter didn't get rotated 180 because it is not CardinalDirectionFacade
			  *   but just CardinalDirection.  Nor should it be rotated because it should face into the wall). 
			  */			
			
			/*
			 * Since RelativeDirection and CardinalDirection are built is in opposite direction of Minecraft vanilla blocks,
			 * they have to be rotated 180 degrees from what the direction originally is.
			 */
			Direction d = null;
			if (blockState.getBlock() instanceof CardinalDirectionFacadeBlock) {
				d = arrangement.getDirection().rotate(Rotate.ROTATE_180);
			}
			else {
				d = arrangement.getDirection();
			}
			// rotate the block to face the correct direction in the room
			blockState = RotatorHelper.rotateBlock(blockState, d);
		}
		
		return blockState;
	}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public Arrangement getArrangement(ICoords coords, Room room, Layout layout){
		// determine the design element of the block @ xyz represents: floor, wall, ceiling, crown, trim, cornice, base, pillar door, air, etc					
		DesignElement element = getDesignElement(coords, room, layout);
		Location location = getLocation(coords, room, layout);
		Direction direction = getDirection(coords, room, element, location);

		return new Arrangement(element, location, direction);
	}
	
	/**
	 * @param element
	 * @param location
	 * @return
	 */
	default public Direction getDirection(ICoords coords, Room room, DesignElement element,Location location) {
		Direction direction = Direction.NORTH;
		
		// if ladder rotate to same direction as room
		if (element == DesignElement.LADDER) {
			switch(room.getDirection()) {
			case NORTH:
				direction = Direction.SOUTH;
				break;
			case EAST:
				direction = Direction.WEST;
				break;
			case SOUTH:
				direction = Direction.NORTH;
				break;
			case WEST:
				direction = Direction.EAST;
				break;
			default:
				direction = room.getDirection();
			}
		}
		// TEMP exterior elements need to face in the opposite direction as default interior elements
		else if (
//				element.getFace() == Face.EXTERIOR || //<-- probably only need this one
				element == DesignElement.PLINTH  ||
				element == DesignElement.COLUMN  ||
				element == DesignElement.CORNICE ||
				element == DesignElement.COLUMN ||
				element == DesignElement.CAPITAL ||
				element == DesignElement.BASE	) {
			if (coords.getX() == room.getMinX()) direction = Direction.EAST;
			else if (coords.getX() == room.getMaxX()) direction = Direction.WEST;
			else if (coords.getZ() == room.getMinZ()) direction = Direction.SOUTH;
			else direction = Direction.NORTH;
		}
		else {
			/*
			 * using stairs as an example, the back (the tallest side) is the north side and faces north
			 * therefore if stairs are to be used as trim or cornice, then the direction needs to point in
			 * the same direction of the side. ie NORTH_SIDE = NORTH
			 */
			if (location == Location.SOUTH_SIDE) direction = Direction.SOUTH;
			else if (location == Location.WEST_SIDE) direction = Direction.WEST;
			else if (location == Location.EAST_SIDE) direction = Direction.EAST;
		}
		
		return direction;
	 }
	

	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public DesignElement getDesignElement(ICoords coords, Room room, Layout layout) {

		// check for floor
		if (isFloorElement(coords, room, layout)) {
			if (room.hasGutter() && isGutterElement(coords, room, layout)) {
				return DesignElement.GUTTER;
			}
			// check for grate
			if (room.hasGrate() && isGrateElement(coords, room, layout)) {
				return DesignElement.GRATE;
			}
			return DesignElement.FLOOR;
		}
		
		// check for wall
		if (isWallElement(coords, room, layout)) {
			if (isFacadeSupport(coords, room, layout)) return DesignElement.FACADE_SUPPORT;
			if (room.hasWallBase() && isWallBase(coords, room, layout)) return DesignElement.WALL_BASE;
			if (room.hasWallCapital() && isWallCapital(coords, room, layout)) return DesignElement.WALL_CAPITAL;
			return DesignElement.WALL;
		}
		
		/*
		 *  Optional elements
		 */
		// check for pilaster. if a room has a pilaster and pillar, then pilaster is used
		if (room.hasPilaster() && isPilasterElement(coords, room, layout)) {
			// determine if base, shaft or capital
			if (isBaseElement(coords, room, layout)) return DesignElement.PILASTER_BASE;
			else if (isCapitalElement(coords, room, layout)) return DesignElement.PILASTER_CAPITAL;
			else return DesignElement.PILASTER;
		}
		
		// pillar
		if (room.hasPillar() /*&& !room.hasPilaster()*/ && isPillarElement(coords, room, layout)) {
			if (isBaseElement(coords, room, layout)) return DesignElement.PILLAR_BASE;
			else if (isCapitalElement(coords, room, layout)) return DesignElement.PILLAR_CAPITAL;
			else return DesignElement.PILLAR;
		}
			
		// check for crown molding
		if (room.hasCrown() && isCrownElement(coords, room, layout)) return DesignElement.CROWN;
		// check for trim
		if (room.hasTrim()) {
//			Dungeons2.log.debug("Room has Trim.");
			// &&
			if (isTrimElement(coords, room, layout)) return DesignElement.TRIM;
//			Dungeons2.log.debug("Not a Trim element.");
		}
				
		/* 
		 * End of Optional elements
		 */
		
		// check for coffered ceiling
		if (room.hasCoffer() && isCofferedCrossbeamElement(coords, room, layout)) return DesignElement.COFFERED_CROSSBEAM;
		if (room.hasCoffer() && isCofferedMidbeamElement(coords, room, layout)) return DesignElement.COFFERED_MIDBEAM;
		
		// check for ceiling
		if(isCeilingElement(coords, room, layout)) return DesignElement.CEILING;
		
		// check if air is on a surface, like the floor, ceiling, walls
		if(isSurfaceAirElement(coords, room, layout)) {
			int x = coords.getX();
			int z = coords.getZ();
			
			if (coords.getY() == room.getMinY() + 1) return DesignElement.FLOOR_AIR;
			// check for wall air before ceiling, so the corner edges are recorded as wall as opposed to ceiling
			if (
					((x == room.getMinX() + 1 || x == room.getMaxX()-1) && z > room.getMinZ() && z < room.getMaxZ()) ||
					((z == room.getMinZ()+1 || z == room.getMaxZ()-1) && x > room.getMinX() && x < room.getMaxX())
				) return DesignElement.WALL_AIR;
			if (coords.getY() == room.getMaxY() - 1) return DesignElement.CEILING_AIR;
		}		
		return DesignElement.AIR;
	}
	
	/**
	 * Return same value as crossbeam.
	 * TODO future - determine if midbeam properly.
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isCofferedMidbeamElement(ICoords coords, Room room, Layout layout) {
		return isCofferedCrossbeamElement(coords, room, layout);
	}

	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isCofferedCrossbeamElement(ICoords coords, Room room, Layout layout) {
		if (coords.getY() == room.getMaxY() - 1) return true;
		return false;
	}

	/**
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isSurfaceAirElement(ICoords coords, Room room, Layout layout) {
		if (coords.getY() > room.getMinY() + 1 && coords.getY() < room.getMaxY() - 1 &&
			coords.getX() > room.getMinX() + 1 && coords.getX() < room.getMaxX() -1 &&
			coords.getZ() > room.getMinZ() + 1 && coords.getZ() < room.getMaxZ() -1) return false;
		return true;
	 }

	/**
	 * @param chestCoords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public Location getLocation(ICoords coords, Room room, Layout layout) {
		ICoords center = room.getCenter();
		int zDiff = 0;
		int xDiff = 0;
		int x = coords.getX();
		int y = coords.getY();
		int z = coords.getZ();
		
		if (z < center.getZ()) {
			zDiff = z - room.getMinZ();
			if(x >= (room.getMinX() + zDiff) && x <= (room.getMaxX() - zDiff)) return Location.NORTH_SIDE;
		}
		
		if (z > center.getZ()) {
			zDiff = room.getMaxZ() - z;
			if (x >= (room.getMinX() + zDiff) && x <= (room.getMaxX() - zDiff)) return Location.SOUTH_SIDE;
		}

		if (x < center.getX()) {
			xDiff = x - room.getMinX();
			if (z >= (room.getMinZ() + xDiff) && z <= (room.getMaxZ() - xDiff)) return Location.WEST_SIDE;
		} 
		if (x > center.getX()) {
			xDiff = room.getMaxX() - x;
			if (z >= (room.getMinZ() + xDiff) && z <= (room.getMaxZ() - xDiff)) return Location.EAST_SIDE;
		}
		return Location.MIDDLE;
	}
	
	/**
	 * 
	 * @param elem
	 * @param layout
	 * @param theme
	 * @param styleSheet
	 * @return
	 */
	default public Style getStyle(DesignElement elem, Layout layout, Theme theme, StyleSheet styleSheet) {
		Style style = Style.NO_STYLE;

		// ensure that a layout is used
		if (layout == null) {
			layout = DungeonGenerator.getDefaultStyleSheet().getLayouts().get(Layout.DEFAULT_NAME);
		}
		
		/*
		 *  any of the following elements should return without a style
		 *  NOTE FACADE_SUPPORT should not be included in this list
		 *  as it is a listed design element and returns a WALL!
		 */
		if (elem == null || elem == DesignElement.NONE ||
				elem == DesignElement.AIR || elem.getFamily() == DesignElement.SURFACE_AIR ||
				elem == DesignElement.FACADE/* || elem == DesignElement.FACADE_SUPPORT*/)
			return Style.NO_STYLE;

		// TODO this will be part of the style sheet
		// get the frame from the layout
		Frame frame = layout.getFrames().get(elem.name());
		// recursively check if layout has a ref and check if it contains the frame (up to 5 nested refs)
		if (frame == null) {
			List<String> layoutRefs = new ArrayList<>(5);
			while (frame == null && layout != null && layout.getRef() != null && layout.getRef().length() > 0) {
				if (layoutRefs.contains(layout.getRef())) {
					Dungeons2.log.warn(String.format("Stylesheet layout circular dependency: %s. Using defaults.", layout.getRef()));
					break;
				}
				else if (layoutRefs.size() == 5) {
					Dungeons2.log.warn("Too many Stylesheet layout references (5 max allowed. Using defaults.");
					break;
				}
				// update the layout refs
				layoutRefs.add(layout.getRef());
				// get the new referred layout
				layout = styleSheet.getLayouts().get(layout.getRef());
				if (layout != null) {
					frame = layout.getFrames().get(elem.name());
				}
			}
		}
		
		// if frame is still null then use the default
		if (frame == null) {
//			Dungeons2.log.debug("Getting element from default stylesheet:" + elem.getName());
			frame = DungeonGenerator.getDefaultStyleSheet().getLayouts().get(Layout.DEFAULT_NAME).getFrames().get(elem.name());
		}
		
		// TODO throw custom error UNKNOWN DesignElement
		if (frame == null) {
			return Style.NO_STYLE;
		}
		
		// get the style by the alias or style NAME
		if (theme != null && frame.getAlias() != null && frame.getAlias().length() > 0) {
//			Dungeons2.log.debug(String.format("theme: %s\nAlias:%s", theme.getName(), frame.getAlias()));
			// TODO need to catch error here is style == null or theme.getAliases == null or the alias from the theme doesn't exist
			style = styleSheet.getStyles().get(theme.getAliases().get(frame.getAlias()).getStyle());
			if (style == null) {
				Dungeons2.log.warn(String.format("Unable to locate style based on theme [%s] and frame [%s]'s alias [%s]", theme.getName(), elem.name(), frame.getAlias()));
			}
		}
		else if (frame.getStyle() != null && frame.getStyle().length() > 0) {
//			Dungeons2.log.debug("frame.getStyle:" + frame.getStyle());
			style = styleSheet.getStyles().get(frame.getStyle());
//			Dungeons2.log.debug("style=" + style);
			// get the style from the default stylesheet
			if (style == null) {
				style = DungeonGenerator.getDefaultStyleSheet().getStyles().get(frame.getStyle());
			}
		}

		if (style == null) return Style.NO_STYLE;
		return style;
	}
	
	/**
	 * @param decayMultiplier
	 * @param style
	 * @return
	 */
	default public int getDecayIndex(Random random, int decayMultiplier, Style style) {
		if (decayMultiplier <= 0) return -1;
		
		int decayIndex = -1;
		for (int i = 0; i < decayMultiplier; i++) {
			if (random.nextDouble() * 100 < style.getDecay()) {
				decayIndex++;
			}
		}
		return decayIndex;
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isFloorElement(ICoords coords, Room room, Layout layout) {
		if (coords.getY() == room.getMinY()) return true;
		return false;
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isWallElement(ICoords coords, Room room, Layout layout) {
		if (coords.getX() == room.getMinX() || coords.getZ() == room.getMinZ() ||
				coords.getX() == room.getMaxX() || coords.getZ() == room.getMaxZ()) return true;
		return false;
	}
	
	/**
	 * Assumes isWallElement() has already been met.
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isWallCapital(ICoords coords, Room room, Layout layout) {
		if (coords.getY() == room.getMaxY()-1) return true;
		return false;
	}

	/**
	 * Assumes isWallElement() has already been met.
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isWallBase(ICoords coords, Room room, Layout layout) {
		if (coords.getY() == room.getMinY()+1) return true;
		return false;
	}
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isCeilingElement(ICoords coords, Room room, Layout layout) {
		if (coords.getY() == room.getMaxY()) return true;
		return false;
	}
	
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isCrownElement(ICoords coords, Room room, Layout layout) {
		
		if (coords.getY() == room.getMaxY()-1 &&
				room.getHeight() > 4 && 
				(
					coords.getX() == room.getMinX()+1 ||
					coords.getX() == room.getMaxX()-1 ||
					coords.getZ() == room.getMinZ()+1 ||
					coords.getZ() == room.getMaxZ()-1)
				) return true;
		return false;
	}
	
	/**
	 * Checks for support block for crown and cornice.
	 * It is assumed that the element at x,y,z has already been determined to be a wall. This method simply checks
	 * the vertical position to determine if this element is the 1 less than the ceiling. No additional checks are made to ensure
	 * that is position is indeed a wall.
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isFacadeSupport(ICoords coords, Room room, Layout layout) {
		// check if y is 1 less than top (ceiling)
		if (coords.getY() == room.getMaxY() - 1) return true;
		return false;
	}
	
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isTrimElement(ICoords coords, Room room, Layout layout) {
		if (/*layout.getFrames().get(DesignElement.TRIM.name()) != null &&*/
				/*
				 * room has to be at least 7x7 and 4 high to give enough space to walk around
				 */
				room.getWidth() >=7 &&
				room.getDepth() >=7 &&
				room.getHeight() > 4 &&
				coords.getY() == room.getMinY()+1 &&				
				(
					coords.getX() == room.getMinX()+1 ||
					coords.getX() == room.getMaxX()-1 ||
					coords.getZ() == room.getMinZ()+1 ||
					coords.getZ() == room.getMaxZ()-1)
				) return true;
		return false;
	}
	
	/**
	 * Default isLadder method is a ladder that in going down from the floor as it is assumed dungeons go down.
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isLadderElement(ICoords coords, Room room, Layout layout) {
		int x = coords.getX();
		int y = coords.getY();
		int z = coords.getZ();		
		ICoords center = room.getCenter();
		Direction direction = room.getDirection();
		
		// short-circuit if above floor
		if (y > room.getMinY()) return false;
		
		// want the ladder on the opposite side the room is facing ie if room faces north, want the ladder on the south side of pillar (so that the ladder still faces north)
		switch(direction) {		
		case NORTH:
			if (x == center.getX() && z == (center.getZ()+1) ) return true;
			break;
		case EAST:
			if (x == (center.getX()-1) && z == center.getZ() ) return true;
			break;
		case SOUTH:
			if (x == center.getX() && z == (center.getZ()-1) ) return true;
			break;
		case WEST:
			if (x == (center.getX()+1) && z == center.getZ() ) return true;
			break;
		default:			
		}
		return false;
	}

	//  pillar
	default public boolean isPillarElement(ICoords coords, Room room, Layout layout) {
		int x = coords.getX();
		int y = coords.getY();
		int z = coords.getZ();
		
		if (!room.hasPillar() || Math.min(room.getWidth(), room.getDepth()) < 7 || y == room.getMaxY()) return false;

		// get the x,z indexes
		int xIndex = x - room.getCoords().getX();
		int zIndex = z - room.getCoords().getZ();
		int offset = 1;
		int remainder = 0;
		
		// if the room also has pilasters, then the offset is increased so there is still space between pillar and pilaster
		if (room.hasPilaster()) {
			offset+=2;
			remainder = 1;
		}
		
		if (((xIndex > offset && xIndex < room.getWidth() - offset) && Math.abs(xIndex % 3) == remainder
				&& zIndex > offset && zIndex < room.getDepth() - offset && Math.abs(zIndex % 3) == remainder)) return true;
		return false;
	}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isPilasterElement(ICoords coords, Room room, Layout layout) {
		int x = coords.getX();
		int z = coords.getZ();
		
		// if on the floor level (min y)
		if ((room.getWidth() <= 5 || room.getDepth() <= 5) || coords.getY() == room.getMinY() || coords.getY() == room.getMaxY()) return false;
		
		// get the x,z indexes
		int xIndex = x - room.getCoords().getX();
		int zIndex = z - room.getCoords().getZ();

		// even x-z axis index (only in the corners
		if (room.getWidth() % 2 == 0 || room.getDepth() % 2 == 0) {
			if ((x == room.getMinX()+1 || x == room.getMaxX()-1) && (z == room.getMinZ()+1 || z == room.getMaxZ()-1)) return true;
			return false;
		}

		// odd x-z axis index
		if (((x == room.getMinX()+1 || x == room.getMaxX()-1) && Math.abs(zIndex % 2) == 1 && zIndex > 0 && zIndex < room.getDepth() - 1) ||
				((z == room.getMinZ()+1 || z == room.getMaxZ()-1) && Math.abs(xIndex % 2) == 1 && xIndex > 0 && xIndex < room.getWidth() - 1)) return true;

		return false;
	}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isGutterElement(ICoords coords, Room room, Layout layout) {
		int x = coords.getX();
		int z = coords.getZ();
		// if on the floor level (min y)
		if (coords.getY() == room.getMinY() &&
				// if on the inner edge of the wall and not the corners (gutter block isn't setup yet for corners)
				(((x == room.getMinX()+1 || x == room.getMaxX()-1) && z > room.getMinZ()+1 && z < room.getMaxZ()-1) ||
				((z == room.getMinZ()+1 || z == room.getMaxZ()-1) && x > room.getMinX()+1 && x < room.getMaxX()-1))
				) return true;
		return false;
	}
	
	/**
	 * 
	 * @param coords
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isGrateElement(ICoords coords, Room room, Layout layout) {
		int x = coords.getX();
		int z = coords.getZ();
		
		// if on the floor level (min y)
		if (coords.getY() != room.getMinY()) return false;
		
		if (room.hasGutter()) {
			// populate the four corners with a grate
			if ((x == room.getMinX()+1 || x == room.getMaxX()-1) && (z == room.getMinZ()+1 || z == room.getMaxZ()-1)) return true;
		}
		else {
			ICoords center = room.getCenter();
			// populate the center with a grate
			if (coords.getX() == center.getX() && coords.getZ() == center.getZ()) return true;
		}
		return false;
	}

	/**
	 * It is assumed that the element at x,y,z has already been determined to be a column. This method simply checks
	 * the vertical position to determine if this element is the base of the column. No additional checks are made to ensure
	 * that is position is indeed a column.
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isBaseElement(ICoords coords, Room room, Layout layout) {
		if (coords.getY() == room.getMinY() + 1) return true;
		return false;
	}

	/**
	 * It is assumed that the element at x,y,z has already been determined to be a column. This method simply checks
	 * the vertical position to determine if this element is the capital of the column. No additional checks are made to ensure
	 * that is position is indeed a column.
	 * @param x
	 * @param y
	 * @param z
	 * @param room
	 * @param layout
	 * @return
	 */
	default public boolean isCapitalElement(ICoords coords, Room room, Layout layout) {
		if (coords.getY() == room.getMaxY() - 1) return true;
		return false;
	}
}
