package com.snap.graph.subtree;

import java.util.ArrayList;
import java.util.List;

import util.LblTree;

public class Node extends StringHashable {

	public final String type;
	private String arg;
	public final Node parent;
	public final List<Node> children = new ArrayList<Node>();
	
	public Node(Node parent, String type) {
		this(parent, type, null);
	}
	
	public Node(Node parent, String type, String arg) {
		this.parent = parent;
		this.type = type;
		this.arg = arg;
	}

	@Override
	protected String toCanonicalStringInternal() {
		return String.format("%s%s%s", 
				type, 
				arg == null ? "" : ("(" + arg + ")"), 
				children.size() == 0 ? "" : (":" + toCannonicalString(children)));
	}
	
	public LblTree toTree() {
		LblTree tree = new LblTree(type, 0);
		tree.setUserObject(this);
		for (Node node : children) tree.add(node.toTree());
		return tree;
	}
	
	public static Node fromTree(Node parent, LblTree tree) {
		Node node = new Node(parent, tree.getLabel());
		int count = tree.getChildCount();
		for (int i = 0; i < count; i++) {
			Node child = fromTree(node, (LblTree) tree.getChildAt(i));
			node.children.add(child);
		}
		return node;
	}

	public boolean shallowEquals(Node node) {
		if (node == null) return false;
		return eq(type, node.type) && eq(arg, node.arg);
	}
	
	private boolean eq(String a, String b) {
		if (a == null) return b == null;
		return a.equals(b);
	}
}
