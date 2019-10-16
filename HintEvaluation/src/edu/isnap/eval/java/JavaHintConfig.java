package edu.isnap.eval.java;

import edu.isnap.hint.HintConfig;
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

	//Could you please explain this?
	@Override
	public boolean shouldAutoAdd(Node node) {
		if (node == null || node.parent == null) return false;
		Node parent = node.parent;
		int index = node.index();
		return (parent.hasType("BinaryExp") && index == 0);
	}

	@Override
	public NodeConstructor getNodeConstructor() {
		return JavaNode::new;
	}

	@Override
	public boolean areNodeIDsConsistent() {
		return false;
	}
}
