package edu.isnap.eval.java;

import java.util.Arrays;

import org.json.JSONObject;

import edu.isnap.hint.util.ASTNodeConverter;
import edu.isnap.node.ASTSnapshot;
import edu.isnap.node.Node;
import edu.isnap.python.TextualNode;

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

	public static boolean typeHasBody(String type) {
		// TODO: finish this
		String[] variableChildrenTypes = {"Modifier", "Operator", "NameExpr",
				"Parameter", "IntegerLiteralExpr", "VoidType", "PrimitiveType"};
		return !Arrays.asList(variableChildrenTypes).contains(type);
	}

	@Override
	protected boolean nodeTypeHasBody(String type) {
		return typeHasBody(type);
	}

	public static JavaNode fromJSON(JSONObject jsonAST, String pythonSource) {
		ASTSnapshot astNode = ASTSnapshot.parse(jsonAST, pythonSource);
		return (JavaNode) ASTNodeConverter.toNode(astNode, JavaNode::new);
	}
}