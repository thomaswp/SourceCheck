package com.snap.graph.data;

import java.util.HashMap;

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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public HintMap instance() {
		return new HintFactoryMap();
	}
	
	
	
}
