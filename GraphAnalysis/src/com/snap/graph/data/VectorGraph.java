package com.snap.graph.data;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import com.snap.graph.Alignment;

public class VectorGraph extends OutGraph<VectorState> {

	private final HashMap<VectorState, List<IndexedVectorState>> goalContextMap =
			new HashMap<VectorState, List<IndexedVectorState>>();

	private final transient HashMap<VectorState, Double> tmpGoalValues =
			new HashMap<VectorState, Double>();

	public boolean setGoal(VectorState goal, IndexedVectorState context) {
		List<IndexedVectorState> list = getContext(goal);
		list.add(context);

		return super.setGoal(goal, true);
	}

	@Override
	public boolean addVertex(VectorState v) {
		if (v == null) throw new RuntimeException("Vertex cannot be null");
		return super.addVertex(v);
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

	@Override
	protected double getGoalValue(Vertex<VectorState> vertex) {
		double value = 0;
		if (tmpGoalValues.containsKey(vertex.data)) {
			value = tmpGoalValues.get(vertex.data);
		} else {
			return super.getGoalValue(vertex);
		}
		return value;
	}

	public VectorState getGoalState(VectorState state, IndexedVectorState context, int maxNN, int minGoal) {
		if (state == null) return null;
		contextualBellmanBackup(context, minGoal);

		if (!connectedToGoal(state)) {
			return getGoalState(getNearestNeighbor(state, maxNN, true), context, maxNN, minGoal);
		}
		List<VectorState> goalPath = getMDPGoalPath(state);
		if (goalPath == null) return null;
		return goalPath.get(goalPath.size() - 1);
	}

	public VectorState getHint(VectorState state, IndexedVectorState context, int maxNN, int minGoal, boolean naturalEdges) {
		contextualBellmanBackup(context, minGoal);

		boolean connectedToGoal = connectedToGoal(state);

		if (naturalEdges) {
			if (!connectedToGoal) {
				// Look for a nearest neighbor in the graph
				VectorState nearestNeighbor = getNearestNeighbor(state, maxNN, true);
				if (nearestNeighbor == null) return null;
				// If we find one, get the hint from there
				VectorState hintState = getHint(nearestNeighbor, context, maxNN, minGoal, naturalEdges);
				if (hintState != null) {
					// If it exists, and it's at least as close as the nearest neighbor...
					int disNN = VectorState.distance(state, nearestNeighbor);
					int disHF = VectorState.distance(state, hintState);
					if (disHF <= disNN) {
						// Use it instead
						return hintState;
					}
				}
				// Otherwise hint to go to the nearest neighbor
				return nearestNeighbor;
			}

			List<Edge<VectorState, Void>> edges = fromMap.get(state);
			if (edges == null) return null;
			for (Edge<VectorState, Void> edge : edges) {
				if (edge.bBest) {
					return edge.to;
				}
			}
		} else {
			VectorState nearestNeighbor = state;
			if (!connectedToGoal) {
				nearestNeighbor = getNearestNeighbor(state, maxNN, true);
			}
			return getSmartHint(state, nearestNeighbor);
		}
		return null;
	}

	private boolean connectedToGoal(VectorState state) {
		Vertex<VectorState> vertex = vertexMap.get(state);
		// TODO: Technically it could just be /very/ far away... but maybe that's ok
		boolean connectedToGoal = vertex != null && vertex.bValue > 0;
		return connectedToGoal;
	}


	private VectorState getSmartHint(VectorState state, VectorState nearestNeighbor) {
		List<VectorState> goalPath = getMDPGoalPath(nearestNeighbor);
		if (goalPath == null) return null;
		VectorState goal = goalPath.get(goalPath.size() - 1);

		List<String> stateItems = new LinkedList<String>();
		for (String item : state.items) stateItems.add(item);

		String[] goalItems = goal.items;
		int index = 1;
		while (index < goalPath.size()) {
			String[] nextItems = goalPath.get(index++).items;
			int edits = 0;

			edits += Alignment.doEdits(stateItems, goalItems, Alignment.MoveEditor, 1 - edits);
			int addEdits = Alignment.doEdits(stateItems, nextItems, Alignment.AddEditor, 1 - edits);
			edits += addEdits;
			if (addEdits > 0) {
				edits += Alignment.doEdits(stateItems, goalItems, Alignment.MoveEditor, 1); // If we added, rearrange for free
			}
			edits += Alignment.doEdits(stateItems, goalItems, Alignment.DeleteEditor, 2 - edits); // delete gets an extra edit

			if (edits > 0) {
				VectorState hint = new VectorState(stateItems);
				if (hint.equals(state)) continue;
//				System.out.printf("State: %s\nNeighbor: %s\nNext: %s\nGoal: %s\nHint: %s\n\n", state, nearestNeighbor, Arrays.toString(nextItems), goal, hint);
				return hint;
			}
		}
//		System.out.printf("State: %s\nNeighbor: %s\nGoal: %s\n\n", state, nearestNeighbor, goal);
		return goal;
	}

	private List<VectorState> getMDPGoalPath(VectorState state) {
		List<VectorState> path = new LinkedList<VectorState>();
		while (true) {
			path.add(state);
			List<Edge<VectorState, Void>> edges = fromMap.get(state);
			boolean found = false;
			if (edges != null) {
				for (Edge<VectorState, Void> edge : edges) {
					if (edge.bBest) {
						found = true;
						state = edge.to;
						break;
					}
				}
			}
			if (!found) break;
		}
		if (!isGoal(state)) return null;
		return path;
	}

	private void contextualBellmanBackup(IndexedVectorState context, int minGoal) {
		HashMap<VectorState, Double> cachedDistances = new HashMap<VectorState, Double>();

		tmpGoalValues.clear();

		for (VectorState goal : goalContextMap.keySet()) {
			List<IndexedVectorState> list = goalContextMap.get(goal);
			if (list.size() < 2) continue;
			for (IndexedVectorState state : list) {
				state.cache();
				Double dis = cachedDistances.get(state);
				if (dis == null) {
					dis = IndexedVectorState.distance(context, state);
					cachedDistances.put(state, dis);
				}
			}
		}

		double nextBest = Double.MIN_VALUE;
		double bestAvgDis = Double.MIN_VALUE;
		for (VectorState goal : goalContextMap.keySet()) {
			List<IndexedVectorState> list = goalContextMap.get(goal);
			if (list.size() < 2) continue;

			double weight = 0;
			for (IndexedVectorState state : list) {
				state.cache();
				Double dis = cachedDistances.get(state);
				if (dis == null) {
					dis = IndexedVectorState.distance(context, state);
					cachedDistances.put(state, dis);
				}
				weight += 1 / Math.pow(0.5f + dis, 2);
			}
			tmpGoalValues.put(goal, weight * 10);
			if (weight > nextBest) {
				nextBest = weight;
			}
			if (weight > bestAvgDis) {
				nextBest = bestAvgDis;
				bestAvgDis = weight;
			}
		}

		bellmanBackup(minGoal);
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

	public void generateAndRemoveEdges(int maxAddDis, int maxKeepDis) {
		int n = vertices.size();
		VectorState[] vertices = this.vertices.toArray(new VectorState[n]);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < i; j++) {
				VectorState a = vertices[i];
				VectorState b = vertices[j];
				int distance = VectorState.distance(a, b);
				if (distance <= maxAddDis) {
					if (!hasEdge(a, b)) addOrGetEdge(a, b, null).synthetic = true;
					if (!hasEdge(b, a)) addOrGetEdge(b, a, null).synthetic = true;
				} else if (distance > maxKeepDis) {
					removeEdge(a, b);
					removeEdge(b, a);
				}
			}
		}
	}

	public VectorState getNearestNeighbor(VectorState state, int maxDis, boolean connectToGoalOnly) {
		// TODO: must be goalConnected, must be not rep, add = 1, del = 0.1, should have highest score
		maxDis *= 10; // scale to be an integer
		Vertex<VectorState> nearest = null;
		int nearestDistance = maxDis;
		for (Vertex<VectorState> vertex : vertexMap.values()) {
			if (vertex.data == null || vertex.equals(state)) continue; // Ignore exact matches (they're not neighbors)
			if (connectToGoalOnly && !connectedToGoal(vertex.data)) continue;
			int distance = VectorState.distance(state, vertex.data, 10, 1, 10000); // Favor deletions over insertions, but forbid subs
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

	public double getProportionStayed(VectorState children) {
		Vertex<VectorState> vertex = vertexMap.get(children);
		if (vertex == null || vertex.weight() == 0) return 0;
		return (vertex.weight() - outWeight(vertex.data, true, true)) / (double)vertex.weight();
	}

}
