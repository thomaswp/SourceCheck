package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

import edu.isnap.ctd.graph.Node;

public class BNode {
	public final String type;
	public final boolean inline;
	public final List<BNode> children = new LinkedList<>();
	public int orderGroup;
	public boolean anything;

	public BNode(String type, boolean inline) {
		this.type = type;
		this.inline = inline;
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
		parent.writableAnnotations().matchAnyChildren = anything;
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
}
