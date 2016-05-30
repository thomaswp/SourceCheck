package com.snap.graph.subtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import util.LblTree;

import com.esotericsoftware.kryo.Kryo;
import com.snap.graph.Alignment;
import com.snap.graph.data.Graph;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.HintMap;
import com.snap.graph.data.IndexedVectorState;
import com.snap.graph.data.Node;
import com.snap.graph.data.SkeletonMap;
import com.snap.graph.data.StringHashable;
import com.snap.graph.data.VectorGraph;
import com.snap.graph.data.VectorState;

import distance.RTED_InfoTree_Opt;

public class SubtreeBuilder {

	public final HintMap hintMap;
	public final double minGrade;
	
	@SuppressWarnings("unused")
	private SubtreeBuilder() {
		this(null, 0);
	}
	
	public SubtreeBuilder(HintMap hintMap, double minGrade) {
		this.hintMap = hintMap;
		this.minGrade = minGrade;
	}
	
	public void startBuilding() {
		hintMap.clear();
	}
	
	@SuppressWarnings({ "unchecked" })
	public HintMap addStudent(List<Node> path) {

		HintMap hintMap = this.hintMap.instance();
		if (path.size() <= 1) return hintMap;
		
//		List<HashSet<Node>> keptNodes = keptNodes(path);
		
		LblTree lastTree = null;
		List<LblTree> lastList = null;
		RTED_InfoTree_Opt opt = new RTED_InfoTree_Opt(0.01, 0.01, 10000);
		
				
		for (Node current : path) {
			current.cache();
			
			LblTree tree = current.toTree();
			List<LblTree> list = Collections.list(tree.depthFirstEnumeration());
							
			if (lastTree != null) {
				opt.nonNormalizedTreeDist(lastTree, tree);
				LinkedList<int[]> editMap = opt.computeEditMapping();
			
				
				HashSet<LblTree> addedTrees = new HashSet<LblTree>();
				HashSet<LblTree> removedTrees = new HashSet<LblTree>();
				HashMap<LblTree, LblTree> sameTrees = new HashMap<LblTree, LblTree>();
				HashMap<LblTree, LblTree> sameTreesRev = new HashMap<LblTree, LblTree>();
				for (int[] a : editMap) {
					LblTree c1 = a[0] == 0 ? null : lastList.get(a[0] - 1);
					LblTree c2 = a[1] == 0 ? null : list.get(a[1] - 1);
					if (c1 == null && c2 != null) {
						addedTrees.add(c2);
					} else if (c1 != null && c2 == null) {
						removedTrees.add(c1);
					} else {
						sameTrees.put(c2, c1);
						sameTreesRev.put(c1, c2);
					}
				}
				
				HashMap<LblTree, LblTree> toAdd = new HashMap<LblTree, LblTree>();
				for (LblTree t : addedTrees) {
					LblTree parentTree = (LblTree) t.getParent();
					if (parentTree == null) continue;
					LblTree parentPair = sameTrees.get(parentTree);
					if (parentPair == null) continue;
					toAdd.put(parentPair, parentTree);
				}
				for (LblTree t : removedTrees) {
					LblTree parentTree = (LblTree) t.getParent();
					if (parentTree == null) continue;
					LblTree parentPair = sameTreesRev.get(parentTree);
					if (parentPair == null) continue;
					toAdd.put(parentTree, parentPair);
				}
				for (LblTree from : toAdd.keySet()) {
					LblTree to = toAdd.get(from);
					hintMap.addEdge((Node) from.getUserObject(), (Node) to.getUserObject());
				}
			}
			
			lastList = list;
			lastTree = tree;
//			i++;
		}
		
		Node submission = path.get(path.size() - 1);
		hintMap.setSolution(submission);
		addStudentMap(hintMap);
		
		return hintMap;
	}
	
	public void addStudentMap(HintMap hintMap) {
		synchronized (this.hintMap) {
			this.hintMap.addMap(hintMap);
		}
	}
	
	public void finishedAdding() {
		hintMap.finish();
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
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
		
	public synchronized Hint getFirstHint(Node node) {
		LinkedList<Hint> hints = new LinkedList<Hint>();
		getHints(node, hints, 1, 1);
		return hints.size() > 0 ? hints.getFirst() : null;
	}
	
	public synchronized List<Hint> getHints(Node parent) {
		return getHints(parent, 1);
	}
	
	public synchronized List<Hint> getHints(Node parent, int chain) {
		LinkedList<Hint> hints = new LinkedList<Hint>();
		getHints(parent, hints, chain, Integer.MAX_VALUE);
		return hints;
	}
		
	private void getHints(Node node, List<Hint> list, int chain, int limit) {
		if (list.size() >= limit) return;

		Iterable<Hint> edges = hintMap.getHints(node, chain);
		
		// TODO: don't forget that really the skeleton need not match exactly,
		// we should just be matching as much as possible
		
		for (Hint hint : edges) {
			if (list.contains(hint)) continue;	
			if (hint.from().equals(hint.to())) continue;
			list.add(hint);
			if (list.size() >= limit) return;
		}
		
		for (Node child : node.children) getHints(child, list, chain, limit);
	}
	
	public double skeletonDiff(Node x, Node y) {
		return skeletonDiff(x, y, 0.5);
	}
	
	public double skeletonDiff(Node x, Node y, double decay) {
		if (x == null || y == null) return 0;
		if (!x.type().equals(y.type())) {
			return 0;
		}

		String[] xChildren = childrenTypes(x);
		String[] yChildren = childrenTypes(y);
		double d = Alignment.normalizedAlignScore(xChildren, yChildren);
//		System.out.println(d + ": " + Arrays.toString(xChildren) + " v " + Arrays.toString(yChildren));
		d += decay * skeletonDiff(x.parent, y.parent, decay);
		return d;
	}
	
	private String[] childrenTypes(Node node) {
		String[] types = new String[node.children.size()];
		for (int i = 0; i < types.length; i++) types[i] = node.children.get(i).type();
		return types;
	}
	
	public boolean hasHint(Node node) {
		List<Node> toSearch = new LinkedList<Node>();
		toSearch.add(node);
		
		while (!toSearch.isEmpty()) {
			Node next = toSearch.remove(0);
			Iterable<Hint> edges = hintMap.getHints(node, 1);
			Iterator<Hint> iterator = edges.iterator();
			if (iterator.hasNext()) return true;
			toSearch.addAll(next.children);
		}
		
		return false;
	}

	public static class HintChoice extends PairHint {

		public boolean accepted = true;
		public String status = "Good";
		
		public HintChoice(Node x, Node y) {
			super(x, y);
		}
		
		public void setStatus(boolean accepted, String status) {
			this.accepted = accepted;
			this.status = status;
		}
		
		@Override
		public String data() {
			return String.format("{\"accepted\": %s, \"status\": \"%s\"}", 
					(accepted ? "true" : "false"), status);
		}
	}
	
	public static class WeightedHint extends PairHint {

		public int relevance, context;
		public double alignment, ted;
		
		public WeightedHint(Node x, Node y) {
			super(x, y);
		}
		
		@Override
		public String toString() {
			return String.format("[%02d,%02d,%.3f,%.3f]: %s -> %s", relevance, context, alignment, ted, x.toString(), y.toString());
		}
		
		@Override
		public String data() {
			return String.format(
					"{\"relevance\": %d, \"context\": %d, \"alignment\": %.3f}", 
					relevance, context, alignment);
		}
		
	}
	
	public static interface Hint {
		String from();
		String to();
		String data();
		Node outcome();
	}
	
	public static String hintToJson(Hint hint) {
		return String.format("{\"from\": \"%s\", \"to\": \"%s\", \"data\": %s}", hint.from(), hint.to(), hint.data());
	}
	
	public static class PairHint extends Tuple<Node, Node> implements Hint {

		private PairHint() {
			super(null, null);
		}
		
		public PairHint(Node x, Node y) {
			super(x, y);
		}
		
		@Override
		public String toString() {
			return String.format("%s -> %s", x.toString(), y.toString());
		}

		@Override
		public String from() {
			return x.toString();
		}

		@Override
		public String to() {
			return y.toString();
		}

		@Override
		public String data() {
			return "null";
		}

		@Override
		public Node outcome() {
			return y;
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
		
		public final static HintComparator ByTED = new HintComparator() {
			@Override
			public int compare(WeightedHint o1, WeightedHint o2) {
				return Double.compare(o1.ted, o2.ted);
			}
		};
		
		public final static HintComparator ByAlignment = new HintComparator() {
			@Override
			public int compare(WeightedHint o1, WeightedHint o2) {
				return Double.compare(o2.alignment, o1.alignment);
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
							o2.relevance * relevance + o2.context * context + o2.alignment * quality,
							o1.relevance * relevance + o1.context * context + o1.alignment * quality);
				}
			};
		}
	}
	
	public static class Tuple<T1,T2> {
		public T1 x;
		public T2 y;
		
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
				if (y == null) {
					if (tuple.y != null) return false;
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
		
		@Override
		public String toString() {
			return "{" + x + "," + y + "}";
		}
	}

	public static Kryo getKryo() {
		Kryo kryo = new Kryo();
		kryo.register(SubtreeBuilder.class);
		kryo.register(StringHashable.class);
		kryo.register(Node.class);
		kryo.register(HintMap.class);
		kryo.register(PairHint.class);
//		kryo.register(SimpleHintMap.class);
		kryo.register(SkeletonMap.class);
		kryo.register(HintFactoryMap.class);
		kryo.register(VectorState.class);
		kryo.register(IndexedVectorState.class);
		kryo.register(VectorGraph.class);
		kryo.register(Graph.Vertex.class);
		kryo.register(Graph.Edge.class);
		return kryo;
	}
}
