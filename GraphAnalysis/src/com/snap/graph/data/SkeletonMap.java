package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;

public class SkeletonMap implements HintMap {
	
	private HashMap<Node, List<Hint>> bones = new HashMap<Node, List<Hint>>();

	@Override
	public HintChoice addEdge(Node from, Node to) {
		Node backbone = toBackbone(from).root();
		List<Hint> hints = bones.get(backbone);
		if (hints == null) {
			hints = new ArrayList<Hint>();
			bones.put(backbone, hints);
		}
		return new HintChoice(from, to);
	}
	
	private Node toBackbone(Node node) {
		if (node == null) return null;
		
		Node parent = toBackbone(node.parent);
		Node child = new Node(parent, node.type);
		if (parent != null) parent.children.add(child);
		
		return child;
	}

	@Override
	public boolean hasVertex(Node node) {
		return bones.containsKey(toBackbone(node).root());
	}

	@Override
	public List<Hint> getHints(Node node) {
		final List<Hint> hints = bones.get(toBackbone(node).root());
		return hints;
	}

	@Override
	public void clear() {
		bones.clear();
	}

	@Override
	public HintMap instance() {
		return new SkeletonMap();
	}

}
