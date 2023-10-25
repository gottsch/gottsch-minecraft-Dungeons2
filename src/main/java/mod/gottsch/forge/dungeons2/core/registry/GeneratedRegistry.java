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
package mod.gottsch.forge.dungeons2.core.registry;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import mod.gottsch.forge.dungeons2.core.registry.support.IGeneratedContext;
import mod.gottsch.forge.gottschcore.bst.CoordsInterval;
import mod.gottsch.forge.gottschcore.bst.CoordsIntervalTree;
import mod.gottsch.forge.gottschcore.bst.IInterval;
import mod.gottsch.forge.gottschcore.enums.IRarity;
import mod.gottsch.forge.gottschcore.spatial.ICoords;

/**
 * This Registry is a non-Singleton.
 * 
 * @author Mark Gottschling on Jan 22, 2018
 *
 */
public class GeneratedRegistry<T extends IGeneratedContext> {
	
	/*
	 * a Interval BST registry to determine the proximity of generated dungeons, fortresses.
	 * 
	 */
	private final CoordsIntervalTree<T> distanceRegistry;
	/*
	 * a Linked List registry to maintain descending age of insertion of dungeons
	 */
	private final LinkedList<T> ageRegistry;
	/*
	 * a Table registry for rarity/key lookups
	 */
	// TODO could reintroduce this for ISize instead of IRarity
//	private final Table<IRarity, String, T> tableRegistry;	
	private final Map<ICoords, T> coordsRegistry;
	
	private int registryMaxSize;
	
	/**
	 * 
	 */
	public GeneratedRegistry() {
		distanceRegistry = new CoordsIntervalTree<>();
		ageRegistry = new LinkedList<>();
		coordsRegistry = Maps.newHashMap();
//		tableRegistry = Tables.newCustomTable(new LinkedHashMap<>(), LinkedHashMap::new);
	}
	
	/**
	 * 
	 * @param size
	 */
	public GeneratedRegistry(int size) {
		this();
		this.registryMaxSize = size;
	}
	
//	public boolean isRegistered(final IRarity rarity, final String key) {
//		return tableRegistry.contains(rarity, key);
//	}
//	
//	public boolean hasIRarity(final IRarity rarity) {
//		return tableRegistry.containsColumn(rarity);
//	}
	
	/**
	 * 
	 * @param start
	 * @param context
	 */
	public synchronized void register(/*final IRarity rarity, */final ICoords start, final ICoords end, final T context) {
		// if bigger than max size of registry, remove the first (oldest) element
		if (ageRegistry.size() > getRegistryMaxSize()) {
			unregisterFirst();
		}
		distanceRegistry.insert(new CoordsInterval<>(start.withY(0), end.withY(0), context));
		ageRegistry.add(context);
		coordsRegistry.put(start, context); // TODO this probably will = context.getEntranceCoords();
//		tableRegistry.put(rarity, key.toShortString(), context);
	}
	
	/**
	 * 
	 */
	public synchronized void unregisterFirst() {
		T removeGenContext = ageRegistry.pollFirst();
		if (removeGenContext != null) {
//			if (tableRegistry.contains(removeGenContext.getRarity(), removeGenContext.getCoords().toShortString())) {
//				tableRegistry.remove(removeGenContext.getRarity(), removeGenContext.getCoords().toShortString());
//			}
			distanceRegistry.delete(new CoordsInterval<>(removeGenContext.getCoords(), removeGenContext.getCoords(), null));
			coordsRegistry.remove(removeGenContext.getCoords());
		}
	}
	
	/**
	 * 
	 * @param key
	 * @param rarity
	 */
	public synchronized void unregister(final IRarity rarity, final ICoords key) {
		if (coordsRegistry.containsKey(key)) {
//		if (tableRegistry.contains(rarity, key.toShortString())) {
//			T genContext = tableRegistry.remove(rarity, key.toShortString());
			T genContext = coordsRegistry.remove(key);
			if (genContext != null) {
				ageRegistry.remove(genContext);
				distanceRegistry.delete(new CoordsInterval<>(key, key.add(1, 0, 1 ), null));
			}
		}
	}
	
	/**
	 * 
	 * @param genContext
	 */
	public synchronized void unregister(T genContext) {
		ageRegistry.remove(genContext);
//		tableRegistry.remove(genContext.getRarity(), genContext.getCoords().toShortString());
		coordsRegistry.remove(genContext.getCoords());
		distanceRegistry.delete(new CoordsInterval<>(genContext.getCoords(), genContext.getCoords(), null));
	}
	
	/**
	 * 
	 * @param rarity
	 * @param key
	 * @return
	 */
//	public Optional<T> get(IRarity rarity, String key) {
//		if (tableRegistry.contains(rarity, key)) {
//			return Optional.of(tableRegistry.get(rarity, key));
//		}
//		return Optional.empty();
//	}
	
	// Optional
//	public Optional<List<T>> getByIRarity(IRarity rarity) {
//		if (tableRegistry.containsRow(rarity)) {
//			Treasure.LOGGER.debug("table registry contains rarity -> {}", rarity);
//			Map<String, T> infoMap = tableRegistry.row(rarity);			
//			Treasure.LOGGER.debug("chest infos size -> {}", infoMap.size());
//			return Optional.of(infoMap.values().stream().collect(Collectors.toList()));
//		}
//		return Optional.empty();
//	}

	
	/**
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	public boolean withinArea(ICoords start, ICoords end) {
		List<IInterval<T>> overlaps = distanceRegistry.getOverlapping(distanceRegistry.getRoot(), new CoordsInterval<>(start, end));
		if (overlaps.isEmpty()) {
			return false;
		}
		return true;
	}
	
	/**
	 * This will not update parent collection.
	 * @return
	 */
	public List<T> getValues() {
		return ageRegistry;
	}
	
	public void clear() {
		ageRegistry.clear();
//		tableRegistry.clear();
		coordsRegistry.clear();
		distanceRegistry.clear();
	}

	public int getRegistryMaxSize() {
		return registryMaxSize;
	}
}
