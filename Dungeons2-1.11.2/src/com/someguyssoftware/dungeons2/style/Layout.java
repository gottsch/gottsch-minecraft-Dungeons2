/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public class Layout {
	public static final String DEFAULT_NAME = "default";

	private String ref;
	private boolean useAll;
	private String category;
	private String name;
	private Map<String, Frame> frames;
	/*
	 * this is only here so that a comment can be added to the json without any special processing
	 */
	transient protected String __comment;
	
	public Layout () {
		frames = new HashMap<>();
	}

	/**
	 * 
	 * @param name
	 */
	public Layout(String name) {
		this();
		this.name = name;
	}
	
	/**
	 * NOTE this is a lot of processing to do on each room
	 * @return
	 */
	@Deprecated
	public boolean isInteriorComplete() {
		// collect all the interior design elements in the layout
		List<String> elemNames = frames.keySet().stream()
				.filter(x -> (DesignElement.getByValue(x).getFace() == Face.INTERIOR ||
						DesignElement.getByValue(x).getFace() == Face.BOTH))
				.collect(Collectors.toList());

		// collect all the interior design elements in the enum set
		List<DesignElement> enums = EnumSet.allOf(DesignElement.class).stream()
				.filter(x -> (x.getFace() == Face.INTERIOR ||
						x.getFace() == Face.BOTH))
				.collect(Collectors.toList());
		
		// compare the lists
		for (DesignElement de : enums) {
			if (!elemNames.contains(de.name())) return false;
		}
		return true;
	}
	
	/**
	 * 
	 * @author Mark Gottschling on Aug 20, 2016
	 *
	 */
	public enum Category {
		DEFAULT("default");

		private String name;
		
		/**
		 * 
		 * @param name
		 */
		Category(String name) {
			this.name = name;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @param name the name to set
		 */
		public void setName(String name) {
			this.name = name;
		}
	}
	
	public enum Type {
		ENTRANCE("entrance"),
		START("start"),
		END("end"),
		ROOM("room"),
		HALLWAY("hallway"),
		BOSS("boss"),
		TREASURE("treasure");

		private String name;
		
		/**
		 * 
		 * @param name
		 */
		Type(String name) {
			this.name = name;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @param name the name to set
		 */
		public void setName(String name) {
			this.name = name;
		}
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public Layout setName(String name) {
		this.name = name;
		return this;
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Layout [name=" + name + ", frames=" + frames + "]";
	}

	/**
	 * @return the frames
	 */
	public Map<String, Frame> getFrames() {
		return frames;
	}

	/**
	 * @param frames the frames to set
	 */
	public void setFrames(Map<String, Frame> frames) {
		this.frames = frames;
	}

	/**
	 * @return the category
	 */
	public String getCategory() {
		return category;
	}

	/**
	 * @param category the category to set
	 */
	public void setCategory(String category) {
		this.category = category;
	}

	/**
	 * @return the useAll
	 */
	public boolean isUseAll() {
		return useAll;
	}

	/**
	 * @param useAll the useAll to set
	 */
	public void setUseAll(boolean useAll) {
		this.useAll = useAll;
	}

	/**
	 * @return the ref
	 */
	public String getRef() {
		return ref;
	}

	/**
	 * @param ref the ref to set
	 */
	public void setRef(String ref) {
		this.ref = ref;
	}
	
	/**
	 * 
	 * @return
	 */
	public String getComment() {
		return this.__comment;
	}
	
	/**
	 * 
	 * @param comment
	 */
	public void setComment(String comment) {
		this.__comment = comment;
	}
	
}
