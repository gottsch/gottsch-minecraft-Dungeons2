/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.someguyssoftware.dungeons2.Dungeons2;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class StyleSheet {

	private Map<String, Theme> themes;
	private Map<String, Style> styles;
	private Map<String, Layout> layouts;
	
	/**
	 * 
	 */
	public StyleSheet () {
		styles = new LinkedHashMap<>();
		layouts = new LinkedHashMap<>();
		themes = new LinkedHashMap<>();
	}

	
	/**
	 * Checks the layout and all referenced parent layouts if it contains the element name in it's frame list.
	 * NOTE does not check against the default layout if not found.
	 * @param element
	 * @return
	 */
	public boolean hasFrame(Layout layout, DesignElement element) {
		boolean hasFrame = false;
		List<String> layoutRefs = null;
		do {
			if (layout.getFrames().containsKey(element.name())) {
				hasFrame = true;
				return hasFrame;
			}			
			else if (layout.getRef() != null) {
				// initialize if not already 
				if (layoutRefs == null) layoutRefs = new ArrayList<>(5);
				
				if (layoutRefs.contains(layout.getRef())) {
					Dungeons2.log.warn(String.format("Stylesheet layout circular dependency: %s. Using defaults.", layout.getRef()));
					break;
				}	
				if (layoutRefs.size() == 5) {
					Dungeons2.log.warn("Too many Stylesheet layout references (5 max allowed. Using defaults.");
					break;
				}				
				// update the layout refs
				layoutRefs.add(layout.getRef());
				// get the ref'ed layout				
				layout = this.getLayouts().get(layout.getRef());
			}
			else {
				layout = null;
			}
		} while (!hasFrame && layout != null);
		
		return hasFrame;
	}
		
	/**
	 * @return the styles
	 */
	public Map<String, Style> getStyles() {
		return styles;
	}

	/**
	 * @param styles the styles to set
	 */
	public void setStyles(Map<String, Style> styles) {
		this.styles = styles;
	}

	/**
	 * @return the layouts
	 */
	public Map<String, Layout> getLayouts() {
		return layouts;
	}

	/**
	 * @param layouts the layouts to set
	 */
	public void setLayouts(Map<String, Layout> layouts) {
		this.layouts = layouts;
	}

	/**
	 * @return the themes
	 */
	public Map<String, Theme> getThemes() {
		return themes;
	}

	/**
	 * @param themes the themes to set
	 */
	public void setThemes(Map<String, Theme> themes) {
		this.themes = themes;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "StyleSheet [styles=" + styles + ", layouts=" + layouts + ", themes=" + themes + "]";
	}
}
