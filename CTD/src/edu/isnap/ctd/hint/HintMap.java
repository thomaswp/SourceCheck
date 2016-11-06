package edu.isnap.ctd.hint;

import java.util.List;

import edu.isnap.ctd.graph.Node;

public interface HintMap {

	void clear();

	void addEdge(Node from, Node to);

	Iterable<Hint> getHints(Node node, int chain);

	HintMap instance();

	HintConfig getHintConfig();

	void setSolution(Node solution);

	void finish();

	void addMap(HintMap hintMap);

	void postProcess(List<Hint> hints);
}
