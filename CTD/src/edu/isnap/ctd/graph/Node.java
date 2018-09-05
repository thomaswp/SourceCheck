package edu.isnap.ctd.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.hint.Canonicalization;
import edu.isnap.ctd.hint.Canonicalization.Rename;
import edu.isnap.ctd.hint.Canonicalization.Reorder;
import edu.isnap.ctd.hint.Canonicalization.SwapBinaryArgs;
import edu.isnap.ctd.util.StringHashable;
import util.LblTree;

public abstract class Node extends StringHashable implements INode {

	public static int PrettyPrintSpacing = 2;
	public static boolean PrettyPrintUseColon = false;

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

	/**
	 * Constructs a new node with the given parameters. Subtypes of Node should override to return
	 * a new instance of that type.
	 */
	public abstract Node constructNode(Node parent, String type, String value, String id);

	protected abstract boolean nodeTypeHasBody(String type);

	@Override
	public String type() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
		recache();
	}

	@Override
	public String value() {
		return value;
	}

	@Override
	public String id() {
		return id;
	}

	@Override
	public Node parent() {
		return parent;
	}

	@Override
	public List<Node> children() {
		return children;
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
		return String.format("%s%s%s",
				// TODO: Make sure adding value here doesn't break everything
				type, value == null ? "" : ("=" + value),
				children.size() == 0 ? "" : (":" + toCannonicalString(children)));
	}

	public LblTree toTree() {
		LblTree tree = new LblTree(type, 0);
		tree.setUserObject(this);
		for (Node node : children) tree.add(node.toTree());
		return tree;
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

	public Node searchForNodeWithType(final String type) {
		if (type == null) return null;
		return search(new Predicate() {
			@Override
			public boolean eval(Node node) {
				return type.equals(node.type);
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

	public static class BackbonePredicate implements Predicate
	{
		private final String[] backbone;

		public BackbonePredicate(String... backbone) 
		{
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

	public boolean shallowEquals(Node node) {
		if (node == null) return false;
		return eq(type, node.type) && eq(value, node.value);
	}

	private boolean eq(String a, String b) {
		if (a == null) return b == null;
		return a.equals(b);
	}

	public String parentType() {
		return parent == null ? null : parent.type;
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
			copy.children.add(child == null ? null : child.copyWithNewParent(copy));
		}
		return copy;
	}

	public final Node constructNode(Node parent, String type) {
		return constructNode(parent, type, null, null);
	}

	public Node shallowCopy(Node parent) {
		Node copy = constructNode(parent, type, value, id);
		copy.tag = tag;
		copy.annotations = annotations == null ? null : annotations.copy();
		return copy;
	}

	public Node addChild(String type) {
		Node child = constructNode(this, type);
		children.add(child);
		return child;
	}

	/**
	 * Searches copyRoot and its children for a Node that matches node's ID, or if no unique ID
	 * can be matched, searches for a node with the same position in copyRoot as node has in its
	 * root. If either parameter is null or no match can be found, returns null.
	 */
	public static Node findMatchingNodeInCopy(Node node, Node copyRoot) {
		if (node == null || copyRoot == null) return null;

		// Fist try to find the single match with the same ID
		if (node.id != null) {
			List<Node> matches = copyRoot.searchAll(n -> StringUtils.equals(n.id, node.id));
			if (matches.size() == 1) return matches.get(0);
		}

		// If there isn't a single match, find our parent's match...
		Node parent = findMatchingNodeInCopy(node.parent, copyRoot);
		int index = node.index();
		if (parent == null || index >= parent.children.size()) return null;
		// ... and try to find the child that correspond to this index
		return parent.children.get(index);
	}

	/**
	 * See {@link Node#findMatchingNodeInCopy(Node, Node)}
	 */
	public Node findMatchingNodeInCopy(Node copyRoot) {
		return findMatchingNodeInCopy(this, copyRoot);
	}

	private static Node findParallelNode(Node root, Node child) {
		if (child.parent == null) return root;

		Node parent = findParallelNode(root, child.parent);
		int index = child.index();
		if (parent == null || index >= parent.children.size()) return null;
		return parent.children.get(index);
	}

	public String getTypeValueString() {
		if (type.contains(":")) {
			throw new RuntimeException("Illegal character in type: " + type);
		}
		return type + (value == null ? "" : (":" + value));
	}

	public String[] getChildTypeValueArray() {
		String[] array = new String[children.size()];
		for (int i = 0; i < array.length; i++) {
			Node child = children.get(i);
			array[i] = child.getTypeValueString();
		}
		return array;
	}

	public String[] getChildArray() {
		String[] array = new String[children.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = children.get(i).type;
		}
		return array;
	}

	protected static class Params extends PrettyPrint.Params {

		Map<Node, String> prefixMap;

		public Params(boolean showValues, Map<Node, String> prefixMap,
				java.util.function.Predicate<String> isBodyType) {
			this.showValues = showValues;
			this.indent = PrettyPrintSpacing;
			this.prefixMap = prefixMap;
			this.isBodyType = isBodyType;
			if (PrettyPrintUseColon) {
				this.surroundValueAssignments = false;
				this.valueAssignment = ":";
			}
		}

		@Override
		public String baseString(INode node) {
			StringBuilder sb = new StringBuilder(super.baseString(node));
			if (prefixMap != null) {
				if (prefixMap.containsKey(node)) {
					sb.insert(0, prefixMap.get(node) + ":");
				}
				String id = node.id();
				if (id != null && !id.equals(node.type())) {
					sb.append("[").append(id).append("]");
				}
			}
			if (node instanceof Node) {
				Annotations annotations = ((Node) node).annotations;
				if (annotations != null) sb.append(annotations);
			}
			return sb.toString();
		}
	}

	public String prettyPrint() {
		return prettyPrint(false, null);
	}

	public String prettyPrint(boolean showValues) {
		return prettyPrint(showValues, null);
	}

	public String prettyPrint(boolean showValues, Map<Node, String> prefixMap) {
		return PrettyPrint.toString(this, new Params(showValues, prefixMap, this::nodeTypeHasBody));
	}

	public String prettyPrintWithIDs() {
		return prettyPrint(false, new HashMap<Node, String>());
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
			if (c instanceof Rename) {
//				System.out.println("Invert: " + node);
				label = ((Rename) c).name;
				break;
			}
		}

		int index = node.index();
		if (node.parent != null) {
			for (Canonicalization c : node.parent.canonicalizations) {
				if (c instanceof SwapBinaryArgs) {
//					System.out.println("Swapping children of: " + node.parent);
					index = node.parent.children.size() - 1 - index;
					break;
				}
				if (c instanceof Reorder) {
					int[] reorderings = ((Reorder) c).reordering;
					int originalIndex = index;
					index = ArrayUtils.indexOf(reorderings, index);
					if (index == -1) {
						System.err.println(node.parent);
						System.err.println(node.type);
						throw new RuntimeException("Invalid reorder index: " +
								originalIndex + ", " + Arrays.toString(reorderings));
					}
				}
			}
		}

		JSONObject parent = getNodeReference(node.parent);

		JSONObject obj = new JSONObject();
		obj.put("label", label);
		obj.put("index", index);
		obj.put("parent", parent);
		if (node.value != null) obj.put("value", node.value);

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

	public String rootPathString() {
		return rootPathString(Integer.MAX_VALUE);
	}

	public String rootPathString(int maxLength) {
		List<String> path = new LinkedList<>();
		Node n = this;
		int length = 0;
		while (n != null && length++ < maxLength) {
			path.add(0, n.type);
			n = n.parent;
		}
		return String.join("->", path);
	}

	public JSONObject toJSON() {
		JSONObject object = new JSONObject();
		object.put("type", type);
		if (value != null) object.put("value", value);
		if (id != null) object.put("id", id);
		if (children.size() > 0) {
			JSONArray childArray = new JSONArray();
			for (Node child : children) {
				childArray.put(child.toJSON());
			}
			object.put("children", childArray);
		}
		return object;
	}

	public interface NodeConstructor {
		Node constructNode(Node parent, String type, String value, String id);
	}

	public ASTSnapshot toASTSnapshot(boolean isCorrect, String source) {
		ASTSnapshot node = new ASTSnapshot(type, value, id, isCorrect, source);
		children.forEach(child -> node.addChild(child == null ? null : child.toASTNode()));
		return node;
	}

	public ASTNode toASTNode() {
		ASTNode node = new ASTNode(type, value, id);
		children.forEach(child -> node.addChild(child == null ? null : child.toASTNode()));
		return node;
	}

	@Override
	public String toString() {
		return prettyPrint(true);
	}
}
