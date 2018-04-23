/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import com.someguyssoftware.dungeons2.generator.Location;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Jan 11, 2017
 *
 */
public interface IRoomDecorator {

	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param config
	 */
	void decorate(World world, Random random, IDungeonsBlockProvider provider, Room room, LevelConfig config);

	/**
	 * NOTE This is a STATELESS method ie blocks that don't have a specific state to be in, like WEB.
	 * This won't work with Blocks that use FACING etc.
	 * Adds a random number of specified blocks as decorations to the room.
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param zone
	 * @param block
	 * @param frequency
	 * @param number
	 * @param config
	 */
	default public void addBlock(final World world, Random random, final IDungeonsBlockProvider provider,
			final Room room, final List<Entry<DesignElement, ICoords>> zone, final IBlockState[] states, 
			final Quantity frequency, final Quantity number, final LevelConfig config) {
		
		IBlockState state = null;
		double freq = RandomHelper.randomDouble(random, frequency.getMin(), frequency.getMax());
		int scaledNum = scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, number.getMinInt(), number.getMaxInt()), config);
		
		for (int i = 0; i < scaledNum; i++) {
			double n = random.nextDouble() * 100;
			if (n < freq && zone.size() > 0) {
				// select ANY zone
				int zoneIndex = random.nextInt(zone.size());
				Entry<DesignElement, ICoords> entry = zone.get(zoneIndex);
				DesignElement elem = zone.get(zoneIndex).getKey();
				ICoords coords = entry.getValue();
				// check if the adjoining block exists
				if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
					// select a block
					if (states.length==1) state = states[0];
					else state = states[random.nextInt(states.length)];					
					// update the world
					world.setBlockState(coords.toPos(), state, 3);	
					// remove location from airZone
					zone.remove(entry);
				}
			}
		}		
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @param elem
	 * @param location
	 * @return
	 */
	default public boolean hasSupport(World world, ICoords coords, DesignElement elem, Location location) {
		BlockPos pos = coords.toPos();
		IBlockState blockState = null;
		Block block = null;
		if (elem == DesignElement.FLOOR_AIR) {
			// check below
			blockState = world.getBlockState(pos.add(0, -1, 0));
		}
		else if (elem == DesignElement.CEILING_AIR) {
			// check above
			blockState = world.getBlockState(pos.add(0, 1, 0));
		}
		else if (elem == DesignElement.WALL_AIR) {
			switch (location) {
			case NORTH_SIDE:
				blockState = world.getBlockState(pos.add(0, 0, -1));
				break;
			case EAST_SIDE:
				blockState = world.getBlockState(pos.add(1, 0, 0));
				break;
			case SOUTH_SIDE:
				blockState = world.getBlockState(pos.add(0, 0, 1));
				break;
			case WEST_SIDE:
				blockState = world.getBlockState(pos.add(-1, 0, 0));
				break;
			default:
				break;
			}			
		}		

		if (blockState != null) {
			block = blockState.getBlock();
			if ((!block.isAir(blockState, world, pos) && !block.isLeaves(blockState, world, pos) &&
					!block.isFoliage(world, pos)) && !blockState.getMaterial().isReplaceable()) {
				return true;
			}	
		}
		return false;
	}
	
	/**
	 * Depending on the size of the room, scale the number of decorations.
	 * Anything bigger than half the room size gets full amount, under half scales downward.
	 * @param room
	 * 	@param numDecorations
	 * @param config
	 * @return
	 */
	default public int scaleNumForSizeOfRoom(Room room, int numDecorations, LevelConfig config) {
		int size = (room.getWidth()-2) * (room.getDepth()-2) * (room.getHeight()-2);
		int halfOfMax = ((config.getWidth().getMaxInt()-2) * (config.getDepth().getMaxInt()-2) * (config.getHeight().getMaxInt()-2))/2;
		float factor = 1F;
		
		if (size <= 27) factor = 0.25F;
		else if (size < halfOfMax) factor = 0.5F;		
		
		int num = (int) (numDecorations * factor);

		return num;
	}
	/**
	 * 
	 * @param location
	 * @return
	 */
	default public EnumFacing orientChest(Location location) {
		EnumFacing facing = null;
		switch(location) {
		case NORTH_SIDE:
			facing = EnumFacing.SOUTH;
			break;
		case SOUTH_SIDE:
			facing = EnumFacing.NORTH;
			break;
		case EAST_SIDE:
			facing = EnumFacing.WEST;
			break;
		case WEST_SIDE:
			facing = EnumFacing.EAST;
			break;
		default:
			facing = EnumFacing.NORTH;
		}
		return facing;
	}
}