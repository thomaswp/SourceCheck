package com.snap.graph.data;

import java.util.ArrayList;
import java.util.List;

import util.LblTree;

import com.snap.data.Canonicalization;

public class Node extends StringHashable {

	private String type;
	public final Node parent;
	public final List<Node> children = new ArrayList<Node>();
	
	public transient Object tag;
	public final transient List<Canonicalization> canonicalizations = new ArrayList<Canonicalization>();
	
	public String type() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
		recache();
	}
	
	private void recache() {
		clearCache();
		if (parent != null) parent.recache();
	}
	
	@SuppressWarnings("unused")
	private Node() {
		this(null, null);
	}
	
	public Node(Node parent, String type) {
		this.parent = parent;
		this.type = type;
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
	
	public int searchChildren(Predicate pred) {
		return searchChildren(pred, 0);
	}
	
	public int searchChildren(Predicate pred, int startIndex) {
		return searchChildren(pred, 0, children.size());
	}
	
	public int searchChildren(Predicate pred, int startIndex, int endIndexExclusive) {
		endIndexExclusive = Math.min(endIndexExclusive, children.size());
		for (int i = startIndex; i < endIndexExclusive; i++) {
			if (pred.eval(children.get(i))) return i;
		}
		return -1;
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
	
	public static class BackbonePredicate implements Predicate {
		private final String[] backbone;
		
		public BackbonePredicate(String... backbone) {
			this.backbone = backbone;
		}
		
		@Override
		public boolean eval(Node node) {
			for (int i = backbone.length - 1; i >= 0; i--) {
				String toMatch = backbone[i];
				if (toMatch.equals("...")) {
					if (i == 0) break;
					toMatch = backbone[i - 1];
					while (node != null && !matches(toMatch, node.type)) node = node.parent;
					continue;
				}
				if (node == null || !matches(toMatch, node.type)) return false;
				node = node.parent;
			}
			return true;
		}
		
		private static boolean matches(String toMatch, String type) {
			if (toMatch.contains("|")) {
				String[] parts = toMatch.split("\\|");
				for (String part : parts) {
					if (part.equals(type)) return true;
				}
				return false;
			} else {
				return toMatch.equals(type);
			}
		}
	}
	
	public static class ConjunctionPredicate implements Predicate {
		private final Predicate predicates[];
		private final boolean and;
		
		public ConjunctionPredicate(boolean and, Predicate... predicates) {
			this.and = and;
			this.predicates = predicates;
		}
		
		@Override
		public boolean eval(Node node) {
			for (Predicate pred : predicates) {
				if (pred.eval(node) != and) return !and;
			}
			return and;
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

	public boolean hasType(String type) {
		return type.equals(this.type);
	}
	
	public boolean childHasType(String type, int index) {
		return index < children.size() && children.get(index).hasType(type);
	}

	public Node copy(boolean setParent) {
		Node rootCopy = root().childrenCopy(setParent ? parent : null);
		return findParallelNode(rootCopy, this);
	}
	
	public Node addChild(String type) {
		Node child = new Node(this, type);
		children.add(child);
		return child;
	}
	
	private Node childrenCopy(Node parent) {
		Node copy = new Node(parent, type);
		for (Node child : children) {
			copy.children.add(child.childrenCopy(copy));
		}
		return copy;
	}
	
	private static Node findParallelNode(Node root, Node child) {
		if (child.parent == null) return root;
		
		Node parent = findParallelNode(root, child.parent);
		return parent.children.get(child.index());
	}
}
