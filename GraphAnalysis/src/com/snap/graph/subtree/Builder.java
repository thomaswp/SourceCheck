package com.snap.graph.subtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import util.LblTree;
import distance.RTED_InfoTree_Opt;

public class Builder {

	public final NodeGraph graph = new NodeGraph();
	
	@SuppressWarnings("unchecked")
	public void addStudent(List<Node> path, boolean subtree, boolean duplicates) {
		// duplicates allows multiple edges from the same student
		
		if (path.size() == 0) return;
		
		LblTree lastTree = null;
		List<LblTree> lastList = null;
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 100);
		
		Set<Tuple<Node,Node>> placedEdges = new HashSet<Tuple<Node,Node>>();
		
		Node last = null;
		for (Node current : path) {
			current.cache();
			
			LblTree tree = current.toTree();
			List<LblTree> list = Collections.list(tree.depthFirstEnumeration());
			
			for (LblTree t : list) {
				Node node = (Node) t.getUserObject();
				graph.addVertex(node);
			}
			
			if (!subtree) {
				if (last != null) {
					if (!current.equals(last) && current.shallowEquals(last)) {
						if (duplicates || placedEdges.add(new Tuple<Node,Node>(last, current))) {
							graph.addEdge(last, current);
						}
					}
				}
				last = current;
				continue;
			}
			
			if (lastTree != null) {
				opt.init(lastTree, tree);
				for (int[] a : opt.computeEditMapping()) {
					LblTree c1 = a[0] == 0 ? null : lastList.get(a[0] - 1);
					LblTree c2 = a[1] == 0 ? null : list.get(a[1] - 1);
					
					if (c1 != null && c2 != null) {
						Node n1 = (Node)c1.getUserObject();
						Node n2 = (Node)c2.getUserObject();

						if (!n1.equals(n2) && n1.shallowEquals(n2)) {
							if (duplicates || placedEdges.add(new Tuple<Node,Node>(n1, n2))) {
								graph.addEdge(n1, n2);
							}
						}
					}
				}
			}
			
			lastList = list;
			lastTree = tree;
		}
	}

	@SuppressWarnings("unchecked")
	public float testStudent(List<Node> nodes, boolean subtree) {
		
		Set<Node> set = new HashSet<Node>();
		
		LblTree lastTree = null;
		List<LblTree> lastList = null;
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 100);
		
		int success = 0;
		int size = 0;
		for (int i = 0; i < nodes.size(); i++) {
			Node node = nodes.get(i);
			if (!set.add(node)) continue;
			size++;
			
			if (graph.vertexMap.containsKey(node)) {
				success++;
				continue;
			}
			if (!subtree) continue;

			LblTree tree = node.toTree();
			List<LblTree> list = Collections.list(tree.depthFirstEnumeration());
			if (lastTree != null) {
				opt.init(lastTree, tree);
				for (int[] a : opt.computeEditMapping()) {
					LblTree c1 = a[0] == 0 ? null : lastList.get(a[0] - 1);
					LblTree c2 = a[1] == 0 ? null : list.get(a[1] - 1);
					// We look for newly created blocks (they have no match in the previous state)
					if (c1 == null) {
						Node n = (Node)c2.getUserObject();
						Tuple<Node,Node> hint = getHint(n);
						if (hint != null) {
							System.out.println(i + ":"  + hint.x + "\n\t-> " + hint.y);
							success++;
							break;
						}
					}
				}
			}
			lastTree = tree;
			lastList = list;
		}
		
		float perc = (float)success / size;
		System.out.println(success + "/" + size + " = " + perc );
		
		return perc;
	}
	
	public Tuple<Node,Node> getHint(Node node) {
		List<Graph<Node,Void>.Edge> next = graph.fromMap.get(node);
		if (next != null && next.size() > 0) return new Tuple<Node,Node>(node, next.get(0).to);
		for (Node child : node.children) {
			Tuple<Node,Node> hint = getHint(child); 
			if (hint != null) return hint;
		}
		return null;
	}

	public class Tuple<T1,T2> {
		public final T1 x;
		public final T2 y;
		
		public Tuple(T1 x, T2 y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Tuple<?,?>) {
				Tuple<?,?> tuple = (Tuple<?, ?>) obj;
				if (x == null) { 
					if (tuple.x != null) return false; 
				} else if (!x.equals(tuple.x)) return false;
				if (y == null && tuple.y != null) {
					return false;
				} else if (!y.equals(tuple.y)) return false;
				return true;
			}
			return super.equals(obj);
		}
		
		@Override
		public int hashCode() {
			int hash = 1;
			hash = hash * 31 + (x == null ? 0 : x.hashCode());
			hash = hash * 31 + (y == null ? 0 : y.hashCode());
			return hash;
		}
	}
}
