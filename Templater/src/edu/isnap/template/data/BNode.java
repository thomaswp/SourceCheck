package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

import edu.isnap.ctd.graph.Node;

public class BNode {
	public final String type;
	public final boolean inline;
	public final List<BNode> children = new LinkedList<>();
	public int orderGroup;

	public BNode(String type, boolean inline) {
		this.type = type;
		this.inline = inline;
	}

	public Node toNode() {
		if (inline) throw new RuntimeException("Cannot convert inline BNode to Node");
		Node node = new Node(null, type);
		node.orderGroup = orderGroup;
		for (BNode child : children) {
			child.addToParent(node);
		}
		return node;
	}

	private void addToParent(Node parent) {
		if (inline) {
			for (BNode child : children) {
				child.addToParent(parent);
			}
		} else {
			Node node = new Node(parent, type);
			node.orderGroup = orderGroup;
			parent.children.add(node);
			for (BNode child : children) {
				child.addToParent(node);
			}
		}
	}
}
