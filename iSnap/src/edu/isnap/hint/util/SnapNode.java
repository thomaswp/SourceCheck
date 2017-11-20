package edu.isnap.hint.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import util.LblTree;

public class SnapNode extends Node {

	private final static Set<String> BodyTypes = new HashSet<>(Arrays.asList(
		new String[] {
				"snapshot", "stage", "sprite", "script", "customBlock",
		}
	));

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
		return BodyTypes.contains(type);
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
