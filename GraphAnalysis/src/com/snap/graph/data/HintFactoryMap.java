package com.snap.graph.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.snap.data.Canonicalization;
import com.snap.data.Canonicalization.InvertOp;
import com.snap.data.Canonicalization.SwapArgs;
import com.snap.graph.data.Node.Action;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class HintFactoryMap implements HintMap {

	// All "magic" constants used in the algorithm
	// TODO: Make these into a config file

	// When at least this proportion of visitors to a state finished there,
	// we flag hints to leave it with caution
	private final static double STAY_PROPORTION = 0.75;
	// We prune out states with weight less than this
	private final static int PRUNE_NODES = 2;
	// We prune out goals with fewer students than this finishing there
	private final static int PRUNE_GOALS = 2;
	// To hint towards a nearby neighbor, it must be less than this distance
	// from the student's current state
	private final static int MAX_NN = 3;
	// We add synthetic edges between nodes with distance no more than this
	private final static int MAX_EDGE_ADD_DISTANCE = 1;
	// We prune edges between states with distance greater than this
	private final static int MAX_EDGE_DISTANCE = 2;
	// The maximum number of siblings to look at at either end when considering context
	private final static int MAX_CONTEXT_SIBLINGS = 3;
	// Ratio of unused to used blocks in a side-script for it to used in  a LinkHint
	private static final int LINK_USEFUL_RATIO = 2;

	// Code elements that have exactly one script child or unordered children and
	// therefore should not have their children used as context
	private final static HashSet<String> BAD_CONTEXT = new HashSet<String>();
	static {
		for (String c : new String[] {
				// These control structures hold exactly one script
				"doIf",
				"doUntil",
				// Sprites' children are unordered
				"sprite",
		}) {
			BAD_CONTEXT.add(c);
		}
	}

	private final static String SCRIPT = "script";

	public final HashMap<Node, VectorGraph> map =
			new HashMap<Node, VectorGraph>();

	public static Node toBackbone(Node node) {
		return toBackbone(node, false);
	}

	public static Node toBackbone(Node node, boolean indices) {
		if (node == null) return null;

		Node parent = toBackbone(node.parent, indices);
		String type = node.type();
		if (indices && node.parent != null) {
			int index = 0;
			List<Node> siblings = node.parent.children;
			for (int i = 0; i < siblings.size(); i++) {
				Node sibling = siblings.get(i);
				if (sibling == node) break;
				if (sibling.type().equals(type)) index++;
			}
			type += index;
		}
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
		Node backbone = toBackbone(node).root();
		VectorGraph graph = map.get(backbone);
		if (graph == null) {
			graph = new VectorGraph();
			map.put(backbone, graph);
		}
		return graph;
	}

	private static VectorState getVectorState(Node node) {
		return new VectorState(getChildren(node));
	}

	private static List<String> getChildren(Node node) {
		List<String> children = new ArrayList<String>();
		if (node == null) return children;
		for (Node child : node.children) {
			if ("null".equals(child.type())) continue;
			children.add(child.type());
		}
		return children;
	}

	@Override
	public HintMap instance() {
		return new HintFactoryMap();
	}

	@Override
	public void setSolution(Node solution) {
		solution.recurse(new Action() {
			@Override
			public void run(Node item) {
				if (item.children.size() == 0) return;
				VectorGraph graph = getGraph(item);
				VectorState children = getVectorState(item);
				if (!graph.vertices.contains(children)) {
					graph.addVertex(children);
				}
				graph.setGoal(children, getContext(item));
			}
		});
	}

	private static IndexedVectorState getContext(Node item) {
		return getContext(item, MAX_CONTEXT_SIBLINGS);
	}

	private static IndexedVectorState getContext(Node item, int maxLength) {
		Node contextChild = item;
		while (contextChild.parent != null &&
				BAD_CONTEXT.contains(contextChild.parent.type())) {
			contextChild = contextChild.parent;
		}
		int index = contextChild.index();
		return new IndexedVectorState(getChildren(contextChild.parent), index, maxLength);
	}

	@Override
	public Iterable<Hint> getHints(Node node, int chain) {
		List<Hint> hints = new ArrayList<Hint>();

		Node backbone = toBackbone(node).root();
		VectorGraph graph = map.get(backbone);
		if (graph == null) return hints;

		boolean useGraph = !node.hasType(SCRIPT);

		VectorState children = getVectorState(node);
		IndexedVectorState context = getContext(node);
		VectorState next = children;

		for (int j = 0; j < chain; j++) {
			// Get the best successor state from our current state
			VectorState hint = graph.getHint(next, context, MAX_NN, PRUNE_GOALS, useGraph);
			// If there is none, we stop where we are
			if (hint == null || hint.equals(next)) break;
			// Otherwise, chain to the next hint
			next = hint;
		}

		VectorState goal = graph.getGoalState(next, context, MAX_NN, PRUNE_GOALS);
		if (goal == null) goal = next;

		double stayed = graph.getProportionStayed(children);
		boolean caution =
				graph.getGoalCount(children) >= PRUNE_GOALS &&
				stayed >= STAY_PROPORTION;

		VectorHint hint = new VectorHint(node, backbone.toString(), children, next, goal, caution);
		hints.add(hint);

		return hints;
	}

	@Override
	public void finish() {
		for (VectorGraph graph : map.values()) {
			graph.prune(PRUNE_NODES);
			graph.generateAndRemoveEdges(MAX_EDGE_ADD_DISTANCE, MAX_EDGE_DISTANCE);
			graph.bellmanBackup(PRUNE_GOALS);
		}
	}

	@Override
	public void addMap(HintMap hintMap) {
		HashMap<Node,VectorGraph> addMap = ((HintFactoryMap) hintMap).map;
		for (Node backbone : addMap.keySet()) {
			VectorGraph graph = addMap.get(backbone);
			VectorGraph myGraph = map.get(backbone);
			if (myGraph == null) {
				myGraph = new VectorGraph();
				map.put(backbone, myGraph);
			}
			myGraph.addGraph(graph, true);
		}
	}

	@Override
	public void postProcess(List<Hint> hints) {
		Set<Node> extraScripts = new HashSet<>();
		Map<VectorState, VectorHint> missingMap = new HashMap<>();

		// Find scripts that should be removed and don't give hints to their children
		for (Hint hint : hints) {
			if (!(hint instanceof VectorHint)) return;
			VectorHint vHint = (VectorHint) hint;

			int extraChildren = vHint.from.countOf(SCRIPT) - vHint.goal.countOf(SCRIPT);
			if (extraChildren > 0) {
				List<Integer> sizes = new LinkedList<>();
				for (Node child : vHint.root.children) {
					sizes.add(child.children.size());
				}
				Collections.sort(sizes);
				int cutoff = sizes.get(extraChildren);
				// TODO: find a more comprehensive way of deciding on the primary script(s)
				for (Node child : vHint.root.children) {
					if (child.hasType(SCRIPT) && child.children.size() < cutoff) {
						extraScripts.add(child);
					}
				}
			}

		}

		// Remove the hints for the extra hints and make a map of missing blocks for the
		// others
		List<Hint> toRemove = new LinkedList<>();
		for (Hint hint : hints) {
			if (!(hint instanceof VectorHint)) return;
			VectorHint vHint = (VectorHint) hint;

			if (extraScripts.contains(vHint.root)) {
				toRemove.add(hint);
			} else {
				VectorState missingChildren = vHint.getMissingChildren();
				// To benefit from a LinkHint, an existing hint must have some missing
				// children but it shouldn't be missing all of them (completely replaced)
				// unless it's empty to begin with
				if (missingChildren.length() > 0 && (vHint.from.length() == 0 ||
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
					if (useful * LINK_USEFUL_RATIO >= canceledHint.from.length() &&
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
		public final String oldBackbone;

		public LinkHint(VectorHint mainHint, VectorHint oldHint) {
			super(mainHint.root, mainHint.backbone, mainHint.from,
					mainHint.goal.limitTo(mainHint.from, oldHint.from),
					mainHint.goal, mainHint.caution);
			oldRoot = oldHint.root;
			oldFrom = oldHint.from;
			oldBackbone = oldHint.backbone;
		}

		@Override
		public String from() {
			return super.from() + " and " + oldBackbone + ": " + oldFrom;
		}

		@Override
		public String to() {
			return super.to() + " and " + oldBackbone + ": []";
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
			throw new NotImplementedException();
		}
	}

	public static class VectorHint extends StringHashable implements Hint {

		public final Node root;
		public final String backbone;
		public final VectorState from, to, goal;
		public final boolean caution;

		protected final boolean swapArgs;

		public VectorHint(Node root, String backbone, VectorState from, VectorState to,
				VectorState goal, boolean caution) {
			this.root = root;
			this.backbone = backbone;
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
			return backbone + ": " + from;
		}

		@Override
		public String to() {
			return backbone + ": " + to;
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

			List<Node> children = new ArrayList<Node>();
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
