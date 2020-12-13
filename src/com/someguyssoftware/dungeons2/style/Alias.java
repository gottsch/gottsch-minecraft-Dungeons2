/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class Alias {
	private String alias;
	private String style;

	public Alias() {}
	
	/**
	 * 
	 * @param alias
	 * @param styel
	 */
	public Alias(String alias, String style) {
		this.alias = alias;
		this.style = style;
	}

	/**
	 * @return the alias
	 */
	public String getAlias() {
		return alias;
	}

	/**
	 * @param alias the alias to set
	 */
	public Alias setAlias(String alias) {
		this.alias = alias;
		return this;
	}

	/**
	 * @return the style
	 */
	public String getStyle() {
		return style;
	}

	/**
	 * @param style the style to set
	 */
	public Alias setStyle(String style) {
		this.style = style;
		return this;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Alias [alias=" + alias + ", style=" + style + "]";
	}
}
