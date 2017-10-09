package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableInt;

import edu.isnap.ctd.graph.Node;

public class BNode {
	public final String type;
	public final boolean inline;
	public final List<BNode> children = new LinkedList<>();
	public String contextSnapshot;
	public BNode parent;
	public int orderGroup;
	public boolean anything;

	public BNode(String type, boolean inline, Context context) {
		this(type, inline, context.toString());
	}

	public String deepestContextSnapshot() {
		if (children.size() == 0) return contextSnapshot;
		return children.stream()
				.map(c -> c.deepestContextSnapshot())
				.max((s1, s2) -> Integer.compare(s1.length(), s2.length()))
				.get();
	}

	public BNode(String type, boolean inline, String contextSnapshot) {
		this.type = type;
		this.inline = inline;
		this.contextSnapshot = contextSnapshot;
	}

	private Node createNode(Node parent, MutableInt id) {
		String type = this.type, value = null;
		int colonIndex = type.indexOf(":");
		if (colonIndex >= 0) {
			value = type.substring(colonIndex + 1, type.length());
			type = type.substring(0, colonIndex);
		}
		Node node = new Node(parent, type, value, String.valueOf(id.getAndAdd(1)));
		return node;
	}

	public Node toNode() {
		if (inline) throw new RuntimeException("Cannot convert inline BNode to Node");
		MutableInt id = new MutableInt();
		Node node = createNode(null, id);
		node.setOrderGroup(orderGroup);
		for (BNode child : children) {
			child.addToParent(node, id);
		}
		return node;
	}

	private void addToParent(Node parent, MutableInt id) {
		if (anything) {
			parent.writableAnnotations().matchAnyChildren = true;
		}
		if (inline) {
			for (BNode child : children) {
				if (orderGroup != 0) {
					if (child.orderGroup != 0 && child.orderGroup != orderGroup) {
						System.err.println("Multiple order groups for child: " + child.orderGroup);
					}
					child.orderGroup = orderGroup;
				}
				child.addToParent(parent, id);
			}
		} else {
			Node node = createNode(parent, id);
			node.setOrderGroup(orderGroup);
			parent.children.add(node);
			for (BNode child : children) {
				child.addToParent(node, id);
			}
		}
	}

	@Override
	public String toString() {
		return type + (inline ? "[i]" : "") + ":" + children;
	}

	public BNode copy() {
		BNode copy = new BNode(type, inline, contextSnapshot);
		copy.orderGroup = orderGroup;
		copy.anything = anything;
		for (BNode child : children) {
			copy.children.add(child.copy());
		}
		return copy;
	}
}
