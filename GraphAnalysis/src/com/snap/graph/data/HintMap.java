package com.snap.graph.data;

import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;



public interface HintMap {

	void clear();
	
//	void addVertex(Node node);
	
	void addState(Node node);

	HintChoice addEdge(Node from, Node to);

	boolean hasVertex(Node node);

	Iterable<Hint> getHints(Node node);
	
	HintMap instance();

	void setSolution(Node solution);
	
	void finish();

	void addMap(HintMap hintMap);
}
