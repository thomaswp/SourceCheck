package edu.isnap.python;

import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;
import edu.isnap.rating.RatingConfig;

public class PythonNode extends TextualNode {

	@SuppressWarnings("unused")
	private PythonNode() {
		this(null, null, null, null);
	}

	public PythonNode(Node parent, String type, String value, String id) {
		super(parent, type, value, id);
	}

	@Override
	public Node constructNode(Node parent, String type, String value, String id) {
		return new PythonNode(parent, type, value, id);
	}

	@Override
	protected boolean nodeTypeHasBody(String type) {
		return RatingConfig.Python.nodeTypeHasBody(type);
	}
}