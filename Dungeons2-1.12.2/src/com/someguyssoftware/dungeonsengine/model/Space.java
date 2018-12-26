/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Dec 25, 2018
 *
 */
public class Space implements ISpace {

	/**
	 * 
	 */
	public Space() {
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#copy()
	 */
	@Override
	public ISpace copy() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getId()
	 */
	@Override
	public int getId() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setId(int)
	 */
	@Override
	public void setId(int id) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getName()
	 */
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setName(java.lang.String)
	 */
	@Override
	public void setName(String name) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getCoords()
	 */
	@Override
	public ICoords getCoords() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setCoords(com.someguyssoftware.gottschcore.positional.ICoords)
	 */
	@Override
	public ISpace setCoords(ICoords coords) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDepth()
	 */
	@Override
	public int getDepth() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setDepth(int)
	 */
	@Override
	public ISpace setDepth(int depth) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getWidth()
	 */
	@Override
	public int getWidth() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setWidth(int)
	 */
	@Override
	public ISpace setWidth(int width) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getHeight()
	 */
	@Override
	public int getHeight() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setHeight(int)
	 */
	@Override
	public ISpace setHeight(int height) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDirection()
	 */
	@Override
	public Direction getDirection() {
		// TODO Auto-generated method stub
		return null;
	}	

	@Override
	public ISpace setDirection(Direction direction) {
		return null;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDegrees()
	 */
	@Override
	public int getDegrees() {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setDegrees(int)
	 */
	@Override
	public ISpace setDegrees(int degrees) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isAnchor()
	 */
	@Override
	public boolean isAnchor() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setAnchor(boolean)
	 */
	@Override
	public ISpace setAnchor(boolean anchor) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isStart()
	 */
	@Override
	public boolean isStart() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setStart(boolean)
	 */
	@Override
	public ISpace setStart(boolean start) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isEnd()
	 */
	@Override
	public boolean isEnd() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setEnd(boolean)
	 */
	@Override
	public ISpace setEnd(boolean end) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isObstacle()
	 */
	@Override
	public boolean isObstacle() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setObstacle(boolean)
	 */
	@Override
	public void setObstacle(boolean obstacle) {
		// TODO Auto-generated method stub

	}

	// TODO move to own file or part of IRoom
	public enum Type {
		GENERAL("general"),
		LADDER("ladder"),
		ENTRANCE("entrance"),
		EXIT("exit"),
		TREASURE("treasure"),
		BOSS("boss"),
		HALLWAY("hallway");

		private String name;
		
		/**
		 * @param arg0
		 * @param arg1
		 */
		Type(String name) {
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
		public void setName(String name) {
			this.name = name;
		}
	}
	
	public void setType(Type type) {
		
	}
	
	
}
