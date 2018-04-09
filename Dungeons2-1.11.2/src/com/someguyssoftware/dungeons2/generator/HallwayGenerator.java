/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import java.util.Random;

import com.someguyssoftware.dungeons2.generator.strategy.IRoomGenerationStrategy;
import com.someguyssoftware.dungeons2.model.Door;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.mod.ICoords;
import com.someguyssoftware.mod.enums.Alignment;
import com.someguyssoftware.mod.enums.Direction;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Aug 28, 2016
 *
 */
public class HallwayGenerator extends AbstractRoomGenerator {

	private IRoomGenerationStrategy roomGenerationStrategy;

	/**
	 * Enforce that the room generator has to have a structure generator.
	 * @param generator
	 */
	public HallwayGenerator(IRoomGenerationStrategy generator) {
		setGenerationStrategy(generator);
	}

	@Override
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet,
			LevelConfig config) {

		// generate the room structure
		getGenerationStrategy().generate(world, random, room, theme, styleSheet, config);

		/*
		 *  add additional elements
		 */
		Hallway hw = (Hallway)room;
		// build the doors
		for (Door door : hw.getDoors()) {
			if (hw.getAlignment() == Alignment.HORIZONTAL) {
				// test which side the door is on
				if (door.getCoords().getX() == hw.getMinX()) {
					if (door.getCoords().getZ() == door.getRoom().getMinZ()) {
						buildDoorway(world, door.getCoords(), Direction.WEST, Direction.SOUTH);
					}
					else if (door.getCoords().getZ() == door.getRoom().getMaxZ()) {
						buildDoorway(world, door.getCoords(), Direction.WEST, Direction.NORTH);
					}
					else {
						buildDoorway(world, door.getCoords(), Direction.WEST);
					}
				}
				if (door.getCoords().getX() == hw.getMaxX()) {
					if (door.getCoords().getZ() == door.getRoom().getMinZ()) {
						buildDoorway(world, door.getCoords(), Direction.EAST, Direction.SOUTH);
					}
					else if (door.getCoords().getZ() == door.getRoom().getMaxZ()) {
						buildDoorway(world, door.getCoords(), Direction.EAST, Direction.NORTH);
					}
					else {				
						buildDoorway(world, door.getCoords(), Direction.EAST);
					}
				}
			}
			// VERTICAL (NORTH/SOUTH)
			else {

				if (door.getCoords().getZ() == hw.getMinZ()) {
					if (door.getCoords().getX() == door.getRoom().getMinX()) {
						buildDoorway(world, door.getCoords(), Direction.NORTH, Direction.EAST);
					}
					else if (door.getCoords().getX() == door.getRoom().getMaxX()) {
						buildDoorway(world, door.getCoords(), Direction.NORTH, Direction.WEST);					
					}
					else {
						buildDoorway(world, door.getCoords(), Direction.NORTH);
					}
				}
				if (door.getCoords().getZ() == hw.getMaxZ()) {
					if (door.getCoords().getX() == door.getRoom().getMinX()) {
						buildDoorway(world, door.getCoords(), Direction.SOUTH, Direction.EAST);				
					}
					else if (door.getCoords().getX() == door.getRoom().getMaxX()) {
						buildDoorway(world, door.getCoords(), Direction.SOUTH, Direction.WEST);
					}
					else {
						buildDoorway(world, door.getCoords(), Direction.SOUTH);
					}
				}
			}
		}
	}

	/**
	 * A special case of doorway that needs to be generated when the wayline/hallway
	 *  connects to a room parallel with the room's wall.
	 * @param world
	 * @param coords
	 * @param west
	 * @param south
	 */
	protected void buildDoorway(World world, ICoords coords, Direction direction, Direction doubleSide) {
		int touching = 0;
		int x =0;
		int z = 0;
		int dx = 0;
		int dz = 0;
		int failSafe = 0;

		// setup the side that double side will remove
		switch(doubleSide) {
		case NORTH:
			dz--;
			break;
		case EAST:
			dx++;
			break;
		case SOUTH:
			dz++;
			break;
		case WEST:
			dx--;
			break;
		default:
		}

		do {
			touching = 0;

			// carve "regular" doorway
			world.setBlockState(coords.add(x, 1, z).toBlockPos(), Blocks.AIR.getDefaultState(), 3);
			world.setBlockState(coords.add(x, 2, z).toBlockPos(), Blocks.AIR.getDefaultState(), 3);
			// carve double-side doorway
			world.setBlockState(coords.add(dx, 1, dz).toBlockPos(), Blocks.AIR.getDefaultState(), 3);
			world.setBlockState(coords.add(dx, 2, dz).toBlockPos(), Blocks.AIR.getDefaultState(), 3);

			// check in all four directions and add 1 to value
			BlockPos pos = coords.add(dx, 1, dz).toBlockPos();
			if (world.getBlockState(pos.north()) == Blocks.AIR.getDefaultState()) touching++;
			if (world.getBlockState(pos.south()) == Blocks.AIR.getDefaultState()) touching++;
			if (world.getBlockState(pos.east()) == Blocks.AIR.getDefaultState()) touching++;
			if (world.getBlockState(pos.west()) == Blocks.AIR.getDefaultState()) touching++;

			// move to next block
			switch(direction) {
			case NORTH:
				z--;
				dz--;
				break;
			case EAST:
				x++;
				dx++;
				break;
			case SOUTH:
				z++;
				dz++;
				break;
			case WEST:
				x--;
				dx--;
				break;
			default:
			}
			failSafe++;
		} while (touching < 3 && failSafe < 5);		
	}

	/**
	 * @return the roomGenerationStrategy
	 */
	public IRoomGenerationStrategy getGenerationStrategy() {
		return roomGenerationStrategy;
	}

	/**
	 * @param roomGenerationStrategy the roomGenerationStrategy to set
	 */
	public void setGenerationStrategy(IRoomGenerationStrategy roomGenerationStrategy) {
		this.roomGenerationStrategy = roomGenerationStrategy;
	}

}
