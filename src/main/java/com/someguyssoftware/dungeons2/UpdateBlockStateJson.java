/**
 * 
 */
package com.someguyssoftware.dungeons2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

/**
 * @author Mark Gottschling on Feb 6, 2017
 *
 */
public class UpdateBlockStateJson {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		GsonBuilder builder = new GsonBuilder();
		builder.disableHtmlEscaping();
//		Type blockStates = new TypeToken<Map<String, BlockStates>>() {}.getType();
		builder.setPrettyPrinting();
		Gson gson = builder.create();
		
		Wrapper wrapper = null;

		UpdateBlockStateJson x = new UpdateBlockStateJson();
		int i = 0;
		boolean addUp = false;
		boolean addDown=false;
		
		// go through all the json block states and add the up and down if there is a facing= key
		String filepath = "E:/Development/workspace/mars/Dungeons2-194/src/resources/assets/dungeons2/blockstates";
		  File dir = new File(filepath);
		  File[] directoryListing = dir.listFiles();
		  if (directoryListing != null) {
		    for (File child : directoryListing) {
		    	if (child.isDirectory()) continue;
		    	System.out.println("File:" + child.getAbsolutePath());
				JsonReader reader = null;
				try {
					reader = new JsonReader(new FileReader(child));
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}		    	
		    	wrapper = gson.fromJson(reader, Wrapper.class);
//		    	System.out.println(wrapper);
		    	if (wrapper.getVariants().containsKey("facing=north")) {
		    		if (!wrapper.getVariants().containsKey("facing=up")) {
		    			addUp = true;
		    		}
		    		if (!wrapper.getVariants().containsKey("facing=down")) {
		    			addDown = true;
		    		}
		    	}

		    	if (addUp) {
	    			System.out.println("Adding UP");
	    			wrapper.getVariants().put("facing=up", new BlockStates(wrapper.getVariants().get("facing=north").getModel()));		
		    	}
		    	
		    	if (addDown) {
	    			System.out.println("Adding Down");
	    			wrapper.getVariants().put("facing=down", new BlockStates(wrapper.getVariants().get("facing=north").getModel()));
		    	}
		    	
		    	if (!addUp && !addDown) continue;
		    	
		    	for (Map.Entry<String, BlockStates> s : wrapper.getVariants().entrySet()) {
		    		System.out.println(s.getKey());
		    	}
		    	
		    	// save file back
		    	try (Writer writer = new FileWriter(filepath + "/" +child.getName())) {
		    		gson.toJson(wrapper, writer);
		    	}
		    	catch(Exception e) {
		    		e.printStackTrace();
		    		break;
		    	}
		    	
		    	// TEMP
//		    	i++;
//		    	if (i > 1)  	break;
		    	
		    	
		    	addUp = false;
		    	addDown = false;
		    }
		  }
		
	}
	
	public class Wrapper {
		LinkedHashMap<String, BlockStates> variants;

		/**
		 * @return the variants
		 */
		public LinkedHashMap<String, BlockStates> getVariants() {
			return variants;
		}

		/**
		 * @param variants the variants to set
		 */
		public void setVariants(LinkedHashMap<String, BlockStates> variants) {
			this.variants = variants;
		}
		
	}
	public static class BlockStates {
		String model;
		Integer x;
		Integer y;
		Boolean uvlock;

		public BlockStates() {}
		
		public BlockStates(String model) {
			this.model = model;
		}
		
		/**
		 * @return the model
		 */
		public String getModel() {
			return model;
		}

		/**
		 * @param model the model to set
		 */
		public void setModel(String model) {
			this.model = model;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "BlockStates [model=" + model + "]";
		}

		/**
		 * @return the x
		 */
		public Integer getX() {
			return x;
		}

		/**
		 * @param x the x to set
		 */
		public void setX(Integer x) {
			this.x = x;
		}

		/**
		 * @return the y
		 */
		public Integer getY() {
			return y;
		}

		/**
		 * @param y the y to set
		 */
		public void setY(Integer y) {
			this.y = y;
		}

		/**
		 * @return the uvlock
		 */
		public Boolean isUvlock() {
			return uvlock;
		}

		public Boolean getUvlock() {
			return uvlock;
		}
		
		/**
		 * @param uvlock the uvlock to set
		 */
		public void setUvlock(Boolean uvlock) {
			this.uvlock = uvlock;
		}
		
	}

}
