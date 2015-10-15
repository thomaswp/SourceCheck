package com.snap.graph.subtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.plaf.synth.SynthScrollBarUI;

import util.LblTree;

import com.esotericsoftware.kryo.Kryo;
import com.snap.graph.data.HintMap;
import com.snap.graph.data.Node;
import com.snap.graph.data.SimpleHintMap;
import com.snap.graph.data.SkeletonMap;
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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<List<HintChoice>> addStudent(List<Node> path, boolean duplicates) {
		// duplicates allows multiple edges from the same student
		
		List<List<HintChoice>> generatedHints = new LinkedList<List<HintChoice>>();
		
		if (path.size() <= 1) return generatedHints;
		
		List<HashSet<Node>> keptNodes = keptNodes(path);
		
		LblTree lastTree = null;
		List<LblTree> lastList = null;
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(0.01, 0.01, 10000);
		
		Set<Tuple<Node,Node>> placedEdges = new HashSet<Tuple<Node,Node>>();
				
		int i = 0;
		for (Node current : path) {
			current.cache();
			
			List<HintChoice> hints = new ArrayList<HintChoice>();
			generatedHints.add(hints);
			
			LblTree tree = current.toTree();
			List<LblTree> list = Collections.list(tree.depthFirstEnumeration());
							
			if (lastTree != null) {
				opt.nonNormalizedTreeDist(lastTree, tree);
				LinkedList<int[]> editMap = opt.computeEditMapping();
				
				HashSet<LblTree> addedTrees = new HashSet<LblTree>();
				HashSet<LblTree> removedTrees = new HashSet<LblTree>();
				HashMap<LblTree, LblTree> sameTrees = new HashMap<LblTree, LblTree>();
				for (int[] a : editMap) {
					LblTree c1 = a[0] == 0 ? null : lastList.get(a[0] - 1);
					LblTree c2 = a[1] == 0 ? null : list.get(a[1] - 1);
					if (c1 == null && c2 != null) {
						addedTrees.add(c2);
					} else if (c1 != null && c2 == null) {
						removedTrees.add(c1);
					} else {
						sameTrees.put(c2, c1);
					}
				}
				for (LblTree t : addedTrees) {
					LblTree parentTree = (LblTree) t.getParent();
					if (addedTrees.contains(parentTree)) continue;
					
					Node added = (Node) t.getUserObject();
					Node parent = (Node) parentTree.getUserObject();
					LblTree previousParentTree = sameTrees.get(parentTree);
					Node previousParent = (Node) previousParentTree.getUserObject();
					Node previous = null;
					
					int index = parent.children.indexOf(added);
					int minDis = Integer.MAX_VALUE;
					int childIndex = 0;
					for (Enumeration children = previousParentTree.children(); children.hasMoreElements(); childIndex++) {
						LblTree child = (LblTree) children.nextElement();
						if (removedTrees.contains(child)) {
							int d = Math.abs(index - childIndex);
							if (d < minDis) {
								previous = (Node) child.getUserObject();
								minDis = d;
							}
						}
					}
					if (previous == null) previous = new Node(previousParent, null);
					
					synchronized (hintMap) {
						hints.add(hintMap.addEdge(previous, added));
					}
				}
				
//				int possible = 0, valid = 0;
//				for (int[] a : editMap) {
//					LblTree c1 = a[0] == 0 ? null : lastList.get(a[0] - 1);
//					LblTree c2 = a[1] == 0 ? null : list.get(a[1] - 1);
//					
//					if (c1 != null && c2 != null) {
//						Node n1 = (Node)c1.getUserObject();
//						Node n2 = (Node)c2.getUserObject();
//
//						if (!n1.equals(n2) && n1.shallowEquals(n2)) {
//							possible++;
//														
//							HintChoice hint = new HintChoice(n1, n2);
//							if (added.size() == 0) {
//								hint.setStatus(false, "Deletion");
//								hints.add(hint);
//								continue;
//							}
//							
//							LblTree badAdd = null;
//							Enumeration<LblTree> children = c2.depthFirstEnumeration();
//							HashSet<Node> kept = keptNodes.get(i);
//							while (children.hasMoreElements()) {
//								LblTree child = children.nextElement();
//								if (added.contains(child) && !kept.contains(child.getUserObject())) {
//									badAdd = child;
//									break;
//								}
//							}
//							
//							if (badAdd != null) {
//								hint.setStatus(false, "Not kept: " + ((Node) badAdd.getUserObject()).type);
//								hints.add(hint);
//								continue;
//							}
//							
//							
//							if (duplicates || placedEdges.add(new Tuple<Node,Node>(n1, n2))) {
//								valid++;
//								hintMap.addEdge(n1, n2);
//								hints.add(hint);
//							}
//						}
//					}
//				}
//				System.out.println(valid + "/" + possible);
			}
			
			lastList = list;
			lastTree = tree;
			i++;
		}
		
		return generatedHints;
	}
	
	@SuppressWarnings("unchecked")
	private List<HashSet<Node>> keptNodes(List<Node> path) {
		List<HashSet<Node>> keptList = new ArrayList<HashSet<Node>>();
		if (path.size() == 0) return keptList;
		
		for (int i = 0; i < path.size(); i++) keptList.add(new HashSet<Node>());

		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(1, 1, 10000);
		
		LblTree lastTree = path.get(path.size() - 1).toTree();
		
		Node next = null;
		LblTree nextTree = null;
		List<LblTree> nextList = null;
		for (int i = path.size() - 1; i >= 0; i--) {
			Node node = path.get(i);
			LblTree tree = node.toTree();
			List<LblTree> list = Collections.list(tree.depthFirstEnumeration());
			
			HashSet<Node> kept = keptList.get(i);
			
			if (next != null) {
				HashSet<Node> nextKept = keptList.get(i + 1); 
				
//				HashSet<LblTree> added = new HashSet<LblTree>();
				
				opt.init(tree, nextTree);
				opt.computeOptimalStrategy();
				opt.nonNormalizedTreeDist();
//				int keptCount = 0;
				LinkedList<int[]> editMapping = opt.computeEditMapping();
				for (int[] a : editMapping) {
					if (a[0] != 0 && a[1] != 0) {
						Node saved = (Node) nextList.get(a[1] - 1).getUserObject();
						if (nextKept.contains(saved)) {
							LblTree addedTree = list.get(a[0] - 1);
//							added.add(addedTree);
							kept.add((Node) addedTree.getUserObject());
//							keptCount++;
						}
					}
				}
				
				opt.init(tree, lastTree);
				opt.computeOptimalStrategy();
				opt.nonNormalizedTreeDist();
				editMapping = opt.computeEditMapping();
				for (int[] a : editMapping) {
					if (a[0] != 0 && a[1] != 0) {
						LblTree addedTree = list.get(a[0] - 1);
//						if (added.add(addedTree)) {
							kept.add((Node) addedTree.getUserObject());
//							keptCount++;
//						}
					}
				}	
				
//				System.out.println(i + ": " + keptCount + "/" + list.size());
			} else {
				for (LblTree t : list) {
					kept.add((Node) t.getUserObject());
				}
			}
			
			next = node;
			nextTree = tree;
			nextList = list;
		}
		return keptList;
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
	
	public List<WeightedHint> getHints(Node parent) {
		LinkedList<WeightedHint> hints = new LinkedList<SubtreeBuilder.WeightedHint>();
		getHints(parent, hints);
		return hints;
	}
	
	private void getHints(Node node, List<WeightedHint> list) {
		Iterable<Hint> edges = hintMap.getHints(node);
		
		int context = node.depth();
		
		// TODO: don't forget that really the skeleton need not match exactly,
		// we should just be matching as much as possible
		
		if (edges != null) {
			for (Hint h : edges) {
				WeightedHint hint = new WeightedHint(h.x, h.y);
				
				hint.quality = skeletonDiff(node.parent, h.x.parent);
				
				hint.context = context;
				list.add(hint);
			}
		}
		
		if (node.type != null) getHints(new Node(node, null), list);
		for (Node child : node.children) getHints(child, list);
	}
	
	public int skeletonDiff(Node x, Node y) {
		if (x == null || y == null) return 0;
		if (!x.type.equals(y.type)) {
			return 0;
		}

		String[] xChildren = childrenTypes(x);
		String[] yChildren = childrenTypes(y);
		int d = alignCost(xChildren, yChildren);
		System.out.println(d + ": " + Arrays.toString(xChildren) + " v " + Arrays.toString(yChildren));
		d += skeletonDiff(x.parent, y.parent);
		return d;
	}
	
	private String[] childrenTypes(Node node) {
		String[] types = new String[node.children.size()];
		for (int i = 0; i < types.length; i++) types[i] = node.children.get(i).type;
		return types;
	}
	
	// Credit: http://introcs.cs.princeton.edu/java/96optimization/Diff.java.html
	private int alignCost(String[] sequenceA, String[] sequenceB) {
		// The penalties to apply
		int gap = 1, substitution = 1, match = 0;

		int[][] opt = new int[sequenceA.length + 1][sequenceB.length + 1];

		// First of all, compute insertions and deletions at 1st row/column
		for (int i = 1; i <= sequenceA.length; i++)
		    opt[i][0] = opt[i - 1][0] + gap;
		for (int j = 1; j <= sequenceB.length; j++)
		    opt[0][j] = opt[0][j - 1] + gap;

		for (int i = 1; i <= sequenceA.length; i++) {
		    for (int j = 1; j <= sequenceB.length; j++) {
		        int scoreDiag = opt[i - 1][j - 1] +
		                (sequenceA[i-1].equals(sequenceB[j-1]) ?
		                    match : // same symbol
		                    substitution); // different symbol
		        int scoreLeft = opt[i][j - 1] + gap; // insertion
		        int scoreUp = opt[i - 1][j] + gap; // deletion
		        // we take the minimum
		        opt[i][j] = Math.min(Math.min(scoreDiag, scoreLeft), scoreUp);
		    }
		}
		return opt[sequenceA.length][sequenceB.length];
	}
	
	
	public boolean hasHint(Node node) {
		List<Node> toSearch = new LinkedList<Node>();
		toSearch.add(node);
		
		while (!toSearch.isEmpty()) {
			Node next = toSearch.remove(0);
			Iterable<Hint> edges = hintMap.getHints(node);
			Iterator<Hint> iterator = edges.iterator();
			if (iterator.hasNext()) return true;
			toSearch.addAll(next.children);
		}
		
		return false;
	}

	public static class HintChoice extends Hint {

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
	
	public static class WeightedHint extends Hint {

		public int relevance, context, quality;
		
		public WeightedHint(Node x, Node y) {
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
	
	public static class Hint extends Tuple<Node, Node> {

		private Hint() {
			super(null, null);
		}
		
		public Hint(Node x, Node y) {
			super(x, y);
		}
		
		@Override
		public String toString() {
			return String.format("%s -> %s", x.toString(), y.toString());
		}
	}
	
	public static abstract class HintComparator implements Comparator<WeightedHint> { 
		public final static HintComparator ByRelevance = new HintComparator() {
			@Override
			public int compare(WeightedHint o1, WeightedHint o2) {
				return Integer.compare(o2.relevance, o1.relevance);
			}
		};
		
		public final static HintComparator ByContext = new HintComparator() {
			@Override
			public int compare(WeightedHint o1, WeightedHint o2) {
				return Integer.compare(o2.context, o1.context);
			}
		};
		
		public final static HintComparator ByQuality = new HintComparator() {
			@Override
			public int compare(WeightedHint o1, WeightedHint o2) {
				return Integer.compare(o2.quality, o1.quality);
			}
		};
		
		public HintComparator then(final HintComparator comparator) {
			return new HintComparator() {
				@Override
				public int compare(WeightedHint o1, WeightedHint o2) {
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
				public int compare(WeightedHint o1, WeightedHint o2) {
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
		kryo.register(Hint.class);
		kryo.register(SimpleHintMap.class);
		kryo.register(SkeletonMap.class);
		return kryo;
	}
}
