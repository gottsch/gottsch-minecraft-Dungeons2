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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.jdiemke.triangulation.DelaunayTriangulator;
import io.github.jdiemke.triangulation.NotEnoughPointsException;
import io.github.jdiemke.triangulation.Triangle2D;
import io.github.jdiemke.triangulation.Vector2D;

import mod.gottsch.forge.dungeons2.core.graph.mst.Edge;
import mod.gottsch.forge.dungeons2.core.graph.mst.EdgeWeightedGraph;
import mod.gottsch.forge.dungeons2.core.graph.mst.LazyPrimMST;

/**
 * @author Mark Gottschling on Sep 18, 2020
 *
 */
public abstract class AbstractGraphLevelGenerator implements ILevelGenerator {
	protected static final Logger LOGGER = LogManager.getLogger(AbstractGraphLevelGenerator.class);
	
	/*
	 * DelaunayTriangulator library requires that the list of nodes be in an ordered
	 * list (the actual sort order doesn't matter, as long as it keeps its order).
	 */
	protected Optional<List<Edge>> triangulate(List<? extends INode> nodes) {
		/*
		 *  weight/cost array of all rooms
		 *  the indexes is a "map" to the nodes by their position in the list
		 */
        double[][] matrix = ILevelGenerator.getDistanceMatrix(nodes);
        
		/*
		 * maps all nodes by origin(x,y)
		 * this is required for the Delaunay Triangulation library because it only returns edges without any identifying properties, only points
		 */
		Map<String, INode> map = new HashMap<>();
        
        /*
		 * holds all nodes in Vector2D format.
		 * used for the Delaunay Triangulation library to calculate all the edges between nodes.
		 * 
		 */
		Vector<Vector2D> pointSet = new Vector<>();		
        
        /*
		 * holds all the edges that are produced from triangulation
		 */
		List<Edge> edges = new ArrayList<>();

		/**
		 * a flag to indicate that an edge leading to the "end" room is created
		 */
		boolean isEndEdgeMet = false;
		int endEdgeCount = 0;

		// map all nodes by origin(x,y) and build all edges.
		for (INode node : nodes) {
			Coords2D origin = node.getOrigin();
			// map out the rooms by origin
			map.put(origin.getX() + ":" + origin.getY(), node);
			// convert coords into vector2d for triangulation
			Vector2D v = new Vector2D(origin.getX(), origin.getY());
//			LOGGER.debug(String.format("Room.id: %d = Vector2D: %s", node.getId(), v.toString()));
			pointSet.add(v);
		}

		// triangulate the set of points
		DelaunayTriangulator triangulator = null;
		try {
			triangulator = new DelaunayTriangulator(pointSet);
			triangulator.triangulate();
		}
		catch(NotEnoughPointsException e) {
			LOGGER.warn("Not enough points where provided for triangulation. Level generation aborted.");
			return Optional.empty();
		}
		catch(Exception e) {
//			if (nodes !=null) {
//				LOGGER.debug("rooms.size=" + nodes.size());
//			}
//			else {
//				LOGGER.debug("Rooms is NULL!");
//			}
//			if (pointSet != null) {
//				LOGGER.debug("Pointset.size=" + pointSet.size());
//			}
//			else {
//				LOGGER.debug("Pointset is NULL!");
//			}			
			LOGGER.error("Unable to triangulate: ", e);
		}

		// retrieve all the triangles from triangulation
		List<Triangle2D> triangles = triangulator.getTriangles();

		for(Triangle2D triangle : triangles) {
			// locate the corresponding rooms from the points of the triangles
			INode node1 = map.get((int)triangle.a.x + ":" + (int)triangle.a.y);
			INode node2 = map.get((int)triangle.b.x + ":" + (int)triangle.b.y);
			INode node3 = map.get((int)triangle.c.x + ":" + (int)triangle.c.y);			

			// build an edge based on room distance matrix
			Edge edge = new Edge(node1.getId(), node2.getId(), matrix[node1.getId()][node2.getId()]/*matrix.get(ILevelGenerator.getKey(node1, node2))*/);
			
			// TODO for boss room, not necessarily end room
			// remove any edges that lead to the end room(s) if the end room already has one edge
			// remove (or don't add) any edges that lead to the end room if the end room already has it's maximum edges (degrees)
			if (node1.getType() != NodeType.END && node2.getType() != NodeType.END) {
//			if (!r1.getType().equals(Type.BOSS) && !r2.getType().equals(Type.BOSS)) {
				edges.add(edge);
			}
			else if (node1.getType() == NodeType.START || node2.getType()  == NodeType.START) {
				// skip if start joins the end
			}
			else if (!isEndEdgeMet) {
				// add the edge
				edges.add(edge);
				// increment the number of edges leading to the end room
				endEdgeCount++;
				// get the end room
				INode end = node1.getType() == NodeType.END ? node1 : node2;
				if (endEdgeCount >= end.getMaxDegrees()) {
					isEndEdgeMet = true;
				}
			}
			
			edge = new Edge(node2.getId(), node3.getId(), matrix[node2.getId()][node3.getId()]);
			if (node2.getType() != NodeType.END && node3.getType() != NodeType.END) {
				edges.add(edge);
			}
			else if (node1.getType() == NodeType.START || node2.getType() == NodeType.START) {
				// skip
			}
			else if (!isEndEdgeMet) {
				edges.add(edge);
				isEndEdgeMet = true;
			}
			
			edge = new Edge(node1.getId(), node3.getId(), matrix[node1.getId()][node3.getId()]);
			if (node1.getType() != NodeType.END && node3.getType() != NodeType.END) {
				edges.add(edge);
			}
			else if (node1.getType() == NodeType.START || node2.getType() == NodeType.START) {
				// skip
			}
			else if (!isEndEdgeMet) {
				edges.add(edge);
				isEndEdgeMet = true;
			}
		}
		return Optional.of(edges);
	}
	
	/**
	 * 
	 * @param random
	 * @param edges
	 * @param nodes
	 * @param pathFactor
	 * @return
	 */
	protected List<Edge> getPaths(Random random, List<Edge> edges, List<? extends INode> nodes, double pathFactor) {
		/*
		 * paths are the reduced edges generated by the Minimun Spanning Tree
		 */
		List<Edge> paths = new ArrayList<>();
		
		/**
		 * counts the number of edges that are assigned to each node/vertex
		 */
		int[] edgeCount = new int[nodes.size()];
		
		/*
		 * Map to keep track of which edges for source list have already been used (in MST and extra edges)
		 */
		Map<String, Edge> usedEdges = new HashMap<>();
		
		// reduce all edges to MST
		EdgeWeightedGraph graph = new EdgeWeightedGraph(nodes.size(), edges);
		LazyPrimMST mst = new LazyPrimMST(graph);
		for (Edge edge : mst.edges()) {
			if (edge.v < nodes.size() && edge.w < nodes.size()) {
				INode node1 = nodes.get(edge.v);
				INode node2 = nodes.get(edge.w);	
				// only add edge if max edges has not been met
				if (edgeCount[node1.getId()] < node1.getMaxDegrees() && edgeCount[node2.getId()] < node2.getMaxDegrees()) {
					paths.add(edge);
					edgeCount[node1.getId()]++;
					edgeCount[node2.getId()]++;
				}
			}
			else {
				//LOGGER.warn(String.format("Ignored Room: array out-of-bounds: v: %d, w: %d", edge.v, edge.w));
			}
		}		
		
		// add more edges
		int addtionalEdges = (int) (edges.size() * pathFactor);
		for (int i = 0 ; i < addtionalEdges; i++) {
			int pos = random.nextInt(edges.size());
			Edge edge = edges.get(pos);
			
			// TODO ensure that only non-used edges are selected (and doesn't increment the counter)
			// this would require a list of edges used, BUT first need to match up the edges from mst.edges and
			// param edges - their array indexes wouldn't align.
			INode node1 = nodes.get(edge.v);
			INode node2 = nodes.get(edge.w);
			if (node1.getType() != NodeType.END && node2.getType() != NodeType.END &&
					edgeCount[node1.getId()] < node1.getMaxDegrees() && edgeCount[node2.getId()] < node2.getMaxDegrees()) {
				paths.add(edge);
				edgeCount[node1.getId()]++;
				edgeCount[node2.getId()]++;				
			}
		}
		return paths;
	}
	
	/**
	 * perform a breadth first search against the list of edges to determine if a path exists
	 * from one node to another.
	 * @param start
	 * @param end
	 * @param nodes
	 * @param edges
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected boolean breadthFirstSearch(int start, int end, List<? extends INode> nodes, List<Edge> edges) {
		// build an adjacency list
		LinkedList<Integer> adj[];

		adj = new LinkedList[nodes.size()];
		for (INode r : nodes) {
			adj[r.getId()] = new LinkedList<>();
		}
		
        for (Edge e : edges) {
        	adj[e.v].add(e.w);
        	// add both directions to ensure all adjacencies are covered
        	adj[e.w].add(e.v);     	
        }

		// mark all the vertices as not visited(By default set as false)
		boolean visited[] = new boolean[nodes.size()];

		// create a queue for BFS
		LinkedList<Integer> queue = new LinkedList<Integer>();

		// mark the current node as visited and enqueue it
		visited[start]=true;
		queue.add(start);

		while (queue.size() != 0) {
			// Dequeue a vertex from queue and print it
			int s = queue.poll();

			// get all adjacent vertices of the dequeued vertex s
			// if a adjacent has not been visited, then mark it
			// visited and enqueue it
			Iterator<Integer> i = adj[s].listIterator();
			while (i.hasNext()) {
				int n = i.next();
				if (n == end) return true;
				
				if (!visited[n]) {
					visited[n] = true;
					queue.add(n);
				}
			}
		}		
		return false;
	}

	@Override
	public ILevelGenerator withWidth(int width) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ILevelGenerator withHeight(int height) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ILevel init() {
		// TODO Auto-generated method stub
		return null;
	}

}
