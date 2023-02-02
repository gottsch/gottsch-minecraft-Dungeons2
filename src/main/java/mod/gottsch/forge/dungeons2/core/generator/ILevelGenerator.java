/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2020 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.generator;

import java.util.List;


/**
 * @author Mark Gottschling on Jun 21, 2020
 *
 */
public interface ILevelGenerator {

	// TODO may need to change boolean[][] to some sort of class array
	/**
	 * 
	 * @return
	 */
	ILevel generate();

	ILevelGenerator withWidth(int width);

	ILevelGenerator withHeight(int height);

	ILevel init();

	/**
	 * It is assumed that the rooms list is sorted in some fashion or the caller has a method to map the matrix indices back to a room object
	 * @param nodes
	 * @return
	 */
	public static double[][]/*Map<String, Double>*/ getDistanceMatrix(List<? extends INode> nodes) {
//        Map<String, Double> distanceMap = new HashMap<>();
		double[][] matrix = new double[nodes.size()][nodes.size()];

		for (int i = 0; i < nodes.size(); i++) {
			INode node1 = nodes.get(i);
			for (int j = 0; j < nodes.size(); j++) {
				INode node2 = nodes.get(j);
				if (node1 == node2) {
                    matrix[i][j] = 0.0;
//                    distanceMap.put(getKey(node1, node2), Double.valueOf(0.0));
				}
				else {
                    if (matrix[i][j] == 0.0) {
//                    if (distanceMap.get(getKey(node1, node2)) == null) {
						// calculate distance;
						double dist = node1.getCenter().getDistance(node2.getCenter());
						matrix[i][j] = dist;
                        matrix[j][i] = dist;
//                        distanceMap.put(getKey(node1, node2), Double.valueOf(dist));
//                        distanceMap.put(getKey(node2, node1), Double.valueOf(dist));
					}
				}
			}
		}
         return matrix;
//        return distanceMap;
	}

    static String getKey(INode node1, INode node2) {
        return node1.getId() + ":" + node2.getId();
    }
}
