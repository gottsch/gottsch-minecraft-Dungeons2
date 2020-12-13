/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class Theme {
	private String name;
	private Map<String, Alias> aliases;
	
	public Theme() {
		aliases = new HashMap<>();
	}
	
	/**
	 * 
	 * @param NAME
	 */
	public Theme(String name) {
		this();
		this.name = name;
	}

	/**
	 * @return the NAME
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param NAME the NAME to set
	 */
	public Theme setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * @return the aliases
	 */
	public Map<String, Alias> getAliases() {
		return aliases;
	}

	/**
	 * @param aliases the aliases to set
	 */
	public void setAliases(Map<String, Alias> aliases) {
		this.aliases = aliases;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Theme [NAME=" + name + ", aliases=" + aliases + "]";
	}
}
