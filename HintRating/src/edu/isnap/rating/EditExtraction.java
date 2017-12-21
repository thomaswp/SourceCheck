package edu.isnap.rating;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import costmodel.CostModel;
import distance.APTED;
import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.BiMap;
import node.Node;

public class EditExtraction {

	private static CostModel<ASTNode> costModel = new CostModel<ASTNode>() {
		@Override
		public float ren(Node<ASTNode> nodeA, Node<ASTNode> nodeB) {
			return nodeA.getNodeData().shallowEquals(nodeB.getNodeData(), false) ? 0f : 1f;
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
		for (ASTNode child : astNode.children()) {
			node.addChild(toNode(child));
		}
		return node;
	}

	private static BiMap<ASTNode, ASTNode> extractMap(Node<ASTNode> from, Node<ASTNode> to) {
		APTED<CostModel<ASTNode>, ASTNode> apted = new APTED<>(costModel);
		apted.computeEditDistance(from, to);
		Vector<Node<ASTNode>> fromChildren = from.getChildren();
		Vector<Node<ASTNode>> toChildren = to.getChildren();
		LinkedList<int[]> computeEditMapping = apted.computeEditMapping();
		return new BiMap<>();
	}

	public static List<Edit> extractEdits(ASTNode from, ASTNode to) {
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
					throw new RuntimeException("Duplicate ids in node!");
				}
			}
		});
		return map;
	}

	private static List<Edit> extractEdits(ASTNode from, ASTNode to,
			BiMap<ASTNode, ASTNode> mapping) {

		Set<NodeReference> fromRefs = new HashSet<>();
		from.recurse(node -> {
			NodeReference ref = getReferenceFrom(node);
			if (ref != null) fromRefs.add(ref);
		});

		Set<NodeReference> toRefs = new HashSet<>();
		to.recurse(node -> {
			NodeReference ref = getReferenceTo(node, mapping);
			if (ref != null) {
				if (!toRefs.add(ref)) {
					throw new RuntimeException("Duplicate references in node");
				}
			}
		});

		Set<NodeReference> removedRefs = new HashSet<>(fromRefs);
		removedRefs.removeAll(toRefs);

		Set<NodeReference> addedRefs = new HashSet<>(toRefs);
		addedRefs.removeAll(fromRefs);

		List<Edit> edits = new ArrayList<>();
		removedRefs.forEach(removedRef -> edits.add(new Deletion(removedRef)));
		addedRefs.forEach(addedRef -> edits.add(new Insertion(addedRef)));

		// Find all nodes that have been moved to a new parent and mark them as having been removed
		// from their previous location and added to their new location
		Map<ASTNode, Void> moved = new IdentityHashMap<>();
		to.recurse(toNode -> {
			if (toNode.parent() == null) return;
			ASTNode fromMatch = mapping.getTo(toNode);
			if (fromMatch == null) return;
			NodeReference fromParentMatch = getReferenceTo(toNode.parent(), mapping);
			NodeReference fromMatchParent = getReferenceFrom(fromMatch.parent());
			if (ObjectUtils.equals(fromParentMatch, fromMatchParent)) return;
			moved.put(toNode, null);
			addedRefs.add(getReferenceTo(toNode, mapping));
			removedRefs.add(getReferenceFrom(fromMatch));
		});

		to.recurse(toNode -> {
			if (toNode.parent() == null) return;
			ASTNode fromMatch = mapping.getTo(toNode);
			if (fromMatch == null) return;
			if (!moved.containsKey(toNode)) {
				NodeReference precederMatch = null;
				for (int i = toNode.index() - 1; i >= 0; i--) {
					NodeReference pm = getReferenceTo(toNode.parent().children().get(i), mapping);
					if (!addedRefs.contains(pm)) {
						precederMatch = pm;
						break;
					}
				}

				NodeReference matchPreceder = null;
				for (int i = fromMatch.index() - 1; i >= 0; i--) {
					NodeReference pm = getReferenceFrom(fromMatch.parent().children().get(i));
					if (!removedRefs.contains(pm)) {
						matchPreceder = pm;
						break;
					}
				}

				if (ObjectUtils.equals(precederMatch, matchPreceder)) {
					if (toNode.shallowEquals(fromMatch, false)) return;
				}
			}

			NodeReference fromRef = getReferenceFrom(fromMatch);
			ChildNodeReference toRef = new ChildNodeReference(toNode,
					getReferenceTo(toNode.parent(), mapping));
			if (toNode.shallowEquals(fromMatch, false)) {
				edits.add(new Move(fromRef, toRef));
			} else {
				// For consistency, we represent relabels as insertions + deletions, rather than
				// "moves", since we don't want to represent them differently
				edits.add(new Deletion(fromRef));
				edits.add(new Insertion(toRef));
			}
		});

		return edits;
	}

	static NodeReference getReferenceFrom(ASTNode fromNode) {
		if (fromNode.id != null) return new IDNodeReference(fromNode);
		if (fromNode.parent() == null) return null;
		return new ChildNodeReference(fromNode, getReferenceFrom(fromNode.parent()));
	}

	static NodeReference getReferenceTo(ASTNode toNode, BiMap<ASTNode, ASTNode> mapping) {
		ASTNode fromPair = mapping.getTo(toNode);
		if (fromPair != null && fromPair.id != null) return new IDNodeReference(fromPair);
		if (toNode.parent() == null) return null;
		return new ChildNodeReference(toNode, getReferenceTo(toNode.parent(), mapping));
	}

	static abstract class Edit {
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
}
