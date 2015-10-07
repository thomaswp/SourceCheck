package com.snap.graph.data;

import java.util.HashMap;
import java.util.Iterator;

public class SimpleHintMap implements HintMap {

	HashMap<Node, HashMap<Node, Integer>> edges = new HashMap<Node, HashMap<Node,Integer>>();
	
	@Override
	public void addVertex(Node node) {
		if (edges.containsKey(node)) return;
		edges.put(node, new HashMap<Node, Integer>());
	}

	@Override
	public void addEdge(Node from, Node to) {
		addVertex(from);
		addVertex(to);
		HashMap<Node, Integer> counts = edges.get(from);
		int count = counts.containsKey(to) ? counts.get(to) : 0;
		count++;
		counts.put(to, count);
	}

	@Override
	public boolean hasVertex(Node node) {
		return edges.containsKey(node.toCanonicalString());
	}
	
	public int size() {
		return edges.size();
	}

	@Override
	public HintList getHints(Node node) {
		HashMap<Node, Integer> mapTemp = edges.get(node);
		if (mapTemp == null) mapTemp = new HashMap<Node, Integer>();
		final HashMap<Node, Integer> map = mapTemp;
		
		return new HintList() {
			@Override
			public Iterator<Node> iterator() {
				return map.keySet().iterator();
			}
			
			@Override
			public int getWeight(Node to) {
				return map.get(to);
			}
		};
	}

	@Override
	public void clear() {
		edges.clear();
	}

}
