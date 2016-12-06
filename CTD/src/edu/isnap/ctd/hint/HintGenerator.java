package edu.isnap.ctd.hint;

import java.util.LinkedList;
import java.util.List;

import edu.isnap.ctd.graph.Node;

public class HintGenerator {

	private final HintMap hintMap;

	public HintGenerator(HintMap hintMap) {
		this.hintMap = hintMap;
	}

	/**
	 * Gets the first hint generated for the given (root) Node, representing a student's current
	 * snapshot.
	 * @param node
	 * @return
	 */
	public synchronized VectorHint getFirstHint(Node node) {
		LinkedList<VectorHint> hints = new LinkedList<>();
		getHints(node, hints, 1);
		return hints.size() > 0 ? hints.getFirst() : null;
	}

	/**
	 * Gets all hints generated for the given (root) Node, representing a student's current
	 * snapshot.
	 * @param node
	 * @return
	 */
	public synchronized List<VectorHint> getHints(Node node) {
		LinkedList<VectorHint> hints = new LinkedList<>();
		getHints(node, hints, Integer.MAX_VALUE);
		hintMap.postProcess(hints);
		return hints;
	}

	private void getHints(Node node, List<VectorHint> list, int limit) {
		if (list.size() >= limit) return;

		Iterable<VectorHint> edges = hintMap.getHints(node);

		// TODO: don't forget that really the skeleton need not match exactly,
		// we should just be matching as much as possible

		for (VectorHint hint : edges) {
			if (list.contains(hint)) continue;
			list.add(hint);
			if (list.size() >= limit) return;
		}

		for (Node child : node.children) getHints(child, list, limit);
	}

}
