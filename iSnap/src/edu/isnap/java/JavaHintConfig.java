package edu.isnap.java;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.isnap.hint.HintConfig;
import edu.isnap.node.JavaNode;
import edu.isnap.node.Node;
import edu.isnap.node.Node.NodeConstructor;
import edu.isnap.rating.RatingConfig;

public class JavaHintConfig extends HintConfig {
	private static final long serialVersionUID = 1L;

	public JavaHintConfig() {
		preprocessSolutions = false;
		suggestNewMappedValues = true;
	}

	@Override
	public boolean isOrderInvariant(String type) {
		return false;
	}

	@Override
	public boolean hasFixedChildren(Node node) {
		return node != null && RatingConfig.Java.hasFixedChildren(node.type(), node.parentType());
	}

	// No hint should suggest moving lists or most literal types

	@Override
	public boolean canMove(Node node) {
		return node != null && !(node.type().toLowerCase().indexOf("literal") != -1);
	}

	@Override
	public boolean isContainer(String type) {
		return false;
	}

	//Please explain this...
	/* Essentially refering to same kind of identifier. Anything with an identifier
	 *
	 * */
	private final static String[][] valueMappedTypes = new String[][] {
			new String[] { "NameExpr", "VariableDeclarator", "Parameter" },
			new String[] { "MethodDeclaration" },
	};

	@Override
	public String[][] getValueMappedTypes() {
		return valueMappedTypes;
	}


	@Override
	public boolean shouldAutoAdd(Node node) {
		if (node == null || node.parent == null) return false;
		Node parent = node.parent;
		int index = node.index();
		return (parent.hasType("BinaryExp") && index == 0) ||
				(parent.hasType("ExpressionStmt") && index == 0);
	}

	@Override
	public NodeConstructor getNodeConstructor() {
		return JavaNode::new;
	}

	@Override
	public boolean areNodeIDsConsistent() {
		return false;
	}

	private final static Map<String, String> nameMap = new LinkedHashMap<>();
	static {
		nameMap.put("IfStmt", "an if statement");
		nameMap.put("NameExpr", "a variable");
		nameMap.put("BinaryExpr", "an operarator");
		nameMap.put("ExpressionStmt", "an expression");
		// TODO: Add more to nameMap
	}

	@Override
	public String getHumanReadableName(Node node) {
		String name = nameMap.get(node.type());
		if (name != null) return name;
		return node.type();
//		return node.prettyPrint().replace("\n", "");
	}
}
