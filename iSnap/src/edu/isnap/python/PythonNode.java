package edu.isnap.python;

import org.json.JSONObject;

import edu.isnap.hint.util.ASTNodeConverter;
import edu.isnap.node.ASTSnapshot;
import edu.isnap.node.Node;

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

	public static boolean typeHasBody(String type) {
		return "list".equals(type);
	}

	@Override
	protected boolean nodeTypeHasBody(String type) {
		return typeHasBody(type);
	}

	public static PythonNode fromJSON(JSONObject jsonAST, String pythonSource) {
		ASTSnapshot astNode = ASTSnapshot.parse(jsonAST, pythonSource);
		return (PythonNode) ASTNodeConverter.toNode(astNode, PythonNode::new);
	}
}