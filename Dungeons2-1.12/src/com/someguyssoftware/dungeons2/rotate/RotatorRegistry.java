/**
 * 
 */
package com.someguyssoftware.dungeons2.rotate;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.Dungeons2;

import net.minecraftforge.fml.common.Mod;

/**
 * 
 * @author Mark Gottschling on Aug 4, 2016
 *
 */
public class RotatorRegistry {
	private Multimap<String, String> security;
	private Map<String, IRotator> registry;
	private static RotatorRegistry instance = new RotatorRegistry();
	
	/**
	 * 
	 */
	private RotatorRegistry() {
		security = ArrayListMultimap.create();
		registry = new HashMap<>();
	}
	
	/**
	 * 
	 * @return
	 */
	public static RotatorRegistry getInstance() {
		return instance;
	}
	
	/**
	 * 
	 * @param blockClass the block class to register a rotator with
	 * @param rotator
	 * @param modInstance
	 */
	public void registerBlockRotator(Class<?> blockClass, IRotator rotator, Object modInstance) {
		// check if Object is annotated with @Mod
		if (modInstance.getClass().isAnnotationPresent(Mod.class)) {
			// check if registry contains rotator
			if (!registry.containsKey(blockClass.getName())) {
				Dungeons2.log.debug("Registering Rotator for "  + blockClass.getName());
				registry.put(blockClass.getName(), rotator);
				
				String securityKey = null;
				// get the @Mod annotation
				Mod mod = modInstance.getClass().getAnnotation(Mod.class);
				if (mod != null) {
					securityKey = mod.modid();
				}
				else {
					securityKey = modInstance.getClass().getName();
				}
				// registry the rotator with the security multi map
				security.put(securityKey, blockClass.getName());
			}
			else {
				Dungeons2.log.warn("The class has already been registered.");
			}
		}
		else {
			Dungeons2.log.warn("Unable to register BlockRotator; mod instance object does not have the proper permissions.");
		}
	}
	
	/**
	 * 
	 * @param clazz
	 * @param rotator
	 */
	private void registerBlockRotator(Class<?> clazz, IRotator rotator) {
		if (!registry.containsKey(clazz.getName())) {
			Dungeons2.log.debug("Registering Rotator for "  + clazz.getName());
			registry.put(clazz.getName(), rotator);
		}
	}
	
	/**
	 * 
	 * @param clazz
	 * @param mod
	 */
	public void unregister(Class<?> clazz, Object modInstance) {
		// check if Object is annotated with @Mod or instance of PlansBuilder
		if (modInstance.getClass().isAnnotationPresent(Mod.class)) {
			// check if registry contains the key
			if(registry.containsKey(clazz.getName())) {
				String securityKey = null;
				// get the @Mod annotation
				Mod mod = modInstance.getClass().getAnnotation(Mod.class);
				if (mod != null) {
					securityKey = mod.modid();
				}
				else {
					securityKey = modInstance.getClass().getName();
				}
				// check security if the class is registered to the mod instance
				if (security.get(securityKey).contains(clazz.getName())) {
					// remove class from the registry
					registry.remove(clazz.getName());
					// remove class from the security
					security.remove(securityKey, clazz);
				}
				else {
					Dungeons2.log.warn("Unable to unregister BlockRotator; mod instance object does not have the proper permissions.");
				}
			}
			else {
				Dungeons2.log.warn("The class is not registered with a BlockRotator");
			}
		}
		else {
			Dungeons2.log.warn("Unable to unregister BlockRotator; mod instance object does not have the proper permissions.");
		}	
	}
	
	/**
	 * 
	 * @param clazz
	 */
	private void unregister(Class<?> clazz) {
		if (registry.containsKey(clazz.getName())) {
			registry.remove(clazz.getName());
		}
	}
	
	/**
	 * 
	 * @param clazz
	 * @return
	 */
	public IRotator get(Class<?> clazz) {
		if (registry.containsKey(clazz.getName())) {
			return registry.get(clazz.getName());
		}
		return null;
	}
	
	public boolean has(Class<?> clazz) {
		return registry.containsKey(clazz.getName());
	}
}
