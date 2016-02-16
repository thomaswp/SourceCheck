package com.snap.eval.util;

import com.snap.graph.data.Node;
import com.snap.graph.data.Node.Action;

public class Prune {
	
	public static Node removeSmallerScripts(Node node) {
		node = node.copy(false);
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.hasType("stage") || node.hasType("sprite")) {
					int largestSize = 0;
					Node largestChild = null;
					for (int i = 0; i < node.children.size(); i++) {
						Node child = node.children.get(i);
						if (child.hasType("sprite") || child.hasType("script")) {
							int size = child.size();
							if (size > largestSize) {
								largestSize = Math.max(largestSize, child.size());
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
