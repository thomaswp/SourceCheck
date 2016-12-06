package edu.isnap.ctd.hint;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.ctd.graph.vector.IndexedVectorState;
import edu.isnap.ctd.graph.vector.VectorGraph;
import edu.isnap.ctd.graph.vector.VectorState;

/**
 * Class for handling the core logic of the CTD algorithm.
 */
public class HintMap {

	final HintConfig config;

	public HintConfig getHintConfig() {
		return config;
	}

	@SuppressWarnings("unused")
	private HintMap() {
		this(null);
	}

	public HintMap(HintConfig config) {
		this.config = config;
	}

	protected final HashMap<Node, VectorGraph> map =
			new HashMap<>();

	/**
	 * Gets the root path for the given Node, which contains only the nodes in the root path from
	 * this given node to its root. The Node returned is the end of the path, not the root.
	 */
	public static Node toRootPath(Node node) {
		if (node == null) return null;

		Node parent = toRootPath(node.parent);
		String type = node.type();
		Node child = new Node(parent, type);
		if (parent != null) parent.children.add(child);

		return child;
	}

	public void clear() {
		map.clear();
	}

	public void addEdge(Node from, Node to) {
		// The from Node can be null for new nodes, and is the assumed empty
		VectorState fromState = from == null ?
				VectorState.empty() : getVectorState(from);
		VectorState toState = getVectorState(to);
		// Don't include loops
		if (fromState.equals(toState)) return;
		getGraph(to).addEdge(fromState, toState);
	}

	private VectorGraph getGraph(Node node) {
		Node rootPathChild = toRootPath(node);
		Node rootPath = rootPathChild.root();
		VectorGraph graph = map.get(rootPath);
		if (graph == null) {
			graph = new VectorGraph(rootPathChild);
			map.put(rootPath, graph);
		}
		return graph;
	}

	private static VectorState getVectorState(Node node) {
		return new VectorState(getChildren(node));
	}

	private static List<String> getChildren(Node node) {
		List<String> children = new ArrayList<>();
		if (node == null) return children;
		for (Node child : node.children) {
			if ("null".equals(child.type())) continue;
			children.add(child.type());
		}
		return children;
	}

	public HintMap instance() {
		return new HintMap(config);
	}

	public void setSolution(Node solution) {
		solution.recurse(new Action() {
			@Override
			public void run(Node item) {
				VectorGraph graph = getGraph(item);
				VectorState children = getVectorState(item);
				// TODO: find a more elegant solution that doesn't involve this awkward cast
				if (!((Set<VectorState>) graph.vertices()).contains(children)) {
					graph.addVertex(children);
				}
				graph.setGoal(children, getContext(item));
			}
		});
	}

	private IndexedVectorState getContext(Node item) {
		return getContext(item, config.maxContextSiblings);
	}

	private IndexedVectorState getContext(Node item, int maxLength) {
		Node contextChild = item;
		while (contextChild.parent != null &&
				config.badContext.contains(contextChild.parent.type())) {
			contextChild = contextChild.parent;
		}
		int index = contextChild.index();
		return new IndexedVectorState(getChildren(contextChild.parent), index, maxLength);
	}

	public Iterable<VectorHint> getHints(Node node, int chain) {
		List<VectorHint> hints = new ArrayList<>();

		Node rootPath = toRootPath(node).root();
		VectorGraph graph = map.get(rootPath);
		if (graph == null) return hints;

		VectorState children = getVectorState(node);
		IndexedVectorState context = getContext(node);
		VectorState next = children;

		VectorState goal = graph.getGoalState(next, context, config);

		if (goal != null && config.straightToGoal.contains(node.type())) {
			next = goal;
		} else {
			for (int j = 0; j < chain; j++) {
				// Get the best successor state from our current state
				VectorState hint = graph.getHint(next, context, config);
				// If there is none, we stop where we are
				if (hint == null || hint.equals(next)) break;
				// Otherwise, chain to the next hint
				next = hint;
			}
			if (goal == null) goal = next;
		}

		double stayed = graph.getProportionStayed(children);
		boolean caution =
				graph.getGoalCount(children) >= config.pruneGoals &&
				stayed >= config.stayProportion;

//		if (node.hasType("script") && node.children.size() == 1) {// && stayed > 0) {
//			System.out.println(node);
//			graph.getProportionStayed(children);
//		}

		VectorHint hint = new VectorHint(node, rootPath.toString(), children, next, goal, caution);
		hints.add(hint);

		return hints;
	}

	public void finish() {
		for (VectorGraph graph : map.values()) {
			graph.prune(config.pruneNodes);
		}

		for (Node key : map.keySet()) {
			VectorGraph graph = map.get(key);
			if (!graph.hasGoal()) continue;
			Node parent = key.copy(false);
			// first go down the root path
			while (parent.children.size() == 1) {
				parent = parent.children.get(0);
			}

			// Record the type at the end of the root path
			String type = parent.type();
			parent = parent.parent;
			// then go back up at least one, possible more if there's a bad context
			while (parent != null && config.badContext.contains(parent.type())) {
				type = parent.type();
				parent = parent.parent;
			}
			if (parent == null) continue;
			parent.children.clear();

			VectorGraph parentGraph = map.get(parent.root());
			int clusterCount = parentGraph.getMedianPositiveChildCountInGoals(type);
			graph.setClusters(clusterCount);
		}

		for (VectorGraph graph : map.values()) {
			graph.generateAndRemoveEdges(config.maxEdgeAddDistance, config.maxEdgeDistance);
			graph.bellmanBackup(config.pruneGoals);
		}
	}

	public void addMap(HintMap hintMap) {
		HashMap<Node,VectorGraph> addMap = hintMap.map;
		for (Node rootpath : addMap.keySet()) {
			VectorGraph graph = addMap.get(rootpath);
			VectorGraph myGraph = map.get(rootpath);
			if (myGraph == null) {
				myGraph = new VectorGraph(graph.rootPathEnd);
				map.put(rootpath, myGraph);
			}
			myGraph.addGraph(graph, true);
		}
	}

	public void postProcess(List<VectorHint> hints) {
		// "Hash set" to compare by identity
		final Map<Node, Void> extraScripts = new IdentityHashMap<>();
		Map<VectorState, VectorHint> missingMap = new HashMap<>();

		// Find scripts that should be removed and don't give hints to their children
		for (VectorHint hint : hints) {
			int extraChildren = hint.from.countOf(config.script) -
					hint.goal.countOf(config.script);
			if (extraChildren > 0) {
				List<Integer> sizes = new LinkedList<>();
				for (Node child : hint.root.children) {
					sizes.add(child.children.size());
				}
				Collections.sort(sizes);
				int cutoff = sizes.get(extraChildren);
				// TODO: find a more comprehensive way of deciding on the primary script(s)
				for (Node child : hint.root.children) {
					if (child.hasType(config.script) && child.children.size() < cutoff) {
						extraScripts.put(child, null);
					}
				}
			}

		}

		// Remove the hints for the extra hints and make a map of missing blocks for the others
		List<VectorHint> toRemove = new LinkedList<>();
		for (VectorHint hint : hints) {
			if (hint.from.equals(hint.to) || hint.root.hasAncestor(new Predicate() {
				@Override
				public boolean eval(Node node) {
					return extraScripts.containsKey(node);
				}
			})) {
				toRemove.add(hint);
			} else {
				VectorState missingChildren = hint.getMissingChildren();
				// To benefit from a LinkHint, an existing hint must have some missing
				// children but it shouldn't be missing all of them (completely replaced)
				// unless it's empty to begin with, or a block hint
				if (missingChildren.length() > 0 && (
						!hint.root.hasType(config.script) ||
						hint.from.length() == 0 ||
						missingChildren.length() < hint.goal.length())) {
					missingMap.put(missingChildren, hint);
				}
			}
		}

		// Instead look for another hint that could use the blocks in these
		// scripts and make a LinkHint that points there
		List<VectorHint> toAdd = new LinkedList<>();
		for (VectorHint hint : hints) {
			if (extraScripts.containsKey(hint.root) &&
					hint.from.length() > 0) {

				VectorHint bestMatch = null;
				int bestUseful = 0;
				int bestDistance = Integer.MAX_VALUE;

				for (VectorState missing : missingMap.keySet()) {
					int useful = missing.overlap(hint.from);
					if (useful * config.linkUsefulRatio >= hint.from.length() &&
							useful >= bestUseful) {
						int distance = VectorState.distance(missing, hint.from);
						if (useful > bestUseful || distance < bestDistance) {
							bestUseful = useful;
							bestDistance = distance;
							bestMatch = missingMap.get(missing);
						}
					}
				}

				if (bestMatch != null) {
					// TODO: Why am I getting duplicate hints here (and likely elsewhere)?
					toAdd.add(new LinkHint(bestMatch, hint));
				}
			}
		}
		hints.addAll(toAdd);
		hints.removeAll(toRemove);
	}

	/**
	 * Saves graphs and other debugging files to the given directory for this HintGenerator.
	 * @param rootDir The root directory in which to save the files.
	 * @param minVertices The minimum number of vertices a graph must have to be saved.
	 * @throws FileNotFoundException
	 */
	public void saveGraphs(String rootDir, int minVertices)
			throws FileNotFoundException {
		for (Node node : map.keySet()) {
			VectorGraph graph = map.get(node);
			if (graph.nVertices() < minVertices) continue;
			if (!graph.hasGoal()) continue;

			graph.bellmanBackup(config.pruneGoals);
			Node child = node;
			String dir = rootDir;
			while (child.children.size() > 0) {
				dir += child.type() + "/";
				child = child.children.get(0);
			}
			new File(dir).mkdirs();
			File file = new File(dir, child.type());

			graph.export(new PrintStream(new FileOutputStream(file + ".graphml")), true,
					0, false, true);
			graph.exportGoals(new PrintStream(file + ".txt"));
		}
	}

}
