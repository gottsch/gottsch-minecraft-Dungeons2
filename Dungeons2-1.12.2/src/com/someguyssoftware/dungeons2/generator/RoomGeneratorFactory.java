/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import java.util.List;
import java.util.Random;

import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.generator.blockprovider.BossRoomBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.CheckedFloorRoomBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.EndRoomBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.EntranceRoomBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.HallwayBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.PillarRingRoomBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.ShaftBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.SinglePillarRoomBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.StandardBlockProvider;
import com.someguyssoftware.dungeons2.generator.blockprovider.StartRoomBlockProvider;
import com.someguyssoftware.dungeons2.generator.strategy.StandardHallwayGenerationStrategy;
import com.someguyssoftware.dungeons2.generator.strategy.StandardRoomGenerationStrategy;
import com.someguyssoftware.dungeons2.generator.strategy.SupportedHallwayGenerationStrategy;
import com.someguyssoftware.dungeons2.generator.strategy.SupportedRoomGenerationStrategy;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.model.Room.Type;

/**
 * @author Mark Gottschling on Aug 28, 2016
 *
 */
public class RoomGeneratorFactory {
	Multimap<String, IRoomGenerator> registry;
	
	public RoomGeneratorFactory() {}
	
	// TODO finish this
	public RoomGeneratorFactory(Multimap<String, IRoomGenerator> registry) {
		this.registry = registry;
		/*
		 * add all standard rooms
		 */
		// add all rooms
		registry.put("standard-room", new RoomGenerator(new StandardRoomGenerationStrategy(new StandardBlockProvider())));
		registry.put("standard-room", new RoomGenerator(new StandardRoomGenerationStrategy(new PillarRingRoomBlockProvider())));
		registry.put("standard-room", new RoomGenerator(new StandardRoomGenerationStrategy(new SinglePillarRoomBlockProvider())));
		registry.put("standard-room", new RoomGenerator(new StandardRoomGenerationStrategy(new CheckedFloorRoomBlockProvider())));
		// add all entrance rooms
		registry.put("standard-entrance", new EntranceRoomGenerator(new StandardRoomGenerationStrategy(new EntranceRoomBlockProvider())));
		// add all start rooms
		
		// add all end rooms
		
		// add all boss rooms
		
		// add all treasure rooms
		
		/*
		 * add all supported rooms
		 */
		registry.put("supported-room", new RoomGenerator(new SupportedRoomGenerationStrategy(new StandardBlockProvider())));
		registry.put("supported-room", new RoomGenerator(new SupportedRoomGenerationStrategy(new PillarRingRoomBlockProvider())));
		registry.put("supported-room", new RoomGenerator(new SupportedRoomGenerationStrategy(new SinglePillarRoomBlockProvider())));
		registry.put("supported-room", new RoomGenerator(new SupportedRoomGenerationStrategy(new CheckedFloorRoomBlockProvider())));
		// add all entrance rooms
		registry.put("supported-entrance", new EntranceRoomGenerator(new SupportedRoomGenerationStrategy(new EntranceRoomBlockProvider())));

	}
	
	/**
	 * Convenience method
	 * @param room
	 * @param useSupport
	 * @return
	 */
	public IRoomGenerator createRoomGenerator(Random random, Room room, Boolean useSupport) {
		if (useSupport) {
			return createSupportedRoomGenerator(random, room);
		}
		else {
			return createStandardRoomGenerator(random, room);
		}		
	}
	
	/**
	 * 
	 * @param hallway
	 * @param rooms
	 * @param hallways A list of hallways to check against for intersection.
	 * @param useSupport
	 * @return
	 */
	public IRoomGenerator createHallwayGenerator(Hallway hallway, List<Room> rooms, List<Hallway> hallways, Boolean useSupport) {
		if (useSupport) {
			return createSupportedHallwayGenerator(hallway, rooms, hallways);
		}
		else {
			return createStandardHallwayGenerator(hallway, rooms, hallways);
		}
	}
	
	/**
	 * 
	 * @param room
	 * @param useSupport
	 * @return
	 */
	public IRoomGenerator createShaftGenerator(Room shaft, Boolean useSupport) {
		if (useSupport) {
			return createSupportedShaftGenerator(shaft);
		}
		else {
			return createStandardShaftGenerator(shaft);
		}
	}
	
	/**
	 * 
	 * @param room
	 * @return
	 */
	public IRoomGenerator createStandardRoomGenerator(Random random, Room room) {
		IRoomGenerator gen = null;
		if (room.getType() == Type.ENTRANCE) {
			gen = new EntranceRoomGenerator(new StandardRoomGenerationStrategy(new EntranceRoomBlockProvider()));
		}
		else if (room.isStart()) {
			gen = new RoomGenerator(new StandardRoomGenerationStrategy(new StartRoomBlockProvider()));
		}
		else if (room.isEnd() && room.getType() != Type.BOSS) {
			gen = new RoomGenerator(new StandardRoomGenerationStrategy(new EndRoomBlockProvider()));
		}
		else if (room.getType() == Type.BOSS) {
			gen = new BossRoomGenerator(new StandardRoomGenerationStrategy(new BossRoomBlockProvider()));
		}
		else {
			if (registry != null && registry.containsKey("standard-room")) {
				List<IRoomGenerator> list = (List<IRoomGenerator>) registry.get("standard-room");
				gen = list.get(random.nextInt(list.size()));
			}
			else {
				gen = new RoomGenerator(new StandardRoomGenerationStrategy(new StandardBlockProvider()));
			}
		}
		return gen;
	}
	
	/**
	 * 
	 * @param room
	 * @return
	 */
	public IRoomGenerator createSupportedRoomGenerator(Random random, Room room) {
		IRoomGenerator gen = null;
		if (room.getType() == Type.ENTRANCE	) {
			gen = new EntranceRoomGenerator(new SupportedRoomGenerationStrategy(new EntranceRoomBlockProvider()));
		}
		else if (room.isStart()) {
			gen = new RoomGenerator(new SupportedRoomGenerationStrategy(new StartRoomBlockProvider()));
		}
		else if (room.isEnd() && room.getType() != Type.BOSS) {
			gen = new RoomGenerator(new SupportedRoomGenerationStrategy(new EndRoomBlockProvider()));
		}
		else if (room.getType() == Type.BOSS) {
			gen = new BossRoomGenerator(new SupportedRoomGenerationStrategy(new BossRoomBlockProvider()));
		}
		else {
			if (registry != null && registry.containsKey("supported-room")) {
				List<IRoomGenerator> list = (List<IRoomGenerator>) registry.get("supported-room");
				gen = list.get(random.nextInt(list.size()));
			}
			else {
				gen = new RoomGenerator(new SupportedRoomGenerationStrategy(new StandardBlockProvider()));
			}
//			gen = new RoomGenerator(new SupportedRoomGenerationStrategy(new StandardBlockProvider()));
		}
		return gen;		
	}
	
	/**
	 * 
	 * @param hallway
	 * @param hallways 
	 * @return
	 */
	public IRoomGenerator createStandardHallwayGenerator(Hallway hallway, List<Room> rooms, List<Hallway> hallways) {
		HallwayGenerator gen = null;
		gen = new HallwayGenerator(new StandardHallwayGenerationStrategy(new HallwayBlockProvider(), rooms, hallways));
		return gen;
	}
	
	/**
	 * 
	 * @param hallway
	 * @param rooms
	 * @param hallways
	 * @return
	 */
	public IRoomGenerator createSupportedHallwayGenerator(Hallway hallway, List<Room> rooms, List<Hallway> hallways) {
		HallwayGenerator gen = new HallwayGenerator(new SupportedHallwayGenerationStrategy(new HallwayBlockProvider(), rooms, hallways));
		return gen;
	}
	
	/**
	 * 
	 * @param shaft
	 * @return
	 */
	public IRoomGenerator createStandardShaftGenerator(Room shaft) {
		IRoomGenerator gen = null;
		gen = new RoomGenerator(new StandardRoomGenerationStrategy(new ShaftBlockProvider()));
		return gen;
	}
	
	/**
	 * 
	 * @param shaft
	 * @return
	 */
	public IRoomGenerator createSupportedShaftGenerator(Room shaft) {
		IRoomGenerator gen = new RoomGenerator(new SupportedRoomGenerationStrategy(new ShaftBlockProvider()));
		return gen;
	}
}
