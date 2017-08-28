/**
 * 
 */
package com.someguyssoftware.dungeons2printer;

import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Aug 27, 2017
 *
 */
public interface IPrettyPrinter {

	default public String toString(Quantity q) {
		return q.getMin() + " <--> " + q.getMax();
	}
}
