package edu.isnap.template.data;

import java.util.LinkedList;
import java.util.List;

import edu.isnap.ctd.graph.Node;

public class BNode {
	public String type;
	public List<BNode> children = new LinkedList<>();

	public BNode(String type) {
		this.type = type;
	}

	public Node toNode(Node parent) {
		Node node = new Node(parent, type);
		for (BNode child : children) node.children.add(child.toNode(node));
		return node;
	}
}
