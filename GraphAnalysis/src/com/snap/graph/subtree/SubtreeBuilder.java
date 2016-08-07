package com.snap.graph.subtree;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.esotericsoftware.kryo.Kryo;
import com.snap.graph.data.Graph;
import com.snap.graph.data.Hint;
import com.snap.graph.data.HintFactoryMap;
import com.snap.graph.data.HintMap;
import com.snap.graph.data.IndexedVectorState;
import com.snap.graph.data.Node;
import com.snap.graph.data.StringHashable;
import com.snap.graph.data.Tuple;
import com.snap.graph.data.VectorGraph;
import com.snap.graph.data.VectorState;

import distance.RTED_InfoTree_Opt;
import util.LblTree;

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

	public HintMap addStudent(List<Node> path, boolean useIDs) {

		HintMap hintMap = this.hintMap.instance();
		if (path.size() <= 1) return hintMap;

		if (useIDs) {
			Map<String, Node> lastIDMap = null, cumulativeIDMap =
					new HashMap<String, Node>();
			for (Node current : path) {
				current.cache();

				// The general goal of this loop is to identify all matching nodes between
				// the current and last AST, and add an edge between them.  We also search
				// for nodes that have been added and deleted (no match) and add edges in
				// the graph for their parents.

				// We add edges directly because duplicates are OK, since only one
				// edge from each student is counted towards the combined interaction networks

				// Map ID strings to Nodes in the current AST
				Map<String, Node> idMap = new HashMap<>();
				createMap(current, idMap);

				// If this isn't the first AST in the sequence...
				if (lastIDMap != null) {

					// Find deleted nodes that were in the last map but not this one
					for (String id : lastIDMap.keySet()) {
						if (!idMap.containsKey(id)) {
							Node lastParent = lastIDMap.get(id).parent;
							Tuple<Node,Node> match = findClosestMatch(
									lastParent, lastIDMap, idMap);
							hintMap.addEdge(match.x, match.y);
						}
					}
					// Find added and maintained nodes in this map
					for (String id : idMap.keySet()) {
						Node node = idMap.get(id);
						Node lastNode = cumulativeIDMap.get(id);
						if (lastNode == null) {
							// If there's no matching node in the cumulativeMap, this is a
							// new node Find a match and add the edge
							Tuple<Node,Node> match = findClosestMatch(node.parent, idMap,
									cumulativeIDMap);
							hintMap.addEdge(match.y, match.x);

							// If we've matched a farther ancestor than the parent, it
							// means the parent is new as well, which is an unusual
							// behavior
							if (match.x != node.parent) {
								// TODO: This can be caused by duplication, but there's
								// clearly another cause, possibly to do with custom
								// blocks.
								// See: 1daddf46-9553-462f-b2b6-35d3c332dbbf.csv
//								if (node.parent.tag instanceof CallBlock) {
//									System.out.printf("%s (%s)\n", node.parent, node.parent.getID());
//									System.out.println(current.prettyPrint());
//									break;
//								}
								// In this case, we also want to add the node's parent as an edge,
								// so node also shows up in the graph
								hintMap.addEdge(null, node.parent);
							}
						} else {
							// If a node exists in both trees, we add an edge. This will
							// likely just be a loop if its children have not changed, but
							// these are filtered out.
							hintMap.addEdge(lastNode, node);

							// First, if its position has changed (e.g. two nodes swapped,
							// but neither was added or deleted)
							// TODO: test to make sure this isn't overly exclusive
							if (node.parent != null && (
									lastNode.index() != node.index())) {
								hintMap.addEdge(lastNode.parent, node.parent);
							}
						}
					}
				}
				lastIDMap = idMap;
				cumulativeIDMap.putAll(idMap);
			}
		} else {
			addEdgesTED(path, hintMap);
		}

		Node submission = path.get(path.size() - 1);
		hintMap.setSolution(submission);
		addStudentMap(hintMap);

		return hintMap;
	}

	// Given a node, finds the closest ancestor in pairMap that has a matching ID in
	// nodeMap.
	private Tuple<Node, Node> findClosestMatch(Node node, Map<String, Node> nodeMap,
			Map<String, Node> pairMap) {
		String id = node.getID();

		// If this node has a match, return it. For valid trees, this is a base-case
		// since the root should always have a match.
		Node match = pairMap.get(id);
		if (match != null) return new Tuple<>(node, match);

		// Otherwise, get the parent's closest matching ancestor
		Node parent = node.parent;
		Tuple<Node, Node> parentMatch = findClosestMatch(parent, nodeMap, pairMap);

		// We also look through that ancestor's children to see if there's an
		// unmatched node with the same type as node, and return it if so
		int index = node.index();
		List<Node> children = parentMatch.y.children;
		if (index < children.size()) {
			match = children.get(index);
			if (match.hasType(node.type()) && !nodeMap.containsKey(match.getID())) {
				return new Tuple<>(node, match);
			}
		}

		// Otherwise, there's no way to match directly, so we return the ancestor
		return parentMatch;
	}

	private void createMap(Node node, Map<String, Node> byID) {
		String id = node.getID();
		if (id != null) {
			if (byID.put(id, node) != null) {
				System.err.println("Multiple nodes with ID: " + node + " (" + id + ")");
			}
		}
		for (Node child : node.children) createMap(child, byID);
	}

	@SuppressWarnings({ "unchecked" })
	private void addEdgesTED(List<Node> path, HintMap hintMap) {
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
					hintMap.addEdge((Node) from.getUserObject(),
							(Node) to.getUserObject());
				}
			}

			lastList = list;
			lastTree = tree;
//			i++;
		}
	}

	public void addStudentMap(HintMap hintMap) {
		synchronized (this.hintMap) {
			this.hintMap.addMap(hintMap);
		}
	}

	public void finishedAdding() {
		hintMap.finish();
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
		hintMap.postProcess(hints);
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

	public static Kryo getKryo() {
		Kryo kryo = new Kryo();
		kryo.register(SubtreeBuilder.class);
		kryo.register(StringHashable.class);
		kryo.register(Node.class);
		kryo.register(HintMap.class);
		kryo.register(HintFactoryMap.class);
		kryo.register(VectorState.class);
		kryo.register(IndexedVectorState.class);
		kryo.register(VectorGraph.class);
		kryo.register(Graph.Vertex.class);
		kryo.register(Graph.Edge.class);
		return kryo;
	}
}
