package edu.isnap.rating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import costmodel.CostModel;
import distance.APTED;
import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.BiMap;
import node.Node;

public class EditExtractor {

	private final Map<ASTNode, Set<Edit>> cache = new IdentityHashMap<>();

	private final boolean useIDs;

	public EditExtractor(boolean useIDs) {
		this.useIDs = useIDs;
	}

	public Set<Edit> getEdits(ASTNode from, ASTNode to) {
		Set<Edit> edits = cache.get(to);
		if (edits == null) {
			edits = useIDs ? extractEditsUsingIDs(from, to) : extractEditsUsingTED(from, to);
			cache.put(to, edits);
		}
		return edits;
	}

	private static CostModel<ASTNode> costModel = new CostModel<ASTNode>() {
		@Override
		public float ren(Node<ASTNode> nodeA, Node<ASTNode> nodeB) {
			if (nodeA.getNodeData().shallowEquals(nodeB.getNodeData(), false)) return 0;
			if (StringUtils.equals(nodeA.getNodeData().id, nodeB.getNodeData().id)) return 0.1f;
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

	private static Node<ASTNode> toNode(ASTNode astNode) {
		Node<ASTNode> node = new Node<>(astNode);
		if (astNode != null) {
			for (ASTNode child : astNode.children()) {
				node.addChild(toNode(child));
			}
		}
		return node;
	}

	private static BiMap<ASTNode, ASTNode> extractMap(Node<ASTNode> from, Node<ASTNode> to) {
		APTED<CostModel<ASTNode>, ASTNode> apted = new APTED<>(costModel);
		apted.computeEditDistance(from, to);
		LinkedList<int[]> editMapping = apted.computeEditMapping();
		List<ASTNode> fromChildren = postOrderList(from.getNodeData(), new ArrayList<>());
		List<ASTNode> toChildren = postOrderList(to.getNodeData(), new ArrayList<>());
		BiMap<ASTNode, ASTNode> mapping = new BiMap<>(IdentityHashMap::new);
		for (int[] pair : editMapping) {
			ASTNode fromPair = readPair(fromChildren, pair[0]);
			ASTNode toPair = readPair(toChildren, pair[1]);
			if (fromPair != null && toPair != null) mapping.put(fromPair, toPair);
		}
		return mapping;
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

	public static void printEditsComparison(Set<Edit> editsA, Set<Edit> editsB,
			String nameA, String nameB) {
		Set<Edit> a = new HashSet<>(editsA),
				b = new HashSet<>(editsB),
				both = new HashSet<>(editsB);
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

	public static Set<Edit> extractEditsUsingTED(ASTNode from, ASTNode to) {
		BiMap<ASTNode, ASTNode> mapping = extractMap(toNode(from), toNode(to));
		return extractEdits(from, to, mapping);
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
				// TODO: See if there's a big difference if we treat moves as insert/delete pairs
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
		// to modified ealier siblings
		removedRefs.removeAll(displacedFrom);
		addedRefs.removeAll(displacedTo);

		removedRefs.forEach(removedRef -> edits.add(new Deletion(removedRef)));
		addedRefs.forEach(addedRef -> edits.add(new Insertion(addedRef)));

		return edits;
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

	protected static abstract class Edit {
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
		final ASTNode node;
		final String type;
		final String value;

		NodeReference(ASTNode node) {
			this.node = node;
			this.type = node.type();
			this.value = node.value();
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

		ChildNodeReference(ASTNode node, NodeReference parent) {
			super(node);
			if (parent == null) throw new IllegalArgumentException("Parent ref cannot be null");
			this.index = node.index();
			this.parent = parent;
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
}
