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
		this.parent = parent;
		this.type = type;
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
		return String.format("%s%s", 
				type, 
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
	
	public void recurse(Action action) {
		action.run(this);
		for (Node child : children) {
			if (child != null) child.recurse(action);
		}
	}
	
	public boolean hasAncestor(Predicate pred) {
		return pred.eval(this) || hasProperAncestor(pred);
	}
	
	public boolean hasProperAncestor(Predicate pred) {
		return parent != null && parent.hasAncestor(pred);
	}
	
	public boolean exists(Predicate pred) {
		return search(pred) != null;
	}
	
	public Node search(Predicate pred) {
		if (pred.eval(this)) return this;
		for (Node node : children) {
			Node found = node.search(pred);
			if (found != null) return found;
		}
		return null;
	}
	
	public interface Action {
		void run(Node node);
	}
	
	public interface Predicate {
		boolean eval(Node node);
	}
	
	public static class TypePredicate implements Predicate {
		private final String[] types;
		
		public TypePredicate(String... types) {
			this.types = types;
		}

		@Override
		public boolean eval(Node node) {
			for (String type : types) {
				if (type.equals(node.type)) return true;
			}
			return false;
		}		
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
		return eq(type, node.type);
	}
	
	private boolean eq(String a, String b) {
		if (a == null) return b == null;
		return a.equals(b);
	}
}
