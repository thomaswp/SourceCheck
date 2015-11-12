package com.snap.graph.data;

import java.util.ArrayList;
import java.util.List;

import util.LblTree;

import com.snap.data.Canonicalization;

public class Node extends StringHashable {

	public final String type;
	public final Node parent;
	public final List<Node> children = new ArrayList<Node>();
	
	public transient Object tag;
	public final transient List<Canonicalization> canonicalizations = new ArrayList<Canonicalization>();

	private String arg;
	private transient int cachedSize = -1;
	
	@SuppressWarnings("unused")
	private Node() {
		this(null, null);
	}
	
	@Override
	public void cache() {
		super.cache();
		cachedSize = size();
	}
	
	@Override
	public void clearCache() {
		super.clearCache();
		cachedSize = -1;
	}
	
	public Node(Node parent, String type) {
		this(parent, type, null);
	}
	
	public Node(Node parent, String type, String arg) {
		this.parent = parent;
		this.type = type;
		this.arg = arg;
	}
	
	public int size() {
		if (cachedSize != -1) return cachedSize;
		int size = 1;
		for (Node node : children) {
			size += node.size();
		}
		return size;
	}
	
	public Node root() {
		if (parent == null) return this;
		return parent.root();
	}
	
	public int depth() {
		if (parent == null) return 0;
		return parent.depth() + 1;
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
	
	public int index() {
		if (parent == null) return -1;
		for (int i = 0; i < parent.children.size(); i++) {
			if (parent.children.get(i) == this) {
				return i;
			}
		}
		return -1;
	}
	
	public void recurse(Action<Node> action) {
		action.run(this);
		for (Node child : children) {
			if (child != null) child.recurse(action);
		}
	}
	
	public interface Action<T> {
		void run(T item);
	}
	
	public static Node fromTree(Node parent, LblTree tree, boolean cache) {
		Node node = new Node(parent, tree.getLabel());
		int count = tree.getChildCount();
		for (int i = 0; i < count; i++) {
			Node child = fromTree(node, (LblTree) tree.getChildAt(i), cache);
			node.children.add(child);
		}
		if (cache) node.cache();
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
