package edu.isnap.ctd.graph.vector;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import edu.isnap.ctd.graph.InteractionGraph;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.util.Alignment;
import edu.isnap.node.Node;

@SuppressWarnings("deprecation")
public class VectorGraph extends InteractionGraph<VectorState> {

	public final Node rootPathEnd;

	private final HashMap<VectorState, List<IndexedVectorState>> goalContextMap =
			new HashMap<>();

	private final transient HashMap<VectorState, Double> tmpGoalValues = new HashMap<>();

	private int clusters;

	public void setClusters(int clusters) {
		this.clusters = clusters;
	}

	public int getClusters() {
		return clusters;
	}

	public boolean isScriptGraph(HintConfig config) {
		return config.isScript(rootPathEnd.type());
	}

	@SuppressWarnings("unused")
	private VectorGraph() {
		this(null);
	}

	public VectorGraph(Node rootPathEnd) {
		this.rootPathEnd = rootPathEnd;
	}

	public boolean setGoal(VectorState goal, IndexedVectorState context) {
		List<IndexedVectorState> list = getContext(goal);
		list.add(context);

		return super.setGoal(goal, true);
	}

	private List<IndexedVectorState> getContext(VectorState goal) {
		List<IndexedVectorState> list = goalContextMap.get(goal);
		if (list == null) {
			list = new ArrayList<>();
			goalContextMap.put(goal, list);
		}
		return list;
	}

	@Override
	public boolean addVertex(VectorState v) {
		if (v == null) throw new RuntimeException("Vertex cannot be null");
		return super.addVertex(v);
	}

	public void addGraph(VectorGraph graph, boolean atomicWeight) {
		super.addGraph(graph, atomicWeight);
		for (VectorState goal : graph.goalContextMap.keySet()) {
			getContext(goal).addAll(graph.goalContextMap.get(goal));
		}
	}

	@Override
	protected double getGoalValue(Vertex<VectorState> vertex) {
		if (tmpGoalValues.containsKey(vertex.data)) {
			return tmpGoalValues.get(vertex.data);
		}
		return getUncontextualGoalValue(vertex);
	}

	private double getUncontextualGoalValue(Vertex<VectorState> vertex) {
		return super.getGoalValue(vertex);
	}

	private double getUncontextualGoalValue(VectorState state) {
		return getUncontextualGoalValue(vertexMap.get(state));
	}

	public VectorState getGoalState(VectorState state, IndexedVectorState context,
			HintConfig config) {
		if (state == null) return null;
		contextualBellmanBackup(state, context, config);

		if (!connectedToGoal(state)) {
			return getGoalState(getNearestNeighbor(state, config, true), context, config);
		}
		List<VectorState> goalPath = getMDPGoalPath(state);
		if (goalPath == null) return null;
		return goalPath.get(goalPath.size() - 1);
	}

	public VectorState getHint(VectorState state, IndexedVectorState context, HintConfig config) {
		contextualBellmanBackup(state, context, config);

		boolean connectedToGoal = connectedToGoal(state);

		if (!isScriptGraph(config)) {
			if (!connectedToGoal) {
				// Look for a nearest neighbor in the graph
				VectorState nearestNeighbor = getNearestNeighbor(state, config, true);
				if (nearestNeighbor == null) return null;
				// If we find one, get the hint from there
				VectorState hintState = getHint(nearestNeighbor, context, config);
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
				nearestNeighbor = getNearestNeighbor(state, config, true);
			}
			return getSmartHint(state, nearestNeighbor);
		}
		return null;
	}

	private boolean connectedToGoal(VectorState state) {
		Vertex<VectorState> vertex = vertexMap.get(state);
		boolean connectedToGoal = vertex != null && vertex.bValue > 0;
		return connectedToGoal;
	}


	private VectorState getSmartHint(VectorState state, VectorState nearestNeighbor) {
		List<VectorState> goalPath = getMDPGoalPath(nearestNeighbor);
		if (goalPath == null) return null;
		VectorState goal = goalPath.get(goalPath.size() - 1);

		List<String> stateItems = new LinkedList<>();
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
				// If we added, rearrange for free
				edits += Alignment.doEdits(stateItems, goalItems, Alignment.MoveEditor, 1);
			}
			// Delete gets an extra edit
			edits += Alignment.doEdits(stateItems, goalItems, Alignment.DeleteEditor, 2 - edits);

			if (edits > 0) {
				VectorState hint = new VectorState(stateItems);
				if (hint.equals(state)) continue;
				return hint;
			}
		}
		return goal;
	}

	private List<VectorState> getMDPGoalPath(VectorState state) {
		List<VectorState> path = new LinkedList<>();
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

	private void contextualBellmanBackup(VectorState state, IndexedVectorState context,
			HintConfig config) {
		HashMap<VectorState, Double> cachedDistances = new HashMap<>();

		tmpGoalValues.clear();

		HashMap<VectorState, Double> generatedGoalValues = generateGoalValues(state, config);

		// Here we additionally weight the goals based on the similarity between their respective
		// contexts and the student's current context
		double nextBest = Double.MIN_VALUE;
		double bestAvgDis = Double.MIN_VALUE;
		for (VectorState goal : goalContextMap.keySet()) {
			List<IndexedVectorState> list = goalContextMap.get(goal);

			Double baseValue = generatedGoalValues.get(goal);

			// If this goal has too low weight or no base value, don't use it
			if (list.size() < config.pruneGoals || baseValue == null) {
				tmpGoalValues.put(goal, 0.0);
				continue;
			}

			double weight = 0;
			for (IndexedVectorState goalContext : list) {
				goalContext.cache();
				Double dis = cachedDistances.get(goalContext);
				if (dis == null) {
					dis = IndexedVectorState.distance(context, goalContext);
					cachedDistances.put(goalContext, dis);
				}
				weight += config.getDistanceWeight(dis);
			}
			weight /= list.size();
			tmpGoalValues.put(goal, weight * baseValue);
			if (weight > nextBest) {
				nextBest = weight;
			}
			if (weight > bestAvgDis) {
				nextBest = bestAvgDis;
				bestAvgDis = weight;
			}
		}

		bellmanBackup(config.pruneGoals);
	}

	/** Get the base value for each goal state based on the student's current state. */
	private HashMap<VectorState, Double> generateGoalValues(VectorState state, HintConfig config) {
		HashMap<VectorState, Double> goalValues = new HashMap<>();

		if (isScriptGraph(config)) {
			// If we're filtering by progress (only for scripts), we only consider goal states that
			// the student has made the most progress towards
			HashMap<VectorState, Integer> progressMap = new HashMap<>();

			int maxProgress = 0;
			for (VectorState goal : goalContextMap.keySet()) {
				// We ignore any goals that would be ignored by the bellman backup
				if (vertexMap.get(goal).goalCount() < config.pruneGoals) {
					progressMap.put(goal, 0);
					continue;
				}
				int progress = Alignment.getProgress(state.items, goal.items,
						config.progressOrderFactor, 1);
				progressMap.put(goal, progress);
				// Find the goal state(s) that the student has made the most progress towards
				maxProgress = Math.max(maxProgress, progress);
			}

			// Only include goals states that have at least that much progress (there may be
			// a tie among multiple goal states for most progress)
			for (VectorState goal : goalContextMap.keySet()) {
				int progress = progressMap.get(goal);
				if (progress < maxProgress) continue;
				goalValues.put(goal, getUncontextualGoalValue(goal));
			}
		} else {
			// Otherwise include all goal states
			for (VectorState goal : goalContextMap.keySet()) {
				goalValues.put(goal, getUncontextualGoalValue(goal));
			}
		}

		return goalValues;
	}

	public void exportGoals(PrintStream out) throws FileNotFoundException {
		out.println(this.rootPathEnd.root());
		out.println("Clusters: " + clusters);
		out.println();

		List<VectorState> goals = new ArrayList<>();
		goals.addAll(goalContextMap.keySet());
		Collections.sort(goals, new Comparator<VectorState>() {
			@Override
			public int compare(VectorState o1, VectorState o2) {
				return -Double.compare(getUncontextualGoalValue(o1), getUncontextualGoalValue(o2));
			}
		});

		for (VectorState state : goals) {
			final HashMap<VectorState, Integer> counts =
					new HashMap<>();
			List<IndexedVectorState> contexts = goalContextMap.get(state);
			for (IndexedVectorState context : contexts) {
				int count = 0;
				if (counts.containsKey(context)) {
					count = counts.get(context);
				}
				counts.put(context, count + 1);
			}
			TreeMap<VectorState, Integer> sortedCounts =
					new TreeMap<>(new Comparator<VectorState>() {
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
			out.printf("%s (%.03f):\n", state, getUncontextualGoalValue(state));
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

	public VectorState getNearestNeighbor(VectorState state, HintConfig config,
			boolean connectToGoalOnly) {
		int scale = 100;
		// We will calculate distances scaled by a large constant to allow for fractional distance
		// weights. We want the distance comparison to first look at additions, then deletions
		// but not allow substitutions.
		int maxDis = config.maxNN * scale;
		Vertex<VectorState> nearest = null;
		int nearestDistance = maxDis;
		for (Vertex<VectorState> vertex : vertexMap.values()) {
			// Ignore exact matches (they're not neighbors)
			if (vertex.data == null || vertex.data.equals(state)) continue;
			if (connectToGoalOnly && !connectedToGoal(vertex.data)) continue;
			// Favor deletions over insertions, but forbid subs
			int distance = VectorState.distance(state, vertex.data, scale, 1, 10000);
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
		// TODO: This consistently returns negative values and certainly should not
		Vertex<VectorState> vertex = vertexMap.get(children);
		if (vertex == null || vertex.weight() == 0) return 0;
		return (vertex.weight() - outWeight(vertex.data, true, true)) /
				(double)vertex.weight();
	}

	public int getMedianPositiveChildCountInGoals(String childType) {
		List<Integer> counts = new LinkedList<>();
		for (VectorState goal : goalContextMap.keySet()) {
			int count = 0;
			for (String item : goal.items) {
				if  (childType.equals(item)) count++;
			}
			if (count > 0) {
				int times = vertexMap.get(goal).goalCount();
				for (int i = 0; i < times; i++) {
					counts.add(count);
				}
			}
		}
		if (counts.size() == 0) return 0;
		Collections.sort(counts);
		return counts.get(counts.size() / 2);
	}

}
