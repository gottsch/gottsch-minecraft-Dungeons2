/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.dungeons2.model.Room.Type;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

/**
 * @author Mark Gottschling on Sep 4, 2017
 *
 */
// TODO rename class
// TODO create Abstract room and IRoom... all rooms should have a layout and doors.
public class AboveRoom extends AbstractRoom {

	private Roof roof;
	
	private boolean plinth;
	private boolean cornice;
	
	/**
	 * 
	 */
	public AboveRoom() {
		setID(RandomHelper.randomInt(-5000, 5000));
		setCoords(new Coords(0,0,0));
//		setType(Type.GENERAL);		
//		setDirection(Direction.SOUTH); // South
	}
	
	/**
	 * 
	 * @param id
	 */
	public AboveRoom(int id) {
		setID(id);
	}

	/**
	 * 
	 * @param roomIn
	 */
	public AboveRoom(AboveRoom room) {
		this.setAnchor(room.isAnchor());
		this.setCoords(room.getCoords());
		this.setCornice(room.isCornice());
		// TODO these need to create new objects;
		this.setDimensions(room.getDimensions());
		this.setDoors(room.getDoors());
		this.setID(room.getID());
		this.setLayout(room.getLayout());
		this.setName(room.getName());
		this.setPlinth(room.isPlinth());
		this.setRoof(room.getRoof());		
	}

	/**
	 * 
	 */
	public IRoom copy() {
		return new AboveRoom(this);
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasRoof() {
		if (roof != null) return true;
		return false;
	}

	/**
	 * @return the roof
	 */
	public Roof getRoof() {
		return roof;
	}

	/**
	 * @param roof the roof to set
	 */
	public void setRoof(Roof roof) {
		this.roof = roof;
	}

	/**
	 * @return the plinth
	 */
	public boolean isPlinth() {
		return plinth;
	}

	/**
	 * @param plinth the plinth to set
	 */
	public void setPlinth(boolean plinth) {
		this.plinth = plinth;
	}

	/**
	 * @return the cornice
	 */
	public boolean isCornice() {
		return cornice;
	}

	/**
	 * @param cornice the cornice to set
	 */
	public void setCornice(boolean cornice) {
		this.cornice = cornice;
	}
	
	/**
	 * 
	 */
	public AboveRoom setAnchor(boolean anchor) {
		super.setAnchor(anchor);
		return this;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "AboveRoom [roof=" + roof + ", plinth=" + plinth + ", cornice=" + cornice + ", hasDoors()=" + hasDoors()
				+ ", isAnchor()=" + isAnchor() + ", getDistance()=" + getDistance() + ", isReject()=" + isReject()
				+ ", getCenter()=" + getCenter() + ", getID()=" + getID() + ", getName()=" + getName()
				+ ", getCoords()=" + getCoords() + ", getDimensions()=" + getDimensions() + "]";
	}


}
