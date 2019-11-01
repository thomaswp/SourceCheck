package edu.isnap.hint.util;

import edu.isnap.node.Node;
import edu.isnap.rating.RatingConfig;
import util.LblTree;

public class SnapNode extends Node {

	@SuppressWarnings("unused")
	private SnapNode() {
		this(null, null, null, null);
	}

	public SnapNode(Node parent, String type, String value, String id) {
		super(parent, type, value, id);
	}

	@Override
	public Node constructNode(Node parent, String type, String value, String id) {
		return new SnapNode(parent, type, value, id);
	}

	@Override
	protected boolean nodeTypeHasBody(String type) {
		return RatingConfig.Snap.nodeTypeHasBody(type);
	}

	public static Node fromTree(Node parent, LblTree tree, boolean cache) {
		Node node = new SnapNode(parent, tree.getLabel(), null, null);
		int count = tree.getChildCount();
		for (int i = 0; i < count; i++) {
			Node child = fromTree(node, (LblTree) tree.getChildAt(i), cache);
			node.children.add(child);
		}
		if (cache) node.cache();
		return node;
	}
}
