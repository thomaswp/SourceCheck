package edu.isnap.rating;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.TreeBag;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.isnap.node.ASTNode;
import edu.isnap.node.CodeAlignment;
import edu.isnap.node.CodeAlignment.NodePairs;
import edu.isnap.util.Spreadsheet;
import edu.isnap.util.map.BiMap;
import node.Node;

public class EditExtractor {

	private final RatingConfig config;

	private final Set<String> ignoreTypes = new HashSet<>();

	public EditExtractor(RatingConfig config, String... ignoreTypes) {
		this.config = config;
		Arrays.stream(ignoreTypes).forEach(this.ignoreTypes::add);
	}

	public Bag<Edit> getEdits(ASTNode from, ASTNode to) {
		return extractEditsUsingCodeAlign(from, to);
	}

	public static Node<ASTNode> toNode(ASTNode astNode) {
		Node<ASTNode> node = new Node<>(astNode);
		if (astNode != null) {
			for (ASTNode child : astNode.children()) {
				node.addChild(toNode(child));
			}
		}
		return node;
	}

	public static void printEditsComparison(Bag<Edit> editsA, Bag<Edit> editsB,
			String nameA, String nameB) {
		Bag<Edit> a = new TreeBag<>(editsA),
				b = new TreeBag<>(editsB),
				both = new TreeBag<>(editsB);
		a.removeAll(editsB);
		b.removeAll(editsA);
		both.retainAll(editsA);

		System.out.println("Both:");
		both.forEach(System.out::println);
		System.out.println(nameA + " Only:");
		a.forEach(System.out::println);
		System.out.println(nameB + "Only:");
		b.forEach(System.out::println);
	}

	public Bag<Edit> extractEditsUsingCodeAlign(ASTNode from, ASTNode to) {
		NodePairs pairs = getPairs(from, to);

		Bag<Edit> edits = new TreeBag<>();

		from.recurse(n -> {
			if (!pairs.containsFrom(n)) {
				edits.add(new Deletion(getReferenceAsChildren(n)));
			}
		});
		to.recurse(n -> {
			if (!pairs.containsTo(n)) {
				NodeReference reference = getInsertReference(n, pairs);
				if (reference instanceof ChildNodeReference && reference.value != null) {
					// If this insertion has a non-null value, we split it into two insertions,
					// one for the type and one for the value. The latter can only match if the
					// former does, but this makes it possible to have a partially matching hint
					// that inserts a node with the correct type, but either specifies a new
					// value or specifies no value
					NodeValueReference valueRef = ((ChildNodeReference) reference).decompose();
					edits.add(new Insertion(valueRef.parent));
					edits.add(new Insertion(valueRef));
				} else {
					edits.add(new Insertion(reference));
				}
			}
		});
		from.recurse(n -> {
			ASTNode pair = pairs.getFrom(n);
			if (pair != null && !n.shallowEquals(pair, false)) {
				edits.add(new Relabel(getReferenceAsChildren(n), pair.type, pair.value));
			}
		});

		edits.removeIf(edit -> ignoreTypes.contains(edit.node.type));

		return edits;
	}

	private static NodePairs getPairs(ASTNode from, ASTNode to) {
		// Make renames as expensive as an insert/delete to require at least 1 child match
		// before it will be cheaper to rename.
		// TODO: There are some tradeoffs with using 1 or 2 in the second value; should explore
		NodePairs pairs = new CodeAlignment(1, 2).align(from, to);
		return pairs;
	}

	public static List<ASTNode> getInsertedAndRenamedNodes(ASTNode from, ASTNode to) {
		NodePairs pairs = getPairs(from, to);

		List<ASTNode> inserted = new ArrayList<>();
		to.recurse(n -> {
			if (!pairs.containsTo(n) || !n.shallowEquals(pairs.getTo(n), false)) {
				inserted.add(n);
			}
		});

		return inserted;
	}

	public static NodeReference getReference(ASTNode node) {
		if (node.id != null) return new IDNodeReference(node);
		if (node.parent() == null) return new RootNodeReference(node);
		return new ChildNodeReference(node, getReference(node.parent()));
	}

	public static NodeReference getReferenceAsChild(ASTNode node) {
		if (node.parent() == null) return new RootNodeReference(node);
		return new ChildNodeReference(node, getReference(node.parent()));
	}

	private static NodeReference getReferenceAsChildren(ASTNode node) {
		if (node.parent() == null) return new RootNodeReference(node);
		return new ChildNodeReference(node, getReferenceAsChildren(node.parent()));
	}

	// TODO: This is problematic because we lose insert order (e.g. +(a, b) == +(b, a)), which
	// allows the possibility of an exact match of edits without actually matching. This still
	// constitutes a partial match. How can we make +(a, b) match +(b) but not +(a, b) == +(b, a)?
	private NodeReference getInsertReference(ASTNode node, BiMap<ASTNode, ASTNode> map) {
		if (node.parent() == null) throw new RuntimeException("Root nodes cannot be inserted :/");
		ASTNode parentPair = map.getTo(node.parent());
		int index = node.index();
		if (map != null && parentPair != null &&
				!config.hasFixedChildren(parentPair.type, parentPair.parentType())) {
			// If the node has a parent with a from-match, we look through its earlier siblings and
			// find the first one with a match (not inserted or deleted) and we mark the node as
			// inserted right after it
			List<ASTNode> siblings = node.parent().children();
			int originalIndex = index;
			index = 0;
			for (int i = originalIndex - 1; i >= 0; i--) {
				ASTNode siblingFrom = map.getTo(siblings.get(i));
				if (siblingFrom != null) {
					index = siblingFrom.index() + 1;
					break;
				}
			}
		} else {
			index = node.index();
		}

		// If possible, reference the parent pair in the from-AST; otherwise, continue to walk up
		// the to-AST
		NodeReference parentReference = parentPair != null ?
					getReferenceAsChildren(parentPair) :
					getInsertReference(node.parent(), map);
		return new ChildNodeReference(node, parentReference, index);
	}

	public static abstract class Edit implements Comparable<Edit> {
		final NodeReference node;

		Edit(NodeReference node) {
			this.node = node;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (obj.getClass() != getClass()) return false;
			Edit rhs = (Edit) obj;
			return node.equals(rhs.node);
		}

		@Override
		public int hashCode() {
			return node.hashCode() * 13 + getClass().hashCode();
		}

		@Override
		public int compareTo(Edit o) {
			int c0 = toString().compareTo(o.toString());
			if (c0 != 0) return c0;
			return Integer.compare(hashCode(), o.hashCode());
		}
	}

	static class Insertion extends Edit {
		Insertion(NodeReference node) {
			super(node);
			if (node instanceof IDNodeReference) {
				// If the node has an ID in the from node, it ought to have a match as well
				throw new RuntimeException("Insertions cannot use an IDNodeReference");
			}
		}

		@Override
		public String toString() {
			return "I: " + node.toString();
		}
	}

	static class Deletion extends Edit {
		Deletion(NodeReference node) {
			super(node);
		}

		@Override
		public String toString() {
			return "D: " + node.toString();
		}
	}

	static class Relabel extends Edit {
		public final String type, value;

		Relabel(NodeReference node, String type, String value) {
			super(node);
			this.type = type;
			this.value = value;
		}

		@Override
		public String toString() {
			if (node.value == null && value == null) {
				return String.format("R: %s: [%s => %s]",
						node, node.type, type);
			}
			return String.format("R: %s: [%s(%s) => %s(%s)]",
					node, node.type, node.value, type, value);
		}

		@Override
		public int hashCode() {
			HashCodeBuilder builder = new HashCodeBuilder(17, 3);
			builder.appendSuper(super.hashCode());
			builder.append(type);
			builder.append(value);
			return builder.toHashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (obj.getClass() != getClass()) return false;
			Relabel rhs = (Relabel) obj;
			EqualsBuilder builder = new EqualsBuilder();
			builder.appendSuper(super.equals(rhs));
			builder.append(type, rhs.type);
			builder.append(value, rhs.value);
			return builder.isEquals();
		};
	}

	static class Move extends Edit {

		final ChildNodeReference newPosition;

		Move(NodeReference node, ChildNodeReference newPosition) {
			super(node);
			this.newPosition = newPosition;
		}

		@Override
		public boolean equals(Object obj) {
			return super.equals(obj) && newPosition.equals(((Move) obj).newPosition);
		}

		@Override
		public int hashCode() {
			return super.hashCode() * 13 + newPosition.hashCode();
		}

		@Override
		public String toString() {
			return "M: " + node.toString() + " -> " + newPosition.toString();
		}
	}

	static abstract class NodeReference {
		final String type;
		final String value;

		NodeReference(ASTNode node) {
			this(node.type, node.value);
		}

		private NodeReference(String type, String value) {
			this.type = type;
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (obj == this) return true;
			if (obj.getClass() != getClass()) return false;
			NodeReference rhs = (NodeReference) obj;
			EqualsBuilder builder = new EqualsBuilder();
			addEqualsFields(rhs, builder);
			return builder.isEquals();
		}

		protected void addEqualsFields(NodeReference rhs, EqualsBuilder builder) {
			builder.append(type, rhs.type);
			builder.append(value, rhs.value);
		}

		@Override
		public int hashCode() {
			HashCodeBuilder builder = new HashCodeBuilder(15, 3);
			addHashCodeFields(builder);
			return builder.toHashCode();
		}

		protected void addHashCodeFields(HashCodeBuilder builder) {
			builder.append(type);
			builder.append(value);
		}
	}

	static class IDNodeReference extends NodeReference {
		final String id;

		IDNodeReference(ASTNode node) {
			super(node);
			id = node.id;
		}

		@Override
		protected void addEqualsFields(NodeReference rhs, EqualsBuilder builder) {
			super.addEqualsFields(rhs, builder);
			builder.append(id, ((IDNodeReference) rhs).id);
		}

		@Override
		protected void addHashCodeFields(HashCodeBuilder builder) {
			super.addHashCodeFields(builder);
			builder.append(id);
		}

		@Override
		public String toString() {
			return "{" + id + "}";
		}
	}

	static class ChildNodeReference extends NodeReference  {
		final NodeReference parent;
		final int index;

		public ChildNodeReference(ASTNode node, NodeReference parent) {
			this(node, parent, node.index());
		}

		public ChildNodeReference(ASTNode node, NodeReference parent, int index) {
			super(node);
			if (parent == null) throw new IllegalArgumentException("Parent ref cannot be null");
			this.parent = parent;
			this.index = index;
		}

		private ChildNodeReference(String type, NodeReference parent, int index) {
			super(type, null);
			this.parent = parent;
			this.index = index;
		}

		@Override
		protected void addEqualsFields(NodeReference rhs, EqualsBuilder builder) {
			super.addEqualsFields(rhs, builder);
			builder.append(parent, ((ChildNodeReference) rhs).parent);
			builder.append(index, ((ChildNodeReference) rhs).index);
		}

		@Override
		protected void addHashCodeFields(HashCodeBuilder builder) {
			super.addHashCodeFields(builder);
			builder.append(parent);
			builder.append(index);
		}

		@Override
		public String toString() {
			return String.format("%s->{%s#%02d}", parent.toString(), type, index);
		}

		NodeValueReference decompose() {
			ChildNodeReference withoutValue = new ChildNodeReference(type, parent, index);
			return new NodeValueReference(withoutValue, value);
		}
	}

	static class NodeValueReference extends NodeReference {

		final NodeReference parent;

		NodeValueReference(NodeReference parent, String value) {
			super(null, value);
			if (parent.value != null) {
				// If the parent has a value, using a node value reference is pointless, since
				// it can only match other references if the parents match
				throw new IllegalArgumentException("Parent reference must not have a value");
			}
			this.parent = parent;
		}

		@Override
		protected void addEqualsFields(NodeReference rhs, EqualsBuilder builder) {
			super.addEqualsFields(rhs, builder);
			builder.append(parent, ((NodeValueReference) rhs).parent);
		}

		@Override
		protected void addHashCodeFields(HashCodeBuilder builder) {
			super.addHashCodeFields(builder);
			builder.append(parent);
		}

		@Override
		public String toString() {
			return String.format("%s->{'%s'}", parent.toString(), value);
		}

	}

	static class RootNodeReference extends NodeReference {
		public RootNodeReference(ASTNode node) {
			super(node);
		}

		@Override
		public String toString() {
			return "root";
		}
	}

	public static void addEditInfo(Spreadsheet spreadsheet, Bag<Edit> edits) {
		int nInsertions = 0, nDeletions = 0, nRelabels = 0, nValueInsertions = 0;
		for (Edit edit : edits) {
			if (edit instanceof Insertion) {
				nInsertions++;
				if (edit.node instanceof NodeValueReference) nValueInsertions++;
			}
			else if (edit instanceof Deletion) nDeletions++;
			else if (edit instanceof Relabel) nRelabels++;
		}
		spreadsheet.put("nInsertions", nInsertions);
		spreadsheet.put("nDeletions", nDeletions);
		spreadsheet.put("nRelabels", nRelabels);
		spreadsheet.put("nValueInsertions", nValueInsertions);
	}
}
