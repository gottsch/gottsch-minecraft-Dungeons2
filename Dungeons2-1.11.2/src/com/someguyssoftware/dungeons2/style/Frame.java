/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class Frame {
	private String alias;
	private String style;

	public Frame() {}

	/**
	 * 
	 * @param alias
	 * @param style
	 */
	public Frame(String alias, String style) {
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
	public Frame setAlias(String alias) {
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
	public Frame setStyle(String style) {
		this.style = style;
		return this;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Frame [alias=" + alias + ", style=" + style + "]";
	}
}
