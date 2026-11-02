
package mod.gottsch.neo.dungeons2.core.generator;

import mod.gottsch.neo.gottschcore.spatial.ICoords;

/**
 * @author Mark Gottschling on Aug 15, 2019
 *
 */
public interface IGeneratorData {
	public ICoords getSpawnCoords();
	public void setSpawnCoords(ICoords coords);
}
