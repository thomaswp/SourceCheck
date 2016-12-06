package edu.isnap.ctd.hint;

import java.util.IdentityHashMap;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;

public class HintHighlighter {

	private final HintMap hintMap;

	public HintHighlighter(HintMap hintMap) {
		this.hintMap = hintMap;
	}

	public void highlight(Node node) {
		final IdentityHashMap<Node, String> colors = new IdentityHashMap<>();

		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				highlight(node, colors);
			}
		});
	}

	private void highlight(Node node, Map<Node, String> colors) {

	}
}
