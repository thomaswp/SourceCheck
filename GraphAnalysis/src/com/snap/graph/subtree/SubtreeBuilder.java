package com.snap.graph.subtree;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import util.LblTree;

import com.snap.graph.data.HintMap;
import com.snap.graph.data.HintMap.HintList;
import com.snap.graph.data.Node;
import com.snap.graph.data.SimpleHintMap;

import distance.RTED_InfoTree_Opt;

public class SubtreeBuilder {

	public final HintMap graph = new SimpleHintMap();
	
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
			
			if (graph.hasVertex(node)) {
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
						if (hasHint(n)) {
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
	
	public List<Hint> getHints(Node parent) {
		LinkedList<Hint> hints = new LinkedList<SubtreeBuilder.Hint>();
		getHints(parent, hints);
		return hints;
	}
	
	private void getHints(Node node, List<Hint> list) {
		HintList edges = graph.getHints(node);
		
		int context = node.size();
		
		if (edges != null) {
			for (Node to : edges) {
				Hint hint = new Hint(node, to);
				hint.context = context;
				hint.quality = edges.getWeight(to);
				list.add(hint);
			}
		}
		
		for (Node child : node.children) getHints(child, list);
	}
	
	
	public boolean hasHint(Node node) {
		List<Node> toSearch = new LinkedList<Node>();
		toSearch.add(node);
		
		while (!toSearch.isEmpty()) {
			Node next = toSearch.remove(0);
			HintList edges = graph.getHints(node);
			Iterator<Node> iterator = edges.iterator();
			if (iterator.hasNext()) return true;
			toSearch.addAll(next.children);
		}
		
		return false;
	}

	public static class Hint extends Tuple<Node, Node> {

		public int relevance, context, quality;
		
		
		public Hint(Node x, Node y) {
			super(x, y);
		}
		
		@Override
		public String toString() {
			return String.format("[%d,%d,%d]: %s -> %s", relevance, context, quality, x.toString(), y.toString());
		}
		
	}
	
	public static abstract class HintComparator implements Comparator<Hint> { 
		public final static HintComparator ByRelevance = new HintComparator() {
			@Override
			public int compare(Hint o1, Hint o2) {
				return Integer.compare(o2.relevance, o1.relevance);
			}
		};
		
		public final static HintComparator ByContext = new HintComparator() {
			@Override
			public int compare(Hint o1, Hint o2) {
				return Integer.compare(o2.context, o1.context);
			}
		};
		
		public final static HintComparator ByQuality = new HintComparator() {
			@Override
			public int compare(Hint o1, Hint o2) {
				return Integer.compare(o2.quality, o1.quality);
			}
		};
		
		public HintComparator then(final HintComparator comparator) {
			return new HintComparator() {
				@Override
				public int compare(Hint o1, Hint o2) {
					int first = HintComparator.this.compare(o1, o2);
					if (first != 0) return first; 
					return comparator.compare(o1, o2);
				}
			};
		}
		
		public static HintComparator compose(HintComparator... comparators) {
			HintComparator base = comparators[0];
			for (int i = 0; i < comparators.length; i++) {
				base = base.then(comparators[i]);
			}
			return base;
		}
		
		public static HintComparator weighted(final double relevance, final double context, final double quality) {
			return new HintComparator() {
				@Override
				public int compare(Hint o1, Hint o2) {
					return Double.compare(
							o2.relevance * relevance + o2.context * context + o2.quality * quality,
							o1.relevance * relevance + o1.context * context + o1.quality * quality);
				}
			};
		}
	}
	
	public static class Tuple<T1,T2> {
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
