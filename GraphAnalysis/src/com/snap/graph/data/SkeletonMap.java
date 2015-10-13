package com.snap.graph.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.snap.graph.subtree.SubtreeBuilder.Tuple;

public class SkeletonMap implements HintMap {
	
	private HashMap<Node, List<Tuple<Node,Node>>> bones = new HashMap<Node, List<Tuple<Node,Node>>>();

	@Override
	public void addEdge(Node from, Node to) {
		Node backbone = toBackbone(from).root();
		List<Tuple<Node, Node>> hints = bones.get(backbone);
		if (hints == null) {
			hints = new ArrayList<Tuple<Node,Node>>();
			bones.put(backbone, hints);
		}
	}
	
	private Node toBackbone(Node node) {
		if (node == null) return null;
		
		Node parent = toBackbone(node.parent);
		Node child = new Node(parent, node.type);
		parent.children.add(child);
		
		return child;
	}

	@Override
	public boolean hasVertex(Node node) {
		return bones.containsKey(toBackbone(node).root());
	}

	@Override
	public HintList getHints(Node node) {
		final List<Tuple<Node, Node>> hints = bones.get(toBackbone(node).root());
		
		return new HintList() {
			@Override
			public Iterator<Node> iterator() {
				final Iterator<Tuple<Node,Node>> i = hints.iterator();
				return new Iterator<Node>() {
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
					
					@Override
					public Node next() {
						return i.next().y;
					}
					
					@Override
					public boolean hasNext() {
						return i.hasNext();
					}
				};
			}
			
			@Override
			public int getWeight(Node to) {
				return 1;
			}
		};
	}

	@Override
	public void clear() {
		bones.clear();
	}

}
