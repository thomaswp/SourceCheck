package edu.isnap.ctd.hint;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import distance.RTED_InfoTree_Opt;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Tuple;
import util.LblTree;

/**
 * A data-structure that stores all information needed to generate hints for a given assignment.
 */
public class HintMapBuilder {

	public final HintMap hintMap;
	public final double minGrade;

	@SuppressWarnings("unused")
	private HintMapBuilder() {
		this(null, 0);
	}

	public HintMapBuilder(HintMap hintMap, double minGrade) {
		this.hintMap = hintMap;
		this.minGrade = minGrade;
	}

	/**
	 * This should be called to clear the generator's data before adding new data.
	 */
	public void startBuilding() {
		hintMap.clear();
	}

	/**
	 * Adds an attempt map (returned by {@link HintMapBuilder#addAttempt(List, boolean)} to this
	 * generator. This can be useful if you want to cache the output of addAttempt and reconstruct
	 * new generators from a subset of your data.
	 */
	public void addAttemptMap(HintMap hintMap) {
		synchronized (this.hintMap) {
			this.hintMap.addMap(hintMap);
		}
	}

	/**
	 * Call this methods when you are finished adding data to the generator so that it can perform
	 * finalization.
	 */
	public void finishedAdding() {
		hintMap.finish();
	}

	public HintGenerator hintGenerator() {
		return new HintGenerator(hintMap);
	}

	/**
	 * Add data from an assignment attempt to the HintGenerator.
	 * @param solutionPath A list of Nodes, representing Snapshots of the attempt from start to
	 * finish.
	 * @param useIDs If true, the algorithm assumes that most Nodes will return a non-null
	 * values when getID() is called. This is a more effective version of the algorithm; however,
	 * if the supplied data cannot track nodes across snapshots, this value should be false. In this
	 * case, the RTED algorithm will be used to guess which Nodes are the same across snapshots.
	 * @return The individual HintMap for this attempt, which has now been added to the generator.
	 */
	public HintMap addAttempt(List<Node> solutionPath, boolean useIDs) {

		HintMap hintMap = this.hintMap.instance();
		if (solutionPath.size() <= 1) return hintMap;

		if (useIDs) {
			Map<String, Node> lastIDMap = null, cumulativeIDMap =
					new HashMap<>();
			for (Node current : solutionPath) {
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
							// TODO: For scripts, for some reason many states aren't being added
							// until they're marked as goals. This really shouldn't be happening.
							hintMap.addEdge(lastNode, node);

							// We also check for changes in siblings if there's a parent
							if (node.parent != null) {
								// If the node's position has changed (e.g. two nodes swapped,
								// but neither was added or deleted), or if it's gained or lost
								// siblings, we add the parent (possibly redundantly)
								// TODO: test to make sure this isn't overly in/exclusive
								if (lastNode.index() != node.index() ||
										lastNode.parent.children.size() !=
										node.parent.children.size()) {
									hintMap.addEdge(lastNode.parent, node.parent);
								}
							}
						}
					}
				}
				lastIDMap = idMap;
				cumulativeIDMap.putAll(idMap);
			}
		} else {
			addEdgesTED(solutionPath, hintMap);
		}

		Node submission = solutionPath.get(solutionPath.size() - 1);
		hintMap.setSolution(submission);
		addAttemptMap(hintMap);

		return hintMap;
	}

	// Given a node, finds the closest ancestor in pairMap that has a matching ID in
	// nodeMap.
	private Tuple<Node, Node> findClosestMatch(Node node, Map<String, Node> nodeMap,
			Map<String, Node> pairMap) {
		String id = node.id;

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
			if (match.hasType(node.type()) && !nodeMap.containsKey(match.id)) {
				return new Tuple<>(node, match);
			}
		}

		// Otherwise, there's no way to match directly, so we return the ancestor
		return parentMatch;
	}

	private void createMap(Node node, Map<String, Node> byID) {
		String id = node.id;
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


				HashSet<LblTree> addedTrees = new HashSet<>();
				HashSet<LblTree> removedTrees = new HashSet<>();
				HashMap<LblTree, LblTree> sameTrees = new HashMap<>();
				HashMap<LblTree, LblTree> sameTreesRev = new HashMap<>();
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

				HashMap<LblTree, LblTree> toAdd = new HashMap<>();
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
}
