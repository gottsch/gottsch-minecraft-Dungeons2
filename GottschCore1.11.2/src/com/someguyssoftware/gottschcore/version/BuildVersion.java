/**
 * 
 */
package com.someguyssoftware.gottschcore.version;

/**
 * @author Mark Gottschling on Jan 3, 2016
 * @version 2.1 updated major, minor and build properties to = -1 such that the initial object is equal to the EMPTY_VERSION.
 * @version 2.0
 *
 */
public class BuildVersion {
	/**
	 * Empty version object.
	 */
	public static BuildVersion EMPTY_VERSION = new BuildVersion(-1, -1, -1);
	
	private int major = -1;
	private int minor = -1;
	private int build = -1;
	
	/**
	 * Empty constructor.
	 */
	public BuildVersion(){}
	
	/**
	 * Full constructor.
	 * @param major
	 * @param minor
	 * @param build
	 */
	
	public BuildVersion(int major, int minor, int build) {
		this.major = major;
		this.minor = minor;
		this.build = build;
	}

	/**
	 * Constructor that takes a string representation of a version in format of "major.minor.build" where major, minor and build are integer representations.
	 * @param version
	 */
	public BuildVersion(String version) {
		String[] parts = version.split("\\.");

		if (parts.length >= 1) {
			setMajor(Integer.valueOf(parts[0]));
		}
		if (parts.length >=2) {
			setMinor(Integer.valueOf(parts[1]));
		}
		if (parts.length >=3) {
			setBuild(Integer.valueOf(parts[2]));
		}
	}
	
	/**
	 * Tests whether this object is the EMPTY_VERSION object
	 * @return
	 */
	public boolean isEmpty() {
		return this.equals(EMPTY_VERSION);
	}
	
	/**
	 * 
	 * @return
	 */
	public int getMajor() {
		return major;
	}

	/**
	 * 
	 * @param major
	 */
	public void setMajor(int major) {
		this.major = major;
	}

	/**
	 * 
	 * @return
	 */
	public int getMinor() {
		return minor;
	}

	/**
	 * 
	 * @param minor
	 */
	public void setMinor(int minor) {
		this.minor = minor;
	}

	/**
	 * 
	 * @return
	 */
	public int getBuild() {
		return build;
	}
	
	/**
	 * 
	 * @param build
	 */
	public void setBuild(int build) {
		this.build = build;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return major + "." + minor + "." +build;
	}	

	/** (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 * @since 2.0
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + build;
		result = prime * result + major;
		result = prime * result + minor;
		return result;
	}

	/** (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 * @since 2.0
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BuildVersion other = (BuildVersion) obj;
		if (build != other.build)
			return false;
		if (major != other.major)
			return false;
		if (minor != other.minor)
			return false;
		return true;
	}
}
