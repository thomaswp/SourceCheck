package com.snap.graph.data;

import java.util.List;

public interface HintMap {

	void clear();

	void addEdge(Node from, Node to);

	Iterable<Hint> getHints(Node node, int chain);
	
	HintMap instance();

	void setSolution(Node solution);
	
	void finish();

	void addMap(HintMap hintMap);

	void postProcess(List<Hint> hints);
}
