/*
 * This file is part of  Treasure2.
 * Copyright (c) 2022 Mark Gottschling (gottsch)
 *
 * Treasure2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Treasure2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Treasure2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.registry;

import com.google.common.collect.Maps;
import mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns;
import mod.gottsch.forge.dungeons2.core.enums.IPatternEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry to register all enums that are expandable ie IPatternEnum.
 * @author Mark Gottschling on Mar 3, 2024
 *
 */
public class PatternRegistry {
	public static final Map<String, Map<String, IPatternEnum>> ENUMS_MAP = Maps.newHashMap();
	
	/**
	 * 
	 * @param key the name key for the enum set. ex "rarity" for the Rarity enum.
	 * @param ienum
	 */
	public static void register(String key, IPatternEnum ienum) {
		Map<String, IPatternEnum> map = null;
		if (!ENUMS_MAP.containsKey(key)) {
			// create a new inner map
			map = Maps.newHashMap();
			// add inner map to enums map
			ENUMS_MAP.put(key, map);
		}
		else {
			map = ENUMS_MAP.get(key);
		}
		// add enum to inner map
		if (!map.containsKey(ienum.getName().toUpperCase())) {
			map.put(ienum.getName(), ienum);
		}
	}
	
	/**
	 * 
	 * @param key
	 * @param ienum
	 * @return
	 */
	public static boolean isRegistered(String key, IPatternEnum ienum) {
		if (ENUMS_MAP.containsKey(key)) {
			if (ENUMS_MAP.get(key).containsKey(ienum.getName().toUpperCase())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 * @param key
	 * @param enumKey
	 * @return
	 */
	public static Optional<IPatternEnum> get(String key, String enumKey) {
		if (ENUMS_MAP.containsKey(key)) {
			if (ENUMS_MAP.get(key).containsKey(enumKey.toUpperCase())) {
				return Optional.of(ENUMS_MAP.get(key).get(enumKey.toUpperCase()));
			}
		}
		return Optional.empty();
	}

	public static List<IPatternEnum> getValues(String key) {
		if (ENUMS_MAP.containsKey(key)) {
			return new ArrayList<>(ENUMS_MAP.get(key).values());
		}
		return new ArrayList<>();
	}

	/**
	 * Convenience method.
	 * @param key
	 * @return
	 */
	public static Optional<IPatternEnum> getFloorPattern(String key) {
		return get(DungeonRoomPatterns.FLOOR_PATTERN, key);
	}
}
