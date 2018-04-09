/**
 * 
 */
package com.someguyssoftware.gottschcore.random;

import java.util.Random;

/**
 * @author Mark Gottschling on May 6, 2017
 *
 */
public final class RandomHelper {
	private static int INT_MAX_PROB = 100;
	private static double DOUBLE_MAX_PROB = 100.0D;
	
	/**
	 * 
	 * @param min
	 * @param max
	 * @return
	 */
	public static int randomInt(final int min, final int max) {
		final Random random = new Random();
		return randomInt(random, min, max);
	}
	
	/**
	 * 
	 * @param random
	 * @param min
	 * @param max
	 * @return
	 */
	public static int randomInt(final Random random, final int min, final int max) {
		if (min == max) {
			return min;
		}
		
		if (min > max) {
			return random.nextInt(min) + 1;
		}
		return random.nextInt(max - min  + 1) + min;
	}
	
	/**
	 * 
	 * @param min
	 * @param max
	 * @return
	 */
	public static double randomDouble(final double min, final double max) {
		final Random random = new Random();
		return randomDouble(random, min, max);
	}
	
	/**
	 * 
	 * @param random
	 * @param min
	 * @param max
	 * @return
	 */
	public static double randomDouble(final Random random, final double min, final double max) {
		if (min == max) {
			return min;
		}
		
		if (min > max) {
			return random.nextDouble()*min;
		}
		return random.nextDouble()*(max - min) + min;
	}
	
	/**
	 * 
	 * @param random
	 * @param probability
	 * @return
	 */
	public static boolean checkProbability(final Random random, final int probability) {
		Random r = null;
		r = (random == null) ? new Random() : random;
		if (r.nextInt(INT_MAX_PROB) < probability) {
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param random
	 * @param probability
	 * @return
	 */
	public static boolean checkProbability(final Random random, final double probability) {
		Random r = null;
		r = (random == null) ? new Random() : random;
		if (r.nextDouble() * DOUBLE_MAX_PROB < probability) {
			return true;
		}
		return false;
	}
}
