package edu.isnap.node;

import org.json.JSONObject;

import edu.isnap.node.ASTSnapshot;
import edu.isnap.rating.RatingConfig;

public class JavaNode extends TextualNode {

	@SuppressWarnings("unused")
	private JavaNode() {
		this(null, null, null, null);
	}

	public JavaNode(Node parent, String type, String value, String id) {
		super(parent, type, value, id);
	}

	@Override
	public Node constructNode(Node parent, String type, String value, String id) {
		return new JavaNode(parent, type, value, id);
	}

	@Override
	protected boolean nodeTypeHasBody(String type) {
		return RatingConfig.Java.nodeTypeHasBody(type);
	}

	public static JavaNode fromJSON(JSONObject jsonAST, String pythonSource) {
		ASTSnapshot astNode = ASTSnapshot.parse(jsonAST, pythonSource);
		return (JavaNode) fromASTNode(astNode, JavaNode::new);
	}
}