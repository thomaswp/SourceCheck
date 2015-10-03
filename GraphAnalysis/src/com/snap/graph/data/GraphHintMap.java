package com.snap.graph.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.snap.graph.data.Graph.Edge;

public class GraphHintMap implements HintMap {
	private NodeGraph graph = new NodeGraph();

	@Override
	public void addVertex(Node node) {
		graph.addVertex(node);
	}

	@Override
	public void addEdge(Node from, Node to) {
		graph.addEdge(from, to);
	}

	@Override
	public boolean hasVertex(Node node) {
		return graph.vertexMap.containsKey(node);
	}

	@Override
	public int size() {
		return graph.vertexMap.size();
	}

	@Override
	public HintList getHints(Node node) {
		List<Edge<Node, Void>> listTemp = graph.fromMap.get(node);
		if (listTemp == null) listTemp = new LinkedList<Graph.Edge<Node,Void>>();
		final List<Edge<Node, Void>> list = listTemp;
		
		return new HintList() {
			final HashMap<Node, Integer> weights = new HashMap<Node, Integer>();
			
			@Override
			public Iterator<Node> iterator() {
				final Iterator<Edge<Node, Void>> iterator = list.iterator();
				return new Iterator<Node>() {
					@Override
					public void remove() {
						iterator.remove();
					}
					
					@Override
					public Node next() {
						Edge<Node, Void> edge = iterator.next();
						weights.put(edge.to, edge.weight);
						return edge.to;
					}
					
					@Override
					public boolean hasNext() {
						return iterator.hasNext();
					}
				};
			}
			
			@Override
			public int getWeight(Node to) {
				return weights.get(to);
			}
		};
	}

	@Override
	public void clear() {
		graph = new NodeGraph();
	}
	
}
