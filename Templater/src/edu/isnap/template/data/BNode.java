package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

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

	public Node toNode() {
		if (inline) throw new RuntimeException("Cannot convert inline BNode to Node");
		Node node = new Node(null, type);
		node.setOrderGroup(orderGroup);
		for (BNode child : children) {
			child.addToParent(node);
		}
		return node;
	}

	private void addToParent(Node parent) {
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
				child.addToParent(parent);
			}
		} else {
			Node node = new Node(parent, type);
			node.setOrderGroup(orderGroup);
			parent.children.add(node);
			for (BNode child : children) {
				child.addToParent(node);
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
