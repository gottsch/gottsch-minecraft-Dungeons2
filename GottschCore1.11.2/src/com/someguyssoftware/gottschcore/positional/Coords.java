/**
 * 
 */
package com.someguyssoftware.gottschcore.positional;

import javax.annotation.concurrent.Immutable;

import com.someguyssoftware.gottschcore.GottschCore;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * For Forge 1.8+
 * @author Mark Gottschling on May 15, 2015
 * @version 2.0
 */
@Immutable
public class Coords implements ICoords {
	private final int x;
	private final int y;
	private final int z;
	
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	public Coords(final int x, final int y, final int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	/**
	 * 
	 * @param coords
	 */
	public Coords(ICoords coords) {
		this(coords.getX(), coords.getY(), coords.getZ());
	}
	
	/**
	 * Copy constructor from BlockPos
	 * @param pos
	 */	
	public Coords(BlockPos pos) {
		this(pos.getX(), pos.getY(), pos.getZ());
	}
	
	/**
	 * Offset this Coords n blocks up
	 * @param n the amount to offset by
	 */
	@Override
    public ICoords up(int n) {
    	return new Coords(this.getX(), this.getY() + n, this.getZ());
    }
    
    /**
     * Offset this Coords n blocks down
     */
    @Override
    public ICoords down(int n) {
    	return new Coords(this.getX(), this.getY() - n, this.getZ());
    }
    
    /**
     * Offset this Coords n blocks north
     */
    @Override
    public ICoords north(int n) {
    	return new Coords(this.getX(), this.getY(), this.getZ() - n);
    }
    
    /**
     * Offset this Coords n blocks south
     */
    @Override
    public ICoords south(int n) {
    	return new Coords(this.getX(), this.getY() + n, this.getZ() + n);
    }
    
    /**
     * Offset this Coords n blocks east
     */
    @Override
    public ICoords east(int n) {
    	return new Coords(this.getX() + n, this.getY(), this.getZ());
    }
    
    /**
     * Offset this Coords n blocks west
     */
    @Override
    public ICoords west(int n) {
    	return new Coords(this.getX() - n, this.getY(), this.getZ());
    }
    
	/**
	 * Calculate squared distance to the given coordinates
	 * @param toX
	 * @param toY
	 * @param toZ
	 * @since 1.0
	 */
    @Override
	public double getDistanceSq(double toX, double toY, double toZ) {
        double d0 = this.getX() - toX;
        double d1 = this.getY() - toY;
        double d2 = this.getZ() - toZ;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }
    
    /**
     * 
     * @param coords
     * @return
	 * @since 1.0
     */
    @Override
	public double getDistanceSq(ICoords coords) {
    	return getDistanceSq(coords.getX(), coords.getY(), coords.getZ());
    }
    
    /**
     * 
     * @param x
     * @param y
     * @param z
     * @return
	 * @since 1.0
     */
    @Override
	public double getDistance(double x, double y, double z) {
	    double d0 = this.getX() - x;
	    double d1 = this.getY() - y;
	    double d2 = this.getZ() - z;
	    return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
    }
    
    /**
     * 
     * @param coords
     * @return
	 * @since 1.0
     */
    @Override
	public double getDistance(ICoords coords) {
    	return getDistance(coords.getX(), coords.getY(), coords.getZ());
    }
    
    /**
     * 
     * @param targetX
     * @param targetZ
     * @return
	 * @since 1.0
     */
    @Override
	public double getXZAngle(double targetX, double targetZ) {
		double angle = Math.toDegrees(Math.atan2(targetZ - getZ(), targetX - getX()));		
		if (angle < 0) {
			angle += 360;
		}		
		return angle;
    }
    
    /**
     * 
     * @param coords
     * @return
	 * @since 1.0
     */
    @Override
	public double getXZAngle(ICoords coords) {
    	return getXZAngle(coords.getX(), coords.getZ());
    }
    
	/**
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @return new instance
	 */
	@Override
	public ICoords add(int x, int y, int z) {
		ICoords coords = new Coords(this.x + x, this.y + y, this.z + z);
		return coords;
	}
	
	/**
	 * 
	 * @param coords
	 * @return new instance
	 */
	@Override
	public ICoords add(ICoords coords) {
		ICoords c = new Coords(this.x + coords.getX(), this.y + coords.getY(), this.z + coords.getZ());
		return c;
	}
	
	/**
	 * Delta between this and input ie. this.[xyz] - input.[xyz]
	 * @param coords
	 * @return new instance
	 */
	@Override
	public ICoords delta(ICoords coords) {
		ICoords c = new Coords(this.x - coords.getX(), this.y - coords.getY(), this.z - coords.getZ());
		return c;
	}

	/**
	 * 
	 * @return new BlockPos instance
	 */
	@Override
	public BlockPos toPos() {
		return new BlockPos(getX(), getY(), getZ());
	}

	/**
	 * Rotate 90 degrees.
	 * @param width
	 * @return new instance
	 */
	@Override
	public ICoords rotate90(int width) {
		ICoords coords = new Coords(width - this.getZ() - 1, this.getY(), this.getX());
		return coords;
	}
	
	/**
	 * Rotate 180 degrees
	 * @param depth
	 * @param width
	 * @return new instance
	 */
	@Override
	public ICoords rotate180(int depth, int width) {
		ICoords coords = new Coords(width - this.getX() - 1, this.getY(), depth - this.getZ() - 1);
		return coords;
	}
	
	/**
	 * 
	 * @param depth
	 * @return new instance
	 */
	@Override
	public ICoords rotate270(int depth) {
		ICoords coords = new Coords(this.z, this.y, depth - this.getX() - 1);
		return coords;
	}

	/**
	 * Convenience method
	 * @param axis
	 * @return
	 * @since 1.0
	 */
	@Override
	public int get(int axis) {
		switch(axis) {
		case 0: return this.x;
		case 1: return this.y;
		case 2: return this.z;
		default:return this.x;
		}
	}
	
	/**
	 * Convenience method
	 * @param axis
	 * @return
	 * @since 1.0
	 */
	@Override
	public int get(char axis) {
		switch(axis) {
		case 'x':
		case 'X': return this.x;
		case 'y': 
		case 'Y': return this.y;
		case 'z':
		case 'Z': return this.z;
		default: return this.x;
		}
	}	
	
	/**
	 * 
	 * @param nbt
	 * @return
	 */
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
		try {
			nbt.setInteger("x", getX());
			nbt.setInteger("y", getY());
			nbt.setInteger("z", getZ());
		}
		catch(Exception e) {
			GottschCore.logger.error("Unable to write state to NBT:", e);
		}		
		return nbt;
	}
	
	@Override
	public int getX() {
		return x;
	}

	@Override
	public ICoords resetX(int x) {
    	return new Coords(x, this.getY(), this.getZ());
	}
	
	@Override
	public int getY() {
		return y;
	}
	
	@Override
	public ICoords resetY(int y) {
    	return new Coords(this.getX(), y, this.getZ());
	}

	@Override
	public int getZ() {
		return z;
	}
	
	@Override
	public ICoords resetZ(int z) {
    	return new Coords(this.getX(), this.getY(), z);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + x;
		result = prime * result + y;
		result = prime * result + z;
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Coords other = (Coords) obj;
		if (x != other.x)
			return false;
		if (y != other.y)
			return false;
		if (z != other.z)
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Coords [x=" + x + ", y=" + y + ", z=" + z + "]";
	}
	
	@Override
	public String toShortString() {
		return x + " " + y + " " + z;
	}
	
	/**
	 * @param pos
	 * @return
	 */
	public static String toShortString(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
