package com.snap.graph.data;

import com.snap.graph.subtree.SubtreeBuilder.Hint;



public interface HintMap {

	void clear();
	
//	void addVertex(Node node);

	void addEdge(Node from, Node to);

	boolean hasVertex(Node node);

	Iterable<Hint> getHints(Node node);
}
