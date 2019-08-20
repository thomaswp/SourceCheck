package edu.isnap.hint.util;

import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.node.Node.NodeConstructor;

public class ASTNodeConverter {

	public final static String OLD_SNAPSHOT_TYPE = "Snap!shot";

	public static Node toNode(ASTNode astNode, NodeConstructor constructor) {
		return toNode(astNode, null, constructor);
	}

	public static Node toNode(ASTNode astNode, Node parent, NodeConstructor constructor) {
		String type = astNode.type;
		if (OLD_SNAPSHOT_TYPE.equals(type)) type = "snapshot";
		Node node = constructor.constructNode(parent, type, astNode.value, astNode.id);
		node.tag = astNode;
		for (ASTNode child : astNode.children()) {
			node.children.add(toNode(child, node, constructor));
		}
		return node;
	}
}
