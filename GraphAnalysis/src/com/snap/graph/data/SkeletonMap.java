package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.snap.graph.subtree.SubtreeBuilder.Hint;
import com.snap.graph.subtree.SubtreeBuilder.HintChoice;
import com.snap.graph.subtree.SubtreeBuilder.PairHint;

public class SkeletonMap implements HintMap {
	
	private HashMap<Node, List<Hint>> bones = new HashMap<Node, List<Hint>>();

	@Override
	public HintChoice addEdge(Node from, Node to) {
		Node backbone = toBackbone(from, false).root();
		List<Hint> hints = bones.get(backbone);
		if (hints == null) {
			hints = new ArrayList<Hint>();
			bones.put(backbone, hints);
		}
		hints.add(new PairHint(from, to));
		return new HintChoice(from, to);
	}
	
	public static Node toBackbone(Node node, boolean indices) {
		if (node == null) return null;
		
		Node parent = toBackbone(node.parent, indices);
		String type = node.type;
		if (indices && node.parent != null) {
			int index = 0;
			List<Node> siblings = node.parent.children;
			for (int i = 0; i < siblings.size(); i++) {
				Node sibling = siblings.get(i);
				if (sibling == node) break;
				if (sibling.type.equals(type)) index++;
			}
			type += index;
		}
		Node child = new Node(parent, type);
		if (parent != null) parent.children.add(child);
		
		return child;
	}

	@Override
	public boolean hasVertex(Node node) {
		return bones.containsKey(toBackbone(node, false).root());
	}

	@Override
	public List<Hint> getHints(Node node) {
		final List<Hint> hints = bones.get(toBackbone(node, false).root());
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

	@Override
	public void setSolution(Node solution) {
		
	}

	@Override
	public void finsh() {
		
	}

	@Override
	public void addMap(HintMap hintMap) {
		HashMap<Node,List<Hint>> addBones = ((SkeletonMap) hintMap).bones;
		for (Node backbone : addBones.keySet()) {
			List<Hint> hints = bones.get(backbone);
			if (hints == null) {
				hints = new ArrayList<Hint>();
				bones.put(backbone, hints);
			}
			hints.addAll(addBones.get(backbone));
		}
	}

	@Override
	public void addState(Node node) {
		throw new RuntimeException(); 
	}

}
