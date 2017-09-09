package edu.isnap.ctd.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import edu.isnap.ctd.hint.Canonicalization;
import edu.isnap.ctd.hint.Canonicalization.InvertOp;
import edu.isnap.ctd.hint.Canonicalization.SwapSymmetricArgs;
import edu.isnap.ctd.util.StringHashable;
import util.LblTree;

public class Node extends StringHashable {

	public static int PrettyPrintSpacing = 2;

	private String type;
	// Annotations used to specify that nodes have a non-concrete meaning, such as having a partial
	// order or wildcard children
	private Annotations annotations;

	public final String id;
	public final String value;
	public final Node parent;
	public final List<Node> children = new ArrayList<>();

	public transient Object tag;
	public final transient List<Canonicalization> canonicalizations = new ArrayList<>();

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

	public Annotations writableAnnotations() {
		if (annotations == null) {
			annotations = new Annotations();
		}
		return annotations;
	}

	public Annotations readOnlyAnnotations() {
		return annotations == null ? Annotations.EMPTY : annotations;
	}

	@SuppressWarnings("unused")
	private Node() {
		this(null, null, null, null);
	}

	public Node(Node parent, String type) {
		this(parent, type, null, null);
	}

	public Node(Node parent, String type, String value) {
		this(parent, type, value, null);
	}

	public Node(Node parent, String type, String value, String id) {
		this.parent = parent;
		this.type = type;
		this.value = value;
		this.id = id;
	}

	public Node setOrderGroup(int group) {
		writableAnnotations().orderGroup = group;
		return this;
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

	public List<Node> allChildren() {
		return allChildren(new LinkedList<Node>());
	}

	public List<Node> allChildren(List<Node> list) {
		for (Node child : children) {
			list.add(child);
			child.allChildren(list);
		}
		return list;
	}

	public List<Node> searchAll(Predicate predicate) {
		List<Node> list = new LinkedList<>();
		searchAll(predicate, list);
		return list;
	}

	public void searchAll(Predicate predicate, List<Node> list) {
		if (predicate.eval(this)) list.add(this);
		for (Node node : children) {
			node.searchAll(predicate, list);
		}
	}

	public int searchChildren(Predicate pred) {
		return searchChildren(pred, 0);
	}

	public int searchChildren(Predicate pred, int startIndex) {
		return searchChildren(pred, startIndex, children.size());
	}

	public int searchChildren(Predicate pred, int startIndex, int endIndexExclusive) {
		endIndexExclusive = Math.min(endIndexExclusive, children.size());
		for (int i = startIndex; i < endIndexExclusive; i++) {
			if (pred.eval(children.get(i))) return i;
		}
		return -1;
	}

	public Node searchForNodeWithID(final String id) {
		if (id == null) return null;
		return search(new Predicate() {
			@Override
			public boolean eval(Node node) {
				return id.equals(node.id);
			}
		});
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
			return node != null && node.hasType(types);
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

	public boolean hasType(String... types) {
		for (String type : types) {
			if (type.equals(this.type)) return true;
		}
		return false;
	}

	public boolean parentHasType(String... types) {
		return parent != null && parent.hasType(types);
	}

	public boolean childHasType(String type, int index) {
		return index < children.size() && children.get(index).hasType(type);
	}

	public Node copy() {
		Node rootCopy = root().copyWithNewParent(null);
		return findParallelNode(rootCopy, this);
	}

	public Node copyWithNewParent(Node parent) {
		Node copy = shallowCopy(parent);
		for (Node child : children) {
			copy.children.add(child.copyWithNewParent(copy));
		}
		return copy;
	}

	public Node shallowCopy(Node parent) {
		Node copy = new Node(parent, type, value, id);
		copy.tag = tag;
		copy.annotations = annotations == null ? null : annotations.copy();
		return copy;
	}

	public Node addChild(String type) {
		Node child = new Node(this, type);
		children.add(child);
		return child;
	}

	private static Node findParallelNode(Node root, Node child) {
		if (child.parent == null) return root;

		Node parent = findParallelNode(root, child.parent);
		return parent.children.get(child.index());
	}

	public int treeSize() {
		int size = 1;
		for (Node child : children) size += child.treeSize();
		return size;
	}

	public String[] getChildArray() {
		String[] array = new String[children.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = children.get(i).type;
		}
		return array;
	}

	// TODO: Move to snap-specific project
	private final static String[] HAS_BODY = new String[] {
			"snapshot", "stage", "sprite", "script", "customBlock",
	};

	public String prettyPrint() {
		return prettyPrint("", false, null);
	}

	public String prettyPrint(boolean showValues) {
		return prettyPrint("", showValues, null);
	}

	public String prettyPrint(Map<Node, String> prefixMap) {
		return prettyPrint("", false, prefixMap);
	}

	public String prettyPrintWithIDs() {
		return prettyPrint(new HashMap<Node, String>());
	}

	private String prettyPrint(String indent, boolean showValues, Map<Node, String> prefixMap) {
		boolean inline = true;
		for (String hasBody : HAS_BODY) {
			if (type.equals(hasBody)) {
				inline = false;
				break;
			}
		}
		String out = type;
		if (showValues && value != null) {
			out = "[" + out + "=" + value + "]";
		}
		if (prefixMap != null) {
			if (prefixMap.containsKey(this)) out = prefixMap.get(this) + ":" + out;
			if (id != null && !id.equals(type)) {
				out += "[" + id + "]";
			}
		}
		if (annotations != null) {
			out += annotations;
		}
		if (children.size() > 0) {
			if (inline) {
				out += "(";
				for (int i = 0; i < children.size(); i++) {
					if (i > 0) out += ", ";
					if (children.get(i) == null) {
						out += "null";
						continue;
					}
					out += children.get(i).prettyPrint(indent, showValues, prefixMap);
				}
				out += ")";
			} else {
				out += " {\n";
				String indentMore = indent;
				for (int i = 0; i < PrettyPrintSpacing; i++) indentMore += " ";
				for (int i = 0; i < children.size(); i++) {
					if (children.get(i) == null) {
						out += indentMore + "null\n";
						continue;
					}
					out += indentMore + children.get(i).prettyPrint(
							indentMore, showValues, prefixMap) + "\n";
				}
				out += indent + "}";
			}
		}
		return out;
	}

	public String[] depthFirstIteration() {
		String[] array = new String[treeSize()];
		depthFirstIteration(array, 0);
		return array;
	}

	private int depthFirstIteration(String[] array, int offset) {
		array[offset++] = type;
		for (Node child : children) {
			offset = child.depthFirstIteration(array, offset);
		}
		return offset;
	}

	public Node nthDepthFirstNode(int index) {
		if (index == 0) return this;
		index--;
		for (Node child : children) {
			int size = child.treeSize();
			if (size > index) {
				return child.nthDepthFirstNode(index);
			}
			index -= size;
		}
		return null;
	}

	// TODO: This is really quite an ugly Node reference representation...
	public static JSONObject getNodeReference(Node node) {
		if (node == null) return null;

		String label = node.type();
		for (Canonicalization c : node.canonicalizations) {
			if (c instanceof InvertOp) {
//				System.out.println("Invert: " + node);
				label = ((InvertOp) c).name;
				break;
			}
		}

		int index = node.index();
		if (node.parent != null) {
			for (Canonicalization c : node.parent.canonicalizations) {
				// TODO: Shouldn't this also occur for an InvertOp?
				if (c instanceof SwapSymmetricArgs) {
//					System.out.println("Swapping children of: " + node.parent);
					index = node.parent.children.size() - 1 - index;
					break;
				}
			}
		}

		JSONObject parent = getNodeReference(node.parent);

		JSONObject obj = new JSONObject();
		obj.put("label", label);
		obj.put("index", index);
		obj.put("parent", parent);

		return obj;
	}

	public int rootPathLength() {
		if (parent == null) return 1;
		return 1 + parent.rootPathLength();
	}

	public static class Annotations {
		public static final Annotations EMPTY = new Annotations();

		public boolean matchAnyChildren;
		public int orderGroup;

		public Annotations copy() {
			Annotations copy = new Annotations();
			copy.orderGroup = orderGroup;
			copy.matchAnyChildren = matchAnyChildren;
			return copy;
		}

		@Override
		public String toString() {
			String out = "";
			if (orderGroup != 0) out += "<" + orderGroup + ">";
			if (matchAnyChildren) out += "<*>";
			return out;
		}
	}

	public void resetAnnotations() {
		recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.readOnlyAnnotations().matchAnyChildren) {
					node.children.clear();
				}
			}
		});
	}

	public String rootPath() {
		List<String> path = new LinkedList<>();
		Node n = this;
		while (n != null) {
			path.add(0, n.type);
			n = n.parent;
		}
		return path.toString();
	}
}
