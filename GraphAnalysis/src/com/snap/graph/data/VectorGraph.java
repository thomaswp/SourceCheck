package com.snap.graph.data;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class VectorGraph extends OutGraph<VectorState> {

	private final HashMap<VectorState, List<IndexedVectorState>> goalContextMap = 
			new HashMap<VectorState, List<IndexedVectorState>>();
	
	public boolean setGoal(VectorState goal, IndexedVectorState context) {
		List<IndexedVectorState> list = getContext(goal);
		list.add(context);
		
		return super.setGoal(goal, true);
	}

	private List<IndexedVectorState> getContext(VectorState goal) {
		List<IndexedVectorState> list = goalContextMap.get(goal);
		if (list == null) {
			list = new ArrayList<IndexedVectorState>();
			goalContextMap.put(goal, list);
		}
		return list;
	}
	
	public void addGraph(VectorGraph graph, boolean atomicWeight) {
		super.addGraph(graph, atomicWeight);
		for (VectorState goal : graph.goalContextMap.keySet()) {
			getContext(goal).addAll(graph.goalContextMap.get(goal));
		}
	}
	
	public VectorState getContextualGoal(IndexedVectorState context) {
//		int minDis = Integer.MAX_VALUE;
		HashMap<VectorState, Integer> cachedDistances = new HashMap<VectorState, Integer>();
		VectorState best = null;
		double bestAvgDis = Integer.MAX_VALUE;
		
		for (VectorState goal : goalContextMap.keySet()) {
			List<IndexedVectorState> list = goalContextMap.get(goal);
			if (list.size() < 2) continue;
			double totalDis = 0;
			for (IndexedVectorState state : list) {
				state.cache();
				Integer dis = cachedDistances.get(state);
				if (dis == null) {
					dis = IndexedVectorState.distance(context, state);
					cachedDistances.put(state, dis);
				}
				totalDis += dis;
//				minDis = Math.min(minDis, dis);
			}
			double avg = totalDis / list.size();
			if (avg < bestAvgDis) {
				best = goal;
				bestAvgDis = avg;
			}
		}
		
//		int bestVotes = Integer.MIN_VALUE;
//		for (VectorState goal : goalContextMap.keySet()) {
//			int votes = 0;
//			for (IndexedVectorState state : goalContextMap.get(goal)) {
//				int dis = cachedDistances.get(state);
//				if (dis == minDis) votes++;
//			}
//			if (votes > bestVotes) {
//				bestVotes = votes;
//				best = goal;
//			}
//		}
//		System.out.println(minDis + " " + bestVotes);
		
		return best;
	}
		
	public void exportGoalContexts(PrintStream out) throws FileNotFoundException {
		List<VectorState> goals = new ArrayList<VectorState>();
		goals.addAll(goalContextMap.keySet());
		Collections.sort(goals, new Comparator<VectorState>() {
			@Override
			public int compare(VectorState o1, VectorState o2) {
				return -Integer.compare(
						goalContextMap.get(o1).size(), 
						goalContextMap.get(o2).size());
			}
		});
		
		for (VectorState state : goals) {
			final HashMap<VectorState, Integer> counts = new HashMap<VectorState, Integer>();
			List<IndexedVectorState> contexts = goalContextMap.get(state);
			for (IndexedVectorState context : contexts) {
				int count = 0;
				if (counts.containsKey(context)) {
					count = counts.get(context);
				}
				counts.put(context, count + 1);
			}
			TreeMap<VectorState, Integer> sortedCounts = new TreeMap<VectorState, Integer>(new Comparator<VectorState>() {
				@Override
				public int compare(VectorState o1, VectorState o2) {
					if (counts.get(o1) > counts.get(o2)) {
						return -1;
					} else {
						return 1;
					}
				}
			});
			sortedCounts.putAll(counts);
			out.println(state + " (" + contexts.size() + "): ");
			for (VectorState context : sortedCounts.keySet()) {
				out.println("\t" + counts.get(context) + ": " + context);
			}
			out.println();
		}
	}
	
	public void generateEdges() {
		generateEdges(1, 0.15f);
	}
	
	public void generateEdges(int maxDis, double maxNDis) {
		int n = vertices.size();
		VectorState[] vertices = this.vertices.toArray(new VectorState[n]);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < i; j++) {
				VectorState a = vertices[i];
				VectorState b = vertices[j];
				Tuple<Integer,Double> distances = VectorState.distances(a, b);
				if (distances.x <= maxDis || distances.y <= maxNDis) {
					if (!hasEdge(a, b)) addOrGetEdge(a, b, null).synthetic = true;
					if (!hasEdge(b, a)) addOrGetEdge(b, a, null).synthetic = true;
				}
			}
		}
	}
	
	public VectorState getNearestNeighbor(VectorState state, int maxDis) {
		Vertex<VectorState> nearest = null;
		int nearestDistance = maxDis;
		for (Vertex<VectorState> vertex : vertexMap.values()) {
			if (vertex.equals(state)) continue; // Ignore exact matches (they're not neighbors)
			int distance = VectorState.distance(state, vertex.data);
			if (distance > nearestDistance) continue;
			if (distance == nearestDistance && nearest != null && 
					vertex.bValue <= nearest.bValue) {
				continue;
			}
			
			nearest = vertex;
			nearestDistance = distance;
		}
		return nearest == null ? null : nearest.data;
	}
	
}
