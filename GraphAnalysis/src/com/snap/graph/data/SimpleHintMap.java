package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;

public class SimpleHintMap implements HintMap {

	HashMap<Node, HashMap<Node, Integer>> edges = new HashMap<Node, HashMap<Node,Integer>>();
	
	public void addVertex(Node node) {
		if (edges.containsKey(node)) return;
		edges.put(node, new HashMap<Node, Integer>());
	}

	@Override
	public HintChoice addEdge(Node from, Node to) {
		addVertex(from);
//		addVertex(to);
		HashMap<Node, Integer> counts = edges.get(from);
		int count = counts.containsKey(to) ? counts.get(to) : 0;
		count++;
		counts.put(to, count);
		return new HintChoice(from, to);
	}

	@Override
	public boolean hasVertex(Node node) {
		return edges.containsKey(node.toCanonicalString());
	}
	
	public int size() {
		return edges.size();
	}

	@Override
	public Iterable<Hint> getHints(Node node) {
		HashMap<Node, Integer> mapTemp = edges.get(node);
		if (mapTemp == null) mapTemp = new HashMap<Node, Integer>();
		HashMap<Node, Integer> map = mapTemp;
		List<Hint> hints = new ArrayList<Hint>();
		for (Node key : map.keySet()) hints.add(new Hint(node, key));
		return hints;
	}

	@Override
	public void clear() {
		edges.clear();
	}

	@Override
	public HintMap instance() {
		return new SimpleHintMap();
	}

	@Override
	public void setSolution(Node solution) {
		
	}

	@Override
	public void finsh() {
		
	}

	@Override
	public void addMap(HintMap hintMap) {
		
	}

}
