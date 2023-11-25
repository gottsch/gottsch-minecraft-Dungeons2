
package mod.gottsch.forge.dungeons2.core.generator;

import mod.gottsch.forge.gottschcore.spatial.ICoords;

/**
 * @author Mark Gottschling on Aug 15, 2019
 *
 */
public interface IGeneratorData {
	public ICoords getSpawnCoords();
	public void setSpawnCoords(ICoords coords);
}
