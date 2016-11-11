package edu.isnap.ctd.hint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.graph.Node.Predicate;
import edu.isnap.ctd.graph.vector.IndexedVectorState;
import edu.isnap.ctd.graph.vector.VectorGraph;
import edu.isnap.ctd.graph.vector.VectorState;
import edu.isnap.ctd.hint.Canonicalization.InvertOp;
import edu.isnap.ctd.hint.Canonicalization.SwapArgs;
import edu.isnap.ctd.util.StringHashable;

/**
 * Class for handling the core logic of the CTD algorithm.
 */
public class HintFactoryMap implements HintMap {

	final HintConfig config;

	@Override
	public HintConfig getHintConfig() {
		return config;
	}

	@SuppressWarnings("unused")
	private HintFactoryMap() {
		this(null);
	}

	public HintFactoryMap(HintConfig config) {
		this.config = config;
	}

	public final HashMap<Node, VectorGraph> map =
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

	@Override
	public void clear() {
		map.clear();
	}

	@Override
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

	@Override
	public HintMap instance() {
		return new HintFactoryMap(config);
	}

	@Override
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

	@Override
	public Iterable<Hint> getHints(Node node, int chain) {
		List<Hint> hints = new ArrayList<>();

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

	@Override
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

	@Override
	public void addMap(HintMap hintMap) {
		HashMap<Node,VectorGraph> addMap = ((HintFactoryMap) hintMap).map;
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

	@Override
	public void postProcess(List<Hint> hints) {
		final Set<Node> extraScripts = new HashSet<>();
		Map<VectorState, VectorHint> missingMap = new HashMap<>();

		// Find scripts that should be removed and don't give hints to their children
		for (Hint hint : hints) {
			if (!(hint instanceof VectorHint)) return;
			VectorHint vHint = (VectorHint) hint;

			int extraChildren = vHint.from.countOf(config.script) -
					vHint.goal.countOf(config.script);
			if (extraChildren > 0) {
				List<Integer> sizes = new LinkedList<>();
				for (Node child : vHint.root.children) {
					sizes.add(child.children.size());
				}
				Collections.sort(sizes);
				int cutoff = sizes.get(extraChildren);
				// TODO: find a more comprehensive way of deciding on the primary script(s)
				for (Node child : vHint.root.children) {
					if (child.hasType(config.script) && child.children.size() < cutoff) {
						extraScripts.add(child);
					}
				}
			}

		}

		// Remove the hints for the extra hints and make a map of missing blocks for the others
		List<Hint> toRemove = new LinkedList<>();
		for (Hint hint : hints) {
			if (!(hint instanceof VectorHint)) return;
			VectorHint vHint = (VectorHint) hint;

			if (vHint.from.equals(vHint.to) || vHint.root.hasAncestor(new Predicate() {
				@Override
				public boolean eval(Node node) {
					return extraScripts.contains(node);
				}
			})) {
				toRemove.add(hint);
			} else {
				VectorState missingChildren = vHint.getMissingChildren();
				// To benefit from a LinkHint, an existing hint must have some missing
				// children but it shouldn't be missing all of them (completely replaced)
				// unless it's empty to begin with, or a block hint
				if (missingChildren.length() > 0 && (
						!vHint.root.hasType(config.script) ||
						vHint.from.length() == 0 ||
						missingChildren.length() < vHint.goal.length())) {
					missingMap.put(missingChildren, vHint);
				}
			}
		}

		// Instead look for another hint that could use the blocks in these
		// scripts and make a LinkHint that points there
		List<Hint> toAdd = new LinkedList<>();
		for (Hint hint : hints) {
			if (!(hint instanceof VectorHint)) return;
			VectorHint canceledHint = (VectorHint) hint;

			if (extraScripts.contains(canceledHint.root) &&
					canceledHint.from.length() > 0) {

				VectorHint bestMatch = null;
				int bestUseful = 0;
				int bestDistance = Integer.MAX_VALUE;

				for (VectorState missing : missingMap.keySet()) {
					int useful = missing.overlap(canceledHint.from);
					if (useful * config.linkUsefulRatio >= canceledHint.from.length() &&
							useful >= bestUseful) {
						int distance = VectorState.distance(missing, canceledHint.from);
						if (useful > bestUseful || distance < bestDistance) {
							bestUseful = useful;
							bestDistance = distance;
							bestMatch = missingMap.get(missing);
						}
					}
				}

				if (bestMatch != null) {
					toAdd.add(new LinkHint(bestMatch, canceledHint));
				}
			}
		}
		hints.addAll(toAdd);
		hints.removeAll(toRemove);
	}

	public static class LinkHint extends VectorHint {

		public final Node oldRoot;
		public final VectorState oldFrom;
		public final String oldRootPath;

		public LinkHint(VectorHint mainHint, VectorHint oldHint) {
			super(mainHint.root, mainHint.rootPathString, mainHint.from,
					mainHint.goal.limitTo(mainHint.from, oldHint.from),
					mainHint.goal, mainHint.caution);
			oldRoot = oldHint.root;
			oldFrom = oldHint.from;
			oldRootPath = oldHint.rootPathString;
		}

		@Override
		public String from() {
			return super.from() + " and " + oldRootPath + ": " + oldFrom;
		}

		@Override
		public String to() {
			return super.to() + " and " + oldRootPath + ": []";
		}

		@Override
		protected Map<String, String> dataMap() {
			Map<String, String> map =  super.dataMap();
			map.put("oldRoot", getNodeReference(oldRoot));
			map.put("oldFrom", oldFrom.toJson(swapArgs));
			map.put("oldTo", VectorState.empty().toJson());
			return map;
		}

		@Override
		public Node outcome() {
			throw new UnsupportedOperationException();
		}
	}

	public static class VectorHint extends StringHashable implements Hint {

		public final Node root;
		public final String rootPathString;
		public final VectorState from, to, goal;
		public final boolean caution;

		protected final boolean swapArgs;

		public VectorHint(Node root, String rootPathString, VectorState from, VectorState to,
				VectorState goal, boolean caution) {
			this.root = root;
			this.rootPathString = rootPathString;
			this.from = from;
			this.to = to;
			this.goal = goal;
			this.caution = caution;

			boolean swap = false;
			for (Canonicalization c : root.canonicalizations) {
				if (c instanceof InvertOp || c instanceof SwapArgs) {
					swap = true;
					break;
				}
			}
			this.swapArgs = swap;
		}

		@Override
		protected boolean autoCache() {
			return true;
		}

		@Override
		public String from() {
			return rootPathString + ": " + from;
		}

		@Override
		public String to() {
			return rootPathString + ": " + to;
		}

		@Override
		public String data() {
			String data = "{";
			for (Entry<String, String> entry : dataMap().entrySet()) {
				if (data.length() > 1) data += ", ";
				data += String.format("\"%s\": %s", entry.getKey(), entry.getValue());
			}
			data += "}";
			return data;
		}

		protected Map<String, String> dataMap() {
			HashMap<String, String> map = new HashMap<>();
			map.put("root", getNodeReference(root));
			map.put("from", from.toJson(swapArgs));
			map.put("to", to.toJson(swapArgs));
			map.put("goal", goal.toJson(swapArgs));
			map.put("caution", String.valueOf(caution));
			return map;
		}

		protected static String getNodeReference(Node node) {
			if (node == null) return null;

			String label = node.type();
			for (Canonicalization c : node.canonicalizations) {
				if (c instanceof InvertOp) {
//					System.out.println("Invert: " + node);
					label = ((InvertOp) c).name;
					break;
				}
			}

			int index = node.index();
			if (node.parent != null) {
				for (Canonicalization c : node.parent.canonicalizations) {
					if (c instanceof SwapArgs) {
//						System.out.println("Swapping children of: " + node.parent);
						index = node.parent.children.size() - 1 - index;
						break;
					}
				}
			}

			String parent = getNodeReference(node.parent);

			return String.format("{\"label\": \"%s\", \"index\": %d, \"parent\": %s}",
					label, index, parent);
		}

		@Override
		protected String toCanonicalStringInternal() {
			return data();
		}

		@Override
		public Node outcome() {
			return applyHint(root, to.items);
		}

		public VectorState getMissingChildren() {
			List<String> missing = new LinkedList<>();
			for (String i : goal.items) missing.add(i);
			for (String i : from.items) missing.remove(i);
			return new VectorState(missing);
		}

		public static Node applyHint(Node root, String[] to) {
			Node nRoot = root.copy(false);

			List<Node> children = new ArrayList<>();
			children.addAll(nRoot.children);

			nRoot.children.clear();
			for (String type : to) {
				boolean added = false;
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i).hasType(type)) {
						added = true;
						nRoot.children.add(children.remove(i));
						break;
					}
				}
				if (!added) {
					nRoot.children.add(new Node(nRoot, type));
				}
			}

			return nRoot;
		}

	}

}
