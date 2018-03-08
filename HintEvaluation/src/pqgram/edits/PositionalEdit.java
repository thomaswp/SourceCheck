package pqgram.edits;

import java.util.Map;

import astrecognition.model.Graph;
import astrecognition.model.Tree;
import edu.isnap.ctd.graph.Node;

/**
 * An edit which requires some positional description
 */
public class PositionalEdit extends Edit {
	protected int start;

	public PositionalEdit(String a, String b, Graph aG, Graph bG, int start) {
		super(a, b, aG, bG);
		this.start = start;
	}

	public int getPosition() {
		return this.start;
	}

	@Override
	public Node getParentNode(Map<String, Tree> fromMap) {
		if (!fromMap.containsKey(a)) return null;
		return fromMap.get(a).tag;
	}

	@Override
	public Node outcome(Node from) {
		throw new UnsupportedOperationException();
	}
}
