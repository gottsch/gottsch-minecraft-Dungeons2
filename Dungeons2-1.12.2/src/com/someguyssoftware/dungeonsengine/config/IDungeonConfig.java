/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.util.List;

import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Dec 18, 2018
 *
 */
public interface IDungeonConfig {

		public Quantity getNumLevels();
		public void setNumLevels(Quantity num);
		
		public int getTopLimit();
		public void setTopLimit(int limit);
		
		public int getBottomLimit();
		public void setBottomLimit(int limit);		
		
		public ILevelConfig[] getLevelConfigs();
		public void setLevelConfigs(ILevelConfig[] configs);
		
		Integer getSurfaceBuffer();
		void setSurfaceBuffer(Integer surfaceBuffer);
		
		String getName();
		void setName(String name);
		
		DungeonSize getSize();
		void setSize(DungeonSize size);
		
		List<String> getBiomeWhiteList();
		void setBiomeWhiteList(List<String> biomeWhiteList);
		
		List<String> getBiomeBlackList();
		void setBiomeBlackList(List<String> biomeBlackList);
		
		double getFieldFactor();
		void setFieldFactor(double fieldFactor);
		
		String getVersion();
		void setVersion(String version);
		ILevelConfig getSurfaceConfig();
		void setSurfaceConfig(ILevelConfig surfaceConfig);
		
		boolean isMinecraftConstraints();
		void setMinecraftConstraints(boolean minecraftConstraints);

}
