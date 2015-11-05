package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.snap.graph.data.Graph.Edge;
import com.snap.graph.data.Node.Action;
import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;

public class HintFactoryMap implements HintMap {
	public final HashMap<Node, VectorGraph> map = new HashMap<Node, VectorGraph>();

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public void addState(Node node) {
		node.recurse(new Action<Node>() {
			@Override
			public void run(Node item) {
				VectorState children = childrenState(item);
				getGraph(item, true).addPathNode(children, null);
				getGraph(item, false).addPathNode(children, null);
			}
		});
	}
	
	@Override
	public HintChoice addEdge(Node from, Node to) {
		
		VectorState fromState = childrenState(from);
		VectorState toState = childrenState(to);
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
	
	private VectorState childrenState(Node node) {
		List<String> children = new ArrayList<String>();
		if (node == null) return new VectorState(children);
		for (Node child : node.children) {
			if ("null".equals(child.type)) continue;
			children.add(child.type);
		}
		return new VectorState(children);
	}

	@Override
	public boolean hasVertex(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Iterable<Hint> getHints(Node node) {
		List<Hint> hints = new ArrayList<Hint>();
		
		for (int i = 0; i < 2; i++) {
			Node backbone = SkeletonMap.toBackbone(node, i != 0).root();
			VectorGraph graph = map.get(backbone);
			if (graph == null) continue;
			
			VectorState children = childrenState(node);
			// Get the best successor state from our current state
			VectorState next = bestEdge(graph, children);
			
			if (next != null) {
				// If we find one, go there
				hints.add(createHint(node, backbone, children, next));
				continue;
			}
			// If we're at a goal with no better place to go, stay here
			if (graph.isGoal(children)) continue;
			
			// Look for a nearest neighbor in the graph
			VectorState nearestNeighbor = graph.getNearestNeighbor(children, 3);
			if (nearestNeighbor != null) { 
				// If we find one, get the hint from there
				VectorState hintFrom = bestEdge(graph, nearestNeighbor);
				if (hintFrom != null) {
					// If it exists, and it's at least as close as the nearest neighbor...
					int disNN = VectorState.distance(children, nearestNeighbor);
					int disHF = VectorState.distance(children, hintFrom);
					if (disHF <= disNN) {
						// Use it insead
						hints.add(createHint(node, backbone, children, hintFrom));
						continue;
					}
				}
				
				// Otherwise hint to go to the nearest neighbor
				hints.add(createHint(node, backbone, children, nearestNeighbor));
			}
		}
		return hints;
	}
	
	private static VectorState bestEdge(VectorGraph graph, VectorState state) {
		List<Edge<VectorState, Void>> edges = graph.fromMap.get(state);
		if (edges == null) return null;
		for (Edge<VectorState, Void> edge : edges) {
			if (edge.bBest) {
				return edge.to;
			}
		}
		return null;
	}
	
	private static VectorHint createHint(Node parent, Node backbone, VectorState from, VectorState to) {
		return new VectorHint(parent, backbone, from, to);
	}

	@Override
	public HintMap instance() {
		return new HintFactoryMap();
	}

	@Override
	public void setSolution(Node solution) {
		solution.recurse(new Action<Node>() {
			@Override
			public void run(Node item) {
				if (item.children.size() == 0) return;
				for (int i = 0; i < 2; i++) {
					VectorGraph graph = getGraph(item, i != 0);
					VectorState children = childrenState(item);
					if (!graph.vertices.contains(children)) {
						graph.addVertex(children);
					}
					graph.setGoal(children, true);
				}
			}
		});
	}

	@Override
	public void finsh() {
		for (VectorGraph graph : map.values()) {
			graph.prune(2);
			graph.generateEdges();
			graph.bellmanBackup(2);
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
	
	private static class VectorHint implements Hint {

		public final Node root;
		public final String backbone;
		public final VectorState from, to;
		
		public VectorHint(Node root, Node backbone, VectorState from, VectorState to) {
			this.root = root;
			this.backbone = backbone.toString();
			this.from = from;
			this.to = to;
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
			return String.format("{\"root\": %s, \"from\": %s, \"to\": %s}", 
					getNodeReference(root), from.toJson(), to.toJson());
		}
		
		public static String getNodeReference(Node node) {
			if (node == null) return null;
			
			String label = node.type;
			int index = node.index();
			String parent = getNodeReference(node.parent);
			
			return String.format("{\"label\": \"%s\", \"index\": %d, \"parent\": %s}",
					label, index, parent);
		}
		
	}
	
}
