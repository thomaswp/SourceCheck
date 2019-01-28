package astrecognition.model;

import edu.isnap.hint.util.SnapNode;
import edu.isnap.node.Node;

public class Convert {
	public static Tree nodeToTree(Node node) {
		Tree root = new Tree(node.type());
		root.tag = node;
		for (Node child : node.children) {
			Tree childTree = nodeToTree(child);
			childTree.parent = root;
			root.addChild(childTree);
		}
		return root;
	}

	public static Node treeToNode(Tree tree) {
		return treeToNode(tree, null);
	}

	private static Node treeToNode(Tree tree, Node parent) {
		Node root = new SnapNode(parent, tree.getOriginalLabel(), null, null);
		for (Tree child : tree.children) {
			root.children.add(treeToNode(child, root));
		}
		return root;
	}
}
