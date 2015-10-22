package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
			List<Edge<VectorState, Void>> edges = graph.fromMap.get(children);
			if (edges == null) return hints;
			for (Edge<VectorState, Void> edge : edges) {
				if (edge.bBest) {
					hints.add(new Hint(new Node(null, backbone + ": " + edge.from), new Node(null, backbone + ": " + edge.to)));
				}
			}
		}
		return hints;
	}

	@Override
	public HintMap instance() {
		return new HintFactoryMap();
	}

	@Override
	public void setSolution(Node solution) {
		final AtomicInteger total = new AtomicInteger();
		final AtomicInteger good = new AtomicInteger();
		final AtomicInteger set = new AtomicInteger();
		solution.recurse(new Action<Node>() {
			@Override
			public void run(Node item) {
				if (item.children.size() == 0) return;
				for (int i = 0; i < 2; i++) {
					Node backbone = SkeletonMap.toBackbone(item, i != 0).root();
					VectorGraph graph = map.get(backbone);
					total.incrementAndGet();
					VectorState children = childrenState(item);
					if (graph == null) {
	//					System.out.println(backbone + ": " + children);
						return;
					}
					good.incrementAndGet();
					if (graph.setGoal(children, true)) {
						set.incrementAndGet();
					}
				}
			}
		});
//		System.out.println(set + "/" + good + "/" + total);
	}

	@Override
	public void finsh() {
		for (VectorGraph graph : map.values()) {
			graph.prune(2);
			graph.generateEdges();
			graph.bellmanBackup();
		}
	}
	
	
	
}
