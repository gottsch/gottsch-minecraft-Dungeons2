/**
 * 
 */
package com.someguyssoftware.gottschcore.annotation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Target;

@Target(TYPE)
/**
 * @author Mark Gottschling on May 4, 2017
 *
 */
public @interface Credits {
	String[] values();
}
