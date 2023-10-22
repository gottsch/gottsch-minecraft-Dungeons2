package com.someguyssoftware.dungeons2.cache;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.model.DungeonInfo;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;


/**
 * 
 * @author Mark Gottschling on Oct 22, 2023
 *
 */
public class FeatureCaches {
	private static final String CACHE_NAME = "dungeonCache";
	private static final String DELAY_KEY = "delay";
	private static final String DATA_KEY = "data";

	// TODO add values to config
	public static final DelayedFeatureSimpleDistanceCache<DungeonInfo> CACHE = new DelayedFeatureSimpleDistanceCache<>(500);
	
	/**
	 * 
	 */
	private FeatureCaches() {}
	
	/**
	 * 
	 */
	public static void clear() {
		CACHE.clear();
	}
	
	/**
	 * 
	 * @return
	 */
	public static NBTTagCompound save() {
		NBTTagCompound tag = new NBTTagCompound();

		NBTTagCompound dungeonTag = saveCache(CACHE);
		tag.setTag(CACHE_NAME, dungeonTag);
//		Dungeons2.log.debug("save well cache dump -> {}", CACHE.dump());
		return tag;
	}
	
	/**
	 * 
	 * @param tag
	 */
	public static void load(NBTTagCompound tag) {
		if (tag.hasKey(CACHE_NAME)) {
			CACHE.clear();
			loadCache((NBTTagCompound)tag.getTag(CACHE_NAME), CACHE);
//			Dungeons2.log.debug("load well cache dump -> {}", CACHE.dump());
		}
	}
	
	/**
	 * 
	 * @param delayedFeatureCache
	 * @return
	 */
	private static NBTTagCompound saveCache(DelayedFeatureSimpleDistanceCache<DungeonInfo> delayedFeatureCache) {
		
		NBTTagCompound featureTag = new NBTTagCompound();
		featureTag.setInteger(DELAY_KEY, delayedFeatureCache.getDelayCount());
		
		NBTTagList dataTag = new NBTTagList();
		delayedFeatureCache.getValues().forEach(datum -> {
			NBTTagCompound datumTag = datum.save();
			// add entry to list
			dataTag.appendTag(datumTag);
		});	
		featureTag.setTag(DATA_KEY, dataTag);
		return featureTag;
	}
	
	/**
	 * 
	 * @param featureTag
	 * @param delayedFeatureCache
	 * @return
	 */
	private static NBTTagCompound loadCache(NBTTagCompound featureTag, DelayedFeatureSimpleDistanceCache<DungeonInfo> delayedFeatureCache) {		
		int delay = 0;
		if (featureTag.hasKey(DELAY_KEY)) {
			delay = featureTag.getInteger(DELAY_KEY);
		}
		delayedFeatureCache.setDelayCount(delay);
		
		if (featureTag.hasKey(DATA_KEY)) {
			NBTTagList dataTag = featureTag.getTagList(DATA_KEY, 10);
			dataTag.forEach(datum -> {
				DungeonInfo info = new DungeonInfo();
				// extract the data
				info.load((NBTTagCompound)datum);
				Dungeons2.log.debug("dungeon info -> {}", info);
				// register the info
				delayedFeatureCache.cache(info.getCoords(), info);
			});
		}		
		return featureTag;
	}
	
//	/**
//	 * 
//	 * @param cacheMap
//	 * @return
//	 */
//	public static ListTag saveCache(Map<ResourceLocation, SimpleDistanceCache<GeneratedContext>> cacheMap) {
////		ListTag dimensionalCachesTag = new ListTag();
////		cacheMap.forEach((dimension, cache) -> {
//			NBTTagCompound dimensionCacheTag = new NBTTagCompound();
//			dimensionCacheTag.putString(DIMENSION_NAME, dimension.toString());
//			ListTag dataTag = new ListTag();
//			cache.getValues().forEach(datum -> {
//				NBTTagCompound datumTag = datum.save();
//				// add entry to list
//				dataTag.add(datumTag);	
//			});			
//			dimensionCacheTag.put("data", dataTag);
//			dimensionalCachesTag.add(dimensionCacheTag);
//		});
//		return dimensionalCachesTag;
//	}
	
//	/**
//	 * 
//	 * @param <T>
//	 * @param dimensionalCachesTag
//	 * @param cacheMap
//	 * @param supplier
//	 */
//	public static <T> void loadCache(ListTag dimensionalCachesTag, 
//			Map<ResourceLocation, SimpleDistanceCache<GeneratedContext>> cacheMap, Supplier<GeneratedContext> supplier) {
//
//		if (dimensionalCachesTag != null) {
//			Dungeons2.log.debug("loading well caches...");  	
//			dimensionalCachesTag.forEach(dimensionalCacheTag -> {
//				NBTTagCompound dimensionalCacheCompound = (NBTTagCompound)dimensionalCacheTag;
//				if (dimensionalCacheCompound.contains(DIMENSION_NAME)) {
//					String dimensionName = dimensionalCacheCompound.getString(DIMENSION_NAME);
//					Dungeons2.log.debug("loading dimension -> {}", dimensionName);
//					// load the data
//					if (dimensionalCacheCompound.contains("data")) {
//						ResourceLocation dimension = ModUtil.asLocation(dimensionName);					
//						// add the dimension if it doesn't exist
//						if (!cacheMap.containsKey(dimension)) {
//							cacheMap.put(dimension, new SimpleDistanceCache<GeneratedContext>(Config.SERVER.wells.cacheSize.get()));
//						}
//						
//						ListTag dataTag = dimensionalCacheCompound.getList("data", Tag.TAG_COMPOUND);
//						dataTag.forEach(datum -> {
//							GeneratedContext context = supplier.get();
//							// extract the data
//							context.load((NBTTagCompound)datum);
//							Dungeons2.log.debug("context -> {}", context);
//							if (context.getRarity() != null && context.getCoords() != null) {									
//								cacheMap.get(dimension).cache(context.getCoords(), context);
//							}
//						});
//					}
//				}
//			});
//		}            		
//	}
}
