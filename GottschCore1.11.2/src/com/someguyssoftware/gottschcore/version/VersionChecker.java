/**
 * 
 */
package com.someguyssoftware.gottschcore.version;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.someguyssoftware.gottschcore.GottschCore;

/**
 * @author Mark Gottschling on Apr 30, 2015
 * @version 2.0
 */
public class VersionChecker {

	/**
	 * Empty constructor
	 */
	public VersionChecker() {}
	
	
	/**
	 * Loads a JSON file from a URL and returns the BuildVersion object for the specified minecraft BuildVersion.
	 * @param url
	 * @return
	 * @since 2.0
	 */
	public static BuildVersion getVersion(String url, BuildVersion mv) {
		InputStream in = null;
		String json = null;
		try {
			in = new URL(url).openStream();
			json = IOUtils.toString(in);			
			GottschCore.logger.info("Published Version Info: " + json);
		}
		catch(MalformedURLException e) {
			GottschCore.logger.warn("Bad URL: " + url);
			return BuildVersion.EMPTY_VERSION;
		}
		catch(IOException e) {
			GottschCore.logger.warn("IO Exception occurred: " + e.getMessage());
			return BuildVersion.EMPTY_VERSION;
		}
		catch(Exception e ) {
			GottschCore.logger.warn("Unexpected exception occurred: " + e.getMessage());
			return BuildVersion.EMPTY_VERSION;
		}
		
		try {
			// convert json string into Version object
			Gson gson = new Gson();
			Type collectionType = new TypeToken<Collection<VersionPackage>>(){}.getType();
			List<VersionPackage> packages = gson.fromJson(json, collectionType);
			
			// test if mod version is previous to latest published version
			for (VersionPackage pack : packages) {
				BuildVersion v = pack.getMinecraft();
				if (v.getMajor() == mv.getMajor() &&
						v.getMinor() == mv.getMinor() &&
						v.getBuild() == mv.getBuild()) {
					return pack.getMod();
				}
			}
		}
		catch(Exception e) {
			GottschCore.logger.warn("Bad JSON: " + json);
			return BuildVersion.EMPTY_VERSION;
		}		
		return BuildVersion.EMPTY_VERSION;
	}
	
	/**
	 * 
	 * @param version the provided version to check against
	 * @param currentVersion the mod's current version
	 * @return
	 * @since 2.0
	 */
	public static boolean checkVersion(BuildVersion version, BuildVersion modVersion) {
		
		if (version.getMajor() > modVersion.getMajor()) {
			return false;
		}
		else if (version.getMajor() == modVersion.getMajor()) {
			if (version.getMinor() > modVersion.getMinor()) {
				return false;
			}
			else if (version.getMinor() == modVersion.getMinor()) {
				if (version.getBuild() > modVersion.getBuild()) {
					return false;
				}
			}
		}
		return true;
	}
}
