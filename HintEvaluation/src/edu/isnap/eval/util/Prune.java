package edu.isnap.eval.util;

import java.util.ArrayList;
import java.util.List;

import edu.isnap.node.Node;
import edu.isnap.node.Node.Action;

public class Prune {

	public static List<Node> removeSmallerScripts(List<Node> nodes) {
		List<Node> list = new ArrayList<>();
		for (Node node : nodes) list.add(removeSmallerScripts(node));
		return list;
	}

	public static Node removeSmallerScripts(Node node) {
		node = node.copy();
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.hasType("stage") || node.hasType("sprite")) {
					int largestSize = 0;
					Node largestChild = null;
					for (int i = 0; i < node.children.size(); i++) {
						Node child = node.children.get(i);
						if (child.hasType("sprite") || child.hasType("script")) {
							int size = child.treeSize();
							if (size > largestSize) {
								largestSize = Math.max(largestSize, child.treeSize());
								largestChild = child;
							}
						}
					}
					for (int i = 0; i < node.children.size(); i++) {
						Node child = node.children.get(i);
						if (child != largestChild && (child.hasType("sprite") || child.hasType("script"))) {
							node.children.remove(i--);
						}
					}
				}
			}
		});
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				node.cache();
			}
		});
		return node;
	}
}
