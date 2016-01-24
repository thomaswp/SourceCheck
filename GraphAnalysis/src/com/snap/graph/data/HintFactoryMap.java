package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.snap.data.Canonicalization;
import com.snap.data.Canonicalization.InvertOp;
import com.snap.data.Canonicalization.SwapArgs;
import com.snap.graph.data.Node.Action;
import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;

public class HintFactoryMap implements HintMap {
	
	private final static int ROUNDS = 1;
	private final static double STAY_PROPORTION = 0.75;
	private final static int PRUNE_NODES = 2;
	private final static int PRUNE_GOALS = 2;
	private final static int MAX_NN = 3;
	
	// TODO: stop cheating!
	private final static HashSet<String> BAD_CONTEXT = new HashSet<String>();
	static {
		for (String c : new String[] {
				"doIf",
				"doUntil"
		}) {
			BAD_CONTEXT.add(c);
		}
	}
	
	private final static String SCRIPT = "script";
	
	public final HashMap<Node, VectorGraph> map = new HashMap<Node, VectorGraph>();
	
	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public void addState(Node node) {
		node.recurse(new Action() {
			@Override
			public void run(Node item) {
				VectorState children = getVectorState(item);
				getGraph(item, true).addPathNode(children, null);
				getGraph(item, false).addPathNode(children, null);
			}
		});
	}
	
	@Override
	public HintChoice addEdge(Node from, Node to) {
		
		VectorState fromState = getVectorState(from);
		VectorState toState = getVectorState(to);
		getGraph(from, true).addEdge(fromState, toState);
		getGraph(from, false).addEdge(fromState, toState);
		
		return new HintChoice(from, to);
	}
	
	private VectorGraph getGraph(Node node, boolean indices) {
		Node backbone = SkeletonMap.toBackbone(node, indices).root();
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
			if ("null".equals(child.type)) continue;
			children.add(child.type);
		}
		return children;
	}

	@Override
	public boolean hasVertex(Node node) {
		return false;
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
				for (int i = 0; i < ROUNDS; i++) {
					VectorGraph graph = getGraph(item, i != 0);
					VectorState children = getVectorState(item);
					if (!graph.vertices.contains(children)) {
						graph.addVertex(children);
					}
					graph.setGoal(children, getContext(item));
				}
			}
		});
	}

	private static IndexedVectorState getContext(Node item) {
		return getContext(item, 3);
	}
	
	private static IndexedVectorState getContext(Node item, int maxLength) {
		Node contextChild = item;
		while (contextChild.parent != null && BAD_CONTEXT.contains(contextChild.parent.type)) contextChild = contextChild.parent;
		int index = contextChild.index(); 
		return new IndexedVectorState(getChildren(contextChild.parent), index, maxLength);
	}

	@Override
	public Iterable<Hint> getHints(Node node) {
		List<Hint> hints = new ArrayList<Hint>();
		
		for (int i = 0; i < ROUNDS; i++) {
			boolean indexed = i != 0;
			Node backbone = SkeletonMap.toBackbone(node, indexed).root();
			VectorGraph graph = map.get(backbone);
			if (graph == null) continue;
			
			boolean useGraph = !node.hasType(SCRIPT);
			
			VectorState children = getVectorState(node);
			IndexedVectorState context = getContext(node);
			// Get the best successor state from our current state
			VectorState next = graph.getHint(children, context, MAX_NN, useGraph);
			// If there is none, we create a dead-end hint, just so we know we want to stay here
			if (next == null) next = children;
			
			double stayed = graph.getProportionStayed(children);
			boolean caution = 
					graph.getGoalCount(children) >= PRUNE_GOALS && 
					stayed >= STAY_PROPORTION; 
			VectorHint hint = new VectorHint(node, backbone, children, next, indexed, caution);
			hints.add(hint);
		}
		return hints;
	}

	@Override
	public void finish() {		
		for (VectorGraph graph : map.values()) {
			graph.prune(PRUNE_NODES);
			graph.generateEdges();
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
	
	public static class VectorHint extends StringHashable implements Hint {

		public final Node root;
		public final String backbone;
		public final VectorState from, to;
//		public final boolean loop;
		public final boolean indexed;
		public final boolean caution;
		
		private final boolean swapArgs;
		
		public VectorHint(Node root, Node backbone, VectorState from, VectorState to, boolean indexed, boolean caution) {
			this.root = root;
			this.backbone = backbone.toString();
			this.from = from;
			this.to = to;
//			this.loop = from.equals(to);
			this.indexed = indexed;
			this.caution = caution;
			
			boolean swap = false;
			for (Canonicalization c : root.canonicalizations) {
				if (c instanceof InvertOp || c instanceof SwapArgs) {
					swap = true;
					break;
				}
			}
			this.swapArgs = swap;
			
			cache();
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
			return String.format("{\"root\": %s, \"from\": %s, \"to\": %s, \"caution\": %s}", 
					getNodeReference(root), from.toJson(swapArgs), to.toJson(swapArgs), String.valueOf(caution));
		}
		
		public static String getNodeReference(Node node) {
			if (node == null) return null;
			
			String label = node.type;
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
		public boolean overrides(Hint hint) {
			if (!indexed) return false;
			if (!(hint instanceof VectorHint)) return false;
			
			return from.equals(((VectorHint)hint).from);
//			return false;
		}

		@Override
		public Node outcome() {
			Node nRoot = root.copy();
			
			List<Node> children = new ArrayList<Node>();
			children.addAll(nRoot.children);
			
			nRoot.children.clear();
			for (String type : to.items) {
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
