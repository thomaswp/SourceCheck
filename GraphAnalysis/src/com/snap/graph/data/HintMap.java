package com.snap.graph.data;



public interface HintMap {

	void clear();
	
	void addVertex(Node node);

	void addEdge(Node from, Node to);

	boolean hasVertex(Node node);

	HintList getHints(Node node);

	int size();
	
	public interface HintList extends Iterable<Node> {
		int getWeight(Node to);
	}

}
