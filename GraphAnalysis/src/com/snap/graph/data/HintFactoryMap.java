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
import com.snap.graph.data.Node.Predicate;
import com.snap.parser.HintConfig;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Class for handling the core logic of the CTD algorithm.
 */
public class HintFactoryMap implements HintMap {

	final HintConfig config;

	@SuppressWarnings("unused")
	private HintFactoryMap() {
		this(null);
	}

	public HintFactoryMap(HintConfig config) {
		this.config = config;
	}

	public final HashMap<Node, VectorGraph> map =
			new HashMap<Node, VectorGraph>();

	/**
	 * Gets the backbone for the given Node, which contains only the nodes in the root path from
	 * this given node to its root.
	 */
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
		return new HintFactoryMap(config);
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
		List<Hint> hints = new ArrayList<Hint>();

		Node backbone = toBackbone(node).root();
		VectorGraph graph = map.get(backbone);
		if (graph == null) return hints;

		boolean useGraph = !node.hasType(config.script);

		VectorState children = getVectorState(node);
		IndexedVectorState context = getContext(node);
		VectorState next = children;

		VectorState goal = graph.getGoalState(next, context, config.maxNN, config.pruneGoals);

		if (goal != null && config.straightToGoal.contains(node.type())) {
			next = goal;
		} else {
			for (int j = 0; j < chain; j++) {
				// Get the best successor state from our current state
				VectorState hint = graph.getHint(next, context, config.maxNN, config.pruneGoals,
						useGraph);
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

		VectorHint hint = new VectorHint(node, backbone.toString(), children, next, goal, caution);
		hints.add(hint);

		return hints;
	}

	@Override
	public void finish() {
		for (Node key : map.keySet()) {
			VectorGraph graph = map.get(key);
			Node parent = key;
			while (parent.children.size() == 1) {
				parent = parent.children.get(0);
			}

			graph.prune(config.pruneNodes);
			// TODO: This is my shorthand of saying we shouldn't do this for scripts with common
			// siblings (as the children of doIfElse do), since the weighting isn't context
			// sensitive right now. Ideally the weighting should be, or at the very least a more
			// comprehensive solution is needed for identifying when this is inappropriate
			if (parent.hasType(config.script) && !parent.parentHasType("doIfElse")) {
				graph.generateScriptGoalValues();
			}
			graph.generateAndRemoveEdges(config.maxEdgeAddDistance, config.maxEdgeDistance);
			graph.bellmanBackup(config.pruneGoals);
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

			if (vHint.root.hasAncestor(new Predicate() {
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
