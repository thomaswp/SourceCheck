package com.snap.graph.subtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import util.LblTree;

import com.esotericsoftware.kryo.Kryo;
import com.snap.graph.data.HintMap;
import com.snap.graph.data.HintMap.HintList;
import com.snap.graph.data.Node;
import com.snap.graph.data.SimpleHintMap;
import com.snap.graph.data.StringHashable;

import distance.RTED_InfoTree_Opt;

public class SubtreeBuilder {

	public final HintMap hintMap;
	
	@SuppressWarnings("unused")
	private SubtreeBuilder() {
		this(null);
	}
	
	public SubtreeBuilder(HintMap hintMap) {
		this.hintMap = hintMap;
	}
	
	public void startBuilding() {
		hintMap.clear();
	}
	
	@SuppressWarnings("unchecked")
	public List<List<HintChoice>> addStudent(List<Node> path, boolean duplicates) {
		// duplicates allows multiple edges from the same student
		
		List<List<HintChoice>> generatedHints = new LinkedList<List<HintChoice>>();
		
		if (path.size() <= 1) return generatedHints;
		
		LblTree lastTree = null;
		List<LblTree> lastList = null;
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 10000);
		
		Set<Tuple<Node,Node>> placedEdges = new HashSet<Tuple<Node,Node>>();
		
		Node submitted = path.get(path.size() - 1);
		LblTree submittedTree = submitted.toTree();
		List<LblTree> submittedList = Collections.list(submittedTree.depthFirstEnumeration());
		
		int i = 0;
		Node last = null;
		for (Node current : path) {
			current.cache();
			
			List<HintChoice> hints = new ArrayList<HintChoice>();
			generatedHints.add(hints);
			
			LblTree tree = current.toTree();
			List<LblTree> list = Collections.list(tree.depthFirstEnumeration());
			
//			for (LblTree t : list) {
//				Node node = (Node) t.getUserObject();
//				hintMap.addVertex(node);
//			}
					
			if (lastTree != null) {
				LinkedList<int[]> editMap;
				
				HashSet<LblTree> validNodes = new HashSet<LblTree>();

//				System.out.println(tree);
//				System.out.println(submittedTree);
				opt.init(tree, submittedTree);
				opt.computeOptimalStrategy();
//				editMap = opt.computeEditMapping();
//				for (int[] a : editMap) {
//					if (a[0] == 0) continue;
//					LblTree c1 = a[0] == 0 ? null : list.get(a[0] - 1);
//					LblTree c2 = a[1] == 0 ? null : submittedList.get(a[1] - 1);
//					System.out.println(c1 + " <==> " + c2);
//				}
				double dis = opt.nonNormalizedTreeDist();
//				System.out.println(dis);
				editMap = opt.computeEditMapping();
//				for (int[] a : editMap) {
//					if (a[0] == 0) continue;
//					LblTree c1 = a[0] == 0 ? null : list.get(a[0] - 1);
//					LblTree c2 = a[1] == 0 ? null : submittedList.get(a[1] - 1);
//					System.out.println(c1 + " <==> " + c2);
//				}
				for (int[] a : editMap) {
					if (a[0] == 0 || a[1] == 0) continue;
					LblTree valid = list.get(a[0] - 1);
					validNodes.add(valid);
				}
//				System.out.println(validNodes);
//				System.exit(0);
				
//				String out = validNodes.size() + "/" + list.size();
//				if (path.size() - i < 6) out += ": " + current.toCanonicalString();
//				System.out.println(out);
				
				opt.init(lastTree, tree);
				editMap = opt.computeEditMapping();
				
				HashSet<LblTree> added = new HashSet<LblTree>();
				for (int[] a : editMap) {
					if (a[0] != 0 || a[1] == 0) continue;
					added.add(list.get(a[1] - 1));
				}
				
				int possible = 0, valid = 0;
				for (int[] a : editMap) {
					LblTree c1 = a[0] == 0 ? null : lastList.get(a[0] - 1);
					LblTree c2 = a[1] == 0 ? null : list.get(a[1] - 1);
					
					if (c1 != null && c2 != null) {
						Node n1 = (Node)c1.getUserObject();
						Node n2 = (Node)c2.getUserObject();

						if (!n1.equals(n2) && n1.shallowEquals(n2)) {
							possible++;
														
							HintChoice hint = new HintChoice(n1, n2);
							
							LblTree badAdd = null;
							Enumeration<LblTree> children = c2.depthFirstEnumeration();
							while (children.hasMoreElements()) {
								LblTree child = children.nextElement();
								if (added.contains(child) && !validNodes.contains(child)) {
									badAdd = child;
									break;
								}
							}
							
							if (badAdd != null) {
								hint.setStatus(false, "Not kept: " + badAdd);
								hints.add(hint);
								continue;
							}
							
							
							if (duplicates || placedEdges.add(new Tuple<Node,Node>(n1, n2))) {
								valid++;
								hintMap.addEdge(n1, n2);
								hints.add(hint);
							}
						}
					}
				}
//				System.out.println(valid + "/" + possible);
			}
			
			lastList = list;
			lastTree = tree;
			i++;
		}
		
		return generatedHints;
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
			
			if (hintMap.hasVertex(node)) {
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
		HintList edges = hintMap.getHints(node);
		
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
			HintList edges = hintMap.getHints(node);
			Iterator<Node> iterator = edges.iterator();
			if (iterator.hasNext()) return true;
			toSearch.addAll(next.children);
		}
		
		return false;
	}

	public static class HintChoice extends Tuple<Node, Node> {

		public boolean accepted = true;
		public String status = "Good";
		
		public HintChoice(Node x, Node y) {
			super(x, y);
		}
		
		public void setStatus(boolean accepted, String status) {
			this.accepted = accepted;
			this.status = status;
		}

		public String toJson() {
			return String.format(
					"{\"accepted\": %s, \"status\": \"%s\", \"from\": \"%s\", \"to\": \"%s\"}", 
					(accepted ? "true" : "false"), status, x.toString(), y.toString());
		}
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

		public String toJson() {
			return String.format(
					"{\"relevance\": %d, \"context\": %d, \"quality\": %d, \"from\": \"%s\", \"to\": \"%s\"}", 
					relevance, context, quality, x.toString(), y.toString());
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

	public static Kryo getKryo() {
		Kryo kryo = new Kryo();
		kryo.register(SubtreeBuilder.class);
		kryo.register(StringHashable.class);
		kryo.register(Node.class);
		kryo.register(HintMap.class);
		kryo.register(HintMap.HintList.class);
		kryo.register(SimpleHintMap.class);
		return kryo;
	}
}
