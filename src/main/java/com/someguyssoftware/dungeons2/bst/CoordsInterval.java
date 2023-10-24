package com.someguyssoftware.dungeons2.bst;

import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * 
 * @author Mark Gottschling on Sep 19, 2022
 *
 */
public class CoordsInterval<D> implements IInterval<D> {
	public static final CoordsInterval<?> EMPTY = new CoordsInterval<>(new Coords(0, -999, 0), new Coords(0, -999, 0), null);
	
	private ICoords coords1;
	private ICoords coords2;
	
	private Integer min;
	private Integer max;
	private IInterval<D> left;
	private IInterval<D> right;

	// extra mod specific data
	private D data;
	
	/**
	 * 
	 */
	public CoordsInterval() {	}
	
	/**
	 * 
	 * @param coords1 the starting coords
	 * @param coords2 the ending coords
	 * @param supplier
	 */
	public CoordsInterval(ICoords coords1, ICoords coords2) {
		this.coords1 = coords1;
		this.coords2 = coords2;
		this.min = coords1.getX() < coords2.getX() ? coords1.getX() : coords2.getX();
		this.max = coords2.getX() > coords2.getX() ? coords1.getX() : coords2.getX();
	}
	
	/**
	 * 
	 * @param coords1
	 * @param coords2
	 * @param data
	 */
	public CoordsInterval(ICoords coords1, ICoords coords2, D data) {
		this.coords1 = coords1;
		this.coords2 = coords2;
		this.min = coords1.getX() < coords2.getX() ? coords1.getX() : coords2.getX();
		this.max = coords2.getX() > coords2.getX() ? coords1.getX() : coords2.getX();
		this.data = data;
	}
	
	/**
	 * Copy constructor
	 * @param interval
	 */
	public CoordsInterval(CoordsInterval<D> interval) {
		this.coords1 = interval.coords1;
		this.coords2 = interval.coords2;
		this.min = interval.min;
		this.max = interval.max;
		this.data = interval.data;
		this.right = interval.right;
		this.left = interval.left;
	}
	
	@Override
	public int compareTo(IInterval<D> intervalIn) {
		CoordsInterval<D> interval = (CoordsInterval<D>)intervalIn;
		
		if (this.getStart() < interval.getStart()) {
			return -1;
		} else if (this.getStart() == interval.getStart()) {

			if (getEnd() == interval.getEnd()) {
				if (getStartZ() < interval.getStartZ()) {
					return -1;
				} else if (getStartZ() == interval.getStartZ()) {
					if (getEndZ() == interval.getEndZ()) {

						//////////////					
						if (getStartY() < interval.getStartY()) {
							return -1;
						} else if (getStartY() == interval.getStartY()) {
							if (getEndY() == interval.getEndY()) {
								return 0;
							}
							return this.getEndY() < interval.getEndY() ? -1 : 1;
						} else {
							return 1;
						}
						//////////////
					}
					return this.getEndZ() < interval.getEndZ() ? -1 : 1;
				} else {
					return 1;
				}
			} else {
				return this.getEnd() < interval.getEnd() ? -1 : 1;
			}
		} else {
			return 1;
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean isEmpty() {
		if (this.coords1.equals(EMPTY.getCoords1())
				|| this.coords2.equals(EMPTY.getCoords2())) {
			return true;
		}
		return false;
	}
	
	@Override
	public int getStart() {
		return coords1.getX();
	}
	@Override
	public int getEnd() {
		return coords2.getX();
	}
	@Override
	public Integer getMin() {
		return min;
	}
	@Override
	public void setMin(Integer min) {
		this.min = min;
	}
	@Override
	public Integer getMax() {
		return max;
	}
	@Override
	public void setMax(Integer max) {
		this.max = max;
	}
	@Override
	public IInterval<D> getLeft() {
		return left;
	}
	@Override
	public void setLeft(IInterval<D> left) {
		this.left = left;
	}
	@Override
	public IInterval<D> getRight() {
		return right;
	}
	@Override
	public void setRight(IInterval<D> right) {
		this.right = right;
	}

	public int getStartZ() {
		return coords1.getZ();
	}

	public int getEndZ() {
		return coords2.getZ();
	}

	public int getStartY() {
		return coords1.getY();
	}

	public int getEndY() {
		return coords2.getY();
	}

	public ICoords getCoords1() {
		return coords1;
	}

	public void setCoords1(ICoords coords1) {
		this.coords1 = coords1;
	}

	public ICoords getCoords2() {
		return coords2;
	}

	public void setCoords2(ICoords coords2) {
		this.coords2 = coords2;
	}

	public D getData() {
		return data;
	}

	public void setData(D data) {
		this.data = data;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((coords1 == null) ? 0 : coords1.hashCode());
		result = prime * result + ((coords2 == null) ? 0 : coords2.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CoordsInterval<D> other = (CoordsInterval<D>) obj;
		if (coords1 == null) {
			if (other.coords1 != null)
				return false;
		} else if (!coords1.equals(other.coords1))
			return false;
		if (coords2 == null) {
			if (other.coords2 != null)
				return false;
		} else if (!coords2.equals(other.coords2))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "CoordsInterval [coords1=" + coords1 + ", coords2=" + coords2 + ", min=" + min + ", max=" + max
				+ ", left=" + left + ", right=" + right + ", data=" + data + "]";
	}
}
