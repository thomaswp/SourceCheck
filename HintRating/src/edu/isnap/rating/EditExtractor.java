package edu.isnap.rating;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.TreeBag;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import costmodel.CostModel;
import distance.APTED;
import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.CodeAlignment;
import edu.isnap.ctd.graph.CodeAlignment.NodePairs;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.hint.util.Spreadsheet;
import node.Node;

public class EditExtractor {

	private final Map<ASTNode, Bag<Edit>> cache = new IdentityHashMap<>();

	private final RatingConfig config;

	private final Set<String> ignoreTypes = new HashSet<>();

	public EditExtractor(RatingConfig config, String... ignoreTypes) {
		this.config = config;
		Arrays.stream(ignoreTypes).forEach(this.ignoreTypes::add);
	}

	public Bag<Edit> getEdits(ASTNode from, ASTNode to) {
		Bag<Edit> edits = cache.get(to);
		if (edits == null) {
//			edits = config.areNodeIDsConsistent() ?
//					extractEditsUsingIDs(from, to) : extractEditsUsingTED(from, to);
			edits = extractEditsUsingCodeAlign(from, to);
			cache.put(to, edits);
		} else {
//			System.out.println("CACHE");
			// TODO: The cache is not working, since we clone the nodes and it works on identity
		}
		return edits;
	}

	private static CostModel<ASTNode> costModel = new CostModel<ASTNode>() {
		@Override
		public float ren(Node<ASTNode> nodeA, Node<ASTNode> nodeB) {
			// If the nodes are equal, there is no cost to "rename"
			if (nodeA.getNodeData().shallowEquals(nodeB.getNodeData(), false)) {
				return 0;
			}

			// If the Nodes are not equal but of the same type, they should always be renamed, but
			// it is not completely free
			if (StringUtils.equals(nodeA.getNodeData().type(), nodeB.getNodeData().type())) {
				return 0.1f;
			}

			// This was useful when comparing the ID-based and TED-based methods, but if we're using
			// TED, the IDs aren't reliable, so they certainly shouldn't be used here
//			if (StringUtils.equals(nodeA.getNodeData().id, nodeB.getNodeData().id)) return 0.1f;

			// Otherwise, renaming should be more expensive than insertion/deletion
			return 2.1f;
		}

		@Override
		public float ins(Node<ASTNode> node) {
			return 1f;
		}

		@Override
		public float del(Node<ASTNode> node) {
			return 1f;
		}
	};

	public static Node<ASTNode> toNode(ASTNode astNode) {
		Node<ASTNode> node = new Node<>(astNode);
		if (astNode != null) {
			for (ASTNode child : astNode.children()) {
				node.addChild(toNode(child));
			}
		}
		return node;
	}

	private static List<ASTNode> postOrderList(ASTNode node, List<ASTNode> list) {
		for (ASTNode child : node.children()) postOrderList(child, list);
		list.add(node);
		return list;
	}

	private static ASTNode readPair(List<ASTNode> list, int index) {
		if (index == 0) return null;
		return list.get(index - 1);
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
		// TODO: There are some tradeoffs with using 1 or 2 in the second value
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

	// TODO: Remove APTED, etc.
	public static Set<Edit> extractEditsUsingTED(ASTNode fromAST, ASTNode toAST) {
		Node<ASTNode> from = toNode(fromAST), to = toNode(toAST);
		APTED<CostModel<ASTNode>, ASTNode> apted = new APTED<>(costModel);
		apted.computeEditDistance(from, to);
		LinkedList<int[]> editMapping = apted.computeEditMapping();
		List<ASTNode> fromChildren = postOrderList(from.getNodeData(), new ArrayList<>());
		List<ASTNode> toChildren = postOrderList(to.getNodeData(), new ArrayList<>());
		Set<Edit> edits = new TreeSet<>();
		BiMap<ASTNode, ASTNode> mapping = new BiMap<>(IdentityHashMap::new);
		for (int[] pair : editMapping) {
			ASTNode fromPair = readPair(fromChildren, pair[0]);
			ASTNode toPair = readPair(toChildren, pair[1]);
			if (fromPair != null && toPair != null) mapping.put(fromPair, toPair);
		}
		List<ASTNode> inserted = new ArrayList<>();
		List<ASTNode> deleted = new ArrayList<>();
		// First go through every pair and insert the new ones, delete the missing ones and do both
		// for the relabeled ones
		for (int[] pair : editMapping) {
			ASTNode fromPair = readPair(fromChildren, pair[0]);
			ASTNode toPair = readPair(toChildren, pair[1]);
			if (fromPair == null) {
				edits.add(new Insertion(getReferenceAsChildren(toPair)));
				inserted.add(toPair);
			} else if (toPair == null) {
				edits.add(new Deletion(getReferenceAsChildren(fromPair)));
				deleted.add(fromPair);
			} else if (!fromPair.shallowEquals(toPair, false)) {
				edits.add(new Deletion(getReferenceAsChildren(fromPair)));
				edits.add(new Insertion(getReferenceAsChildren(toPair)));
			}
		}
		// Next for through the children of any inserted node and mark them as inserted (and any
		// pair as deleted). This will only effect nodes inserted in between two nodes, as TED
		// insertions can do. Since AST edits rarely ever do this, we assume these matched children
		// are not actually matched, and treat them as insertions/deletions, the same way we treat
		// moves as insertions/deletions in the ID-based code below. The only danger here is that
		// a deletion can match an insertion partially, but that is always a danger with partial
		// matching.
		for (ASTNode toNode : inserted) {
			if (toNode.children().isEmpty()) continue;
			toNode.recurse((child) -> {
				if (child == toNode) return;
				edits.add(new Insertion(getReferenceAsChildren(child)));
				ASTNode fromChild = mapping.getTo(child);
				if (fromChild != null) {
					edits.add(new Deletion(getReferenceAsChildren(fromChild)));
				}
			});
		}
		// Similarly, the children of any deleted nodes should be considered deleted as well.
		for (ASTNode fromNode : deleted) {
			if (fromNode.children().isEmpty()) continue;
			fromNode.recurse((child) -> {
				if (child == fromNode) return;
				edits.add(new Deletion(getReferenceAsChildren(child)));
				ASTNode toChild = mapping.getFrom(child);
				if (toChild != null) {
					edits.add(new Insertion(getReferenceAsChildren(toChild)));
				}
			});
		}
		return edits;
	}

	public static Set<Edit> extractEditsUsingIDs(ASTNode from, ASTNode to) {
		BiMap<ASTNode, ASTNode> mapping = new BiMap<>(IdentityHashMap::new);
		Map<String, ASTNode> fromMap = getIDMap(from);
		Map<String, ASTNode> toMap = getIDMap(to);
		for (String id : fromMap.keySet()) {
			ASTNode toNode = toMap.get(id);
			if (toNode != null) {
				mapping.put(fromMap.get(id), toNode);
			}
		}

		return extractEdits(from, to, mapping);
	}

	private static Map<String, ASTNode> getIDMap(ASTNode node) {
		Map<String, ASTNode> map = new HashMap<>();
		node.recurse(child -> {
			if (child.id != null) {
				if (map.put(child.id, child) != null) {
					System.err.println(node.prettyPrint(true, RatingConfig.Snap));
					throw new RuntimeException("Duplicate ids in node: " + child.id);
				}
			}
		});
		return map;
	}

	private static Set<Edit> extractEdits(ASTNode from, ASTNode to,
			BiMap<ASTNode, ASTNode> mapping) {

		// First get sets of all references in the from and to AST
		Set<NodeReference> fromRefs = new HashSet<>();
		from.recurse(node -> {
			NodeReference ref = getReference(node);
			if (ref != null) fromRefs.add(ref);
		});

		Set<NodeReference> toRefs = new HashSet<>();
		to.recurse(node -> {
			NodeReference ref = getReferenceInPair(node, mapping);
			if (ref != null) {
				if (!toRefs.add(ref)) {
					throw new RuntimeException("Duplicate references in node: " + ref);
				}
			}
		});

		// The removed refs are those present in from and not to, with added being the reverse
		Set<NodeReference> removedRefs = new HashSet<>(fromRefs);
		removedRefs.removeAll(toRefs);
		Set<NodeReference> addedRefs = new HashSet<>(toRefs);
		addedRefs.removeAll(fromRefs);

		// Find all nodes that have been moved to a new parent and mark them as having been removed
		// from their previous location and added to their new location
		Map<ASTNode, Void> moved = new IdentityHashMap<>();
		to.recurse(toNode -> {
			if (toNode.parent() == null) return;
			ASTNode fromMatch = mapping.getTo(toNode);
			if (fromMatch == null) return;
			NodeReference fromParentMatch = getReferenceInPair(toNode.parent(), mapping);
			NodeReference fromMatchParent = getReference(fromMatch.parent());
			// Only consider nodes whose parents have changed
			if (ObjectUtils.equals(fromParentMatch, fromMatchParent)) return;
			moved.put(toNode, null);

			// Temporarily, we add these to the added and moved refs, so we know to differentiate
			// between nodes which have been moved, and those which have earlier siblings
			// added/deleted/moved
			addedRefs.add(getReferenceInPair(toNode, mapping));
			removedRefs.add(getReference(fromMatch));
		});

		Set<Edit> edits = new LinkedHashSet<>();

		// We keep track of the refs that have been moved, so we don't double-count them as added
		// and removed
		Set<NodeReference> movedFrom = new HashSet<>();
		Set<NodeReference> movedTo = new HashSet<>();

		// Keep track of no-id nodes that have been displaced by earlier siblings being changed,
		// so their references will have changed, but they have not actually been added/remvoed
		Set<NodeReference> displacedFrom = new HashSet<>();
		Set<NodeReference> displacedTo = new HashSet<>();

		// Now we find all instances of node movement
		to.recurse(toNode -> {
			if (toNode.parent() == null) return;
			ASTNode fromMatch = mapping.getTo(toNode);
			if (fromMatch == null) return;

			// First we check to make sure the node is actually moved and not that earlier siblings
			// have been changed
			if (!moved.containsKey(toNode)) {
				NodeReference precederMatch = null;
				for (int i = toNode.index() - 1; i >= 0; i--) {
					NodeReference pm = getReferenceInPair(toNode.parent().children().get(i), mapping);
					if (!addedRefs.contains(pm)) {
						precederMatch = pm;
						break;
					}
				}

				NodeReference matchPreceder = null;
				for (int i = fromMatch.index() - 1; i >= 0; i--) {
					NodeReference pm = getReference(fromMatch.parent().children().get(i));
					if (!removedRefs.contains(pm)) {
						matchPreceder = pm;
						break;
					}
				}

				if (ObjectUtils.equals(precederMatch, matchPreceder)) {
					if (toNode.shallowEquals(fromMatch, false)) {
						displacedFrom.add(getReference(fromMatch));
						displacedTo.add(getReferenceInPair(toNode, mapping));
						return;
					}
				}
			}

			// Ignore no-id nodes which have moved according to the mapping, but for which
			// there is still a node in the original position in the to-tree
			if (toNode.id == null && toRefs.contains(getReference(fromMatch))) {
				return;
			}

			NodeReference fromRef = getReference(fromMatch);
			ChildNodeReference toRef = new ChildNodeReference(toNode,
					getReferenceInPair(toNode.parent(), mapping));
			if (StringUtils.equals(toNode.type, fromMatch.type)) {
				edits.add(new Move(fromRef, toRef));
			} else {
				// For consistency, we represent relabels as insertions + deletions, rather than
				// "moves", since we don't want to represent them differently
				edits.add(new Deletion(fromRef));
				edits.add(new Insertion(toRef));
			}
			movedFrom.add(fromRef);
			movedTo.add(toRef);
		});

		// Reset the removed and added refs, since they were modified above
		removedRefs.clear();
		removedRefs.addAll(fromRefs);
		removedRefs.removeAll(toRefs);
		addedRefs.clear();
		addedRefs.addAll(toRefs);
		addedRefs.removeAll(fromRefs);

		// Moving takes precedence over adding or removing, so remove the refs that were moved
		removedRefs.removeAll(movedFrom);
		addedRefs.removeAll(movedTo);

		// Don't list nodes as removed/added if they have a pair and their index only changed due
		// to modified earlier siblings
		removedRefs.removeAll(displacedFrom);
		addedRefs.removeAll(displacedTo);

		removedRefs.forEach(removedRef -> edits.add(new Deletion(removedRef)));
		addedRefs.forEach(addedRef -> edits.add(new Insertion(addedRef)));

		// Remove any edits to NULL nodes, since these aren't really there to begin with
		return edits.stream()
				.filter(e -> !ASTNode.EMPTY_TYPE.equals(e.node.type))
				.collect(Collectors.toSet());
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

	// TODO: This is problematic because we lose insert order (e.g. +(a, b) == +(b, a)) which
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

	private static NodeReference getReferenceInPair(ASTNode toNode,
			BiMap<ASTNode, ASTNode> mapping) {
		ASTNode fromPair = mapping.getTo(toNode);
		if (fromPair != null && fromPair.id != null) return new IDNodeReference(fromPair);
		if (toNode.parent() == null) {
			if (fromPair != null) return new RootNodeReference(fromPair);
			System.err.println(toNode);
			System.err.println(mapping);
			throw new IllegalArgumentException("To root has no match in mapping!");
		}
		return new ChildNodeReference(toNode, getReferenceInPair(toNode.parent(), mapping));
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
