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
	public final HashMap<Node, OutGraph<String>> map = new HashMap<Node, OutGraph<String>>();

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public HintChoice addEdge(Node from, Node to) {
		Node backbone = SkeletonMap.toBackbone(from).root();
		OutGraph<String> graph = map.get(backbone); 
		if (graph == null) {
			graph = new OutGraph<String>();
			map.put(backbone, graph);
		}
		graph.addEdge(childrenState(from), childrenState(to));
		
		return new HintChoice(from, to);
	}
	
	private String childrenState(Node node) {
		if (node == null) return "[]";
		String state = "[";
		for (Node child : node.children) {
			if ("null".equals(child.type)) continue;
			if (state.length() > 1) {
				state += ",";
			}
			state += child.type;
		}
		state += "]";
		return state;
	}

	@Override
	public boolean hasVertex(Node node) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Iterable<Hint> getHints(Node node) {
		List<Hint> hints = new ArrayList<Hint>();
		Node backbone = SkeletonMap.toBackbone(node).root();
		OutGraph<String> graph = map.get(backbone);
		if (graph == null) return hints;
		String children = childrenState(node);
		List<Edge<String, Void>> edges = graph.fromMap.get(children);
		if (edges == null) return hints;
		for (Edge<String, Void> edge : edges) {
			if (edge.bBest) {
				hints.add(new Hint(new Node(null, backbone + ": " + edge.from), new Node(null, backbone + ": " + edge.to)));
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
				Node backbone = SkeletonMap.toBackbone(item).root();
				OutGraph<String> graph = map.get(backbone);
				total.incrementAndGet();
				String children = childrenState(item);
				if (graph == null) {
//					System.out.println(backbone + ": " + children);
					return;
				}
				good.incrementAndGet();
				if (graph.setGoal(children, true)) {
					set.incrementAndGet();
				}
			}
		});
//		System.out.println(set + "/" + good + "/" + total);
	}

	@Override
	public void finsh() {
		for (OutGraph<String> graph : map.values()) {
			graph.bellmanBackup();
		}
	}
	
	
	
}
