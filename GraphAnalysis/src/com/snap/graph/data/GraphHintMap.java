package com.snap.graph.data;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.snap.graph.data.Graph.Edge;
import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;

public class GraphHintMap implements HintMap {
	private NodeGraph graph = new NodeGraph();

	public void addVertex(Node node) {
		graph.addVertex(node);
	}

	@Override
	public HintChoice addEdge(Node from, Node to) {
		graph.addEdge(from, to);
		return new HintChoice(from, to);
	}

	@Override
	public boolean hasVertex(Node node) {
		return graph.vertexMap.containsKey(node);
	}

	public int size() {
		return graph.vertexMap.size();
	}

	@Override
	public Iterable<Hint> getHints(Node node) {
		List<Edge<Node, Void>> listTemp = graph.fromMap.get(node);
		if (listTemp == null) listTemp = new LinkedList<Graph.Edge<Node,Void>>();
		final List<Edge<Node, Void>> list = listTemp;
		
		return new Iterable<Hint>() {			
			@Override
			public Iterator<Hint> iterator() {
				final Iterator<Edge<Node, Void>> iterator = list.iterator();
				return new Iterator<Hint>() {
					@Override
					public void remove() {
						iterator.remove();
					}
					
					@Override
					public Hint next() {
						Edge<Node, Void> edge = iterator.next();
						return new Hint(edge.from, edge.to);
					}
					
					@Override
					public boolean hasNext() {
						return iterator.hasNext();
					}
				};
			}
		};
	}

	@Override
	public void clear() {
		graph = new NodeGraph();
	}

	@Override
	public HintMap instance() {
		return new GraphHintMap();
	}
	
}
