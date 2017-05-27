package edu.isnap.ctd.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.ctd.util.map.MapFactory;

public class NodeAlignment {

	public final Node from, to;

	public final BiMap<Node, Node> mapping = new BiMap<>(MapFactory.IdentityHashMapFactory);

	private double cost;

	private final boolean useSubCost;

	public NodeAlignment(Node from, Node to) {
		this(from, to, true);
	}

	private NodeAlignment(Node from, Node to, boolean useSubCost) {
		this.from = from;
		this.to = to;
		this.useSubCost = useSubCost;
	}

	public double calculateCost(DistanceMeasure distanceMeasure) {
		cost = 0;

		to.resetAnnotations();
		ListMap<String, Node> fromMap = getChildMap(from);
		ListMap<String, Node> toMap = getChildMap(to);

		mapping.clear();

		for (String key : fromMap.keySet()) {
			List<Node> fromNodes = fromMap.get(key);
			List<Node> toNodes = toMap.get(key);

			if (toNodes == null) {
				// TODO: We used to set these to null - was there a good reason?
				// I don't think the mapping can actually have a value for any of these nodes yet
				// except when a node has children in from and not to, in which case it shouldn't
				// be unmatched
				for (Node node : fromNodes) {
					Node last = mapping.getFrom(node);
					if (last != null) {
						System.err.println("NodeAlignment.calculateCost() - unexpected result");
					}
				}
				continue;
			}

			// Containers are nodes that shouldn't have their descendants matched with those of
			// other containers e.g. Sprites, so we make sure only descendants of a given container
			// are aligned
			// TODO: Why do String IDs work so much faster than IDHashMap
			ListMap<String, Node> fromContainers = new ListMap<>();
			ListMap<String, Node> toContainers = new ListMap<>();
			for (Node from : fromNodes) {
				Node container = getContainer(distanceMeasure, from);
				fromContainers.add(container == null ? null : container.id, from);
			}
			for (Node to : toNodes) {
				Node container = mapping.getTo(getContainer(distanceMeasure, to));
				toContainers.add(container == null ? null : container.id, to);
			}

			for (String containerID : fromContainers.keySet()) {
				List<Node> containedFrom = fromContainers.get(containerID);
				List<Node> containedTo = toContainers.get(containerID);
				if (containedTo == null) continue;
				align(containedFrom, containedTo, distanceMeasure, fromMap);
			}
		}

		return cost;
	}

	private static Node getContainer(DistanceMeasure distanceMeasure, Node from) {
		Node parent = from.parent;
		while (parent != null && parent.parent != null &&
				!distanceMeasure.isContainer(parent.type())) {
			parent = parent.parent;
		}
		return parent;
	}

	public interface DistanceMeasure {
		public double measure(String type, String[] a, String[] b, int[] bOrderGroups);
		public boolean isContainer(String type);
		public double matchedOrphanReward(String type);
	}

	public static class ProgressDistanceMeasure implements DistanceMeasure {

		public final int inOrderReward, outOfOrderReward;
		public final double missingCost;
		public final HintConfig config;

		public ProgressDistanceMeasure(HintConfig config) {
			this.inOrderReward = config.progressOrderFactor;
			this.missingCost = config.progressMissingFactor;
			this.outOfOrderReward = 1;
			this.config = config;
		}

		@Override
		public double measure(String type, String[] a, String[] b, int[] bOrderGroups) {
			if (type == null || config.script.equals(type)) {
				return Alignment.getMissingNodeCount(a, b) * missingCost -
						Alignment.getProgress(a, b, bOrderGroups,
								// TODO: skip cost should maybe be another value?
								inOrderReward, outOfOrderReward, missingCost);
			} else {
				// TODO: this is a little snap-specific, so perhaps modify later
				// TODO: support order groups in parameters
				int cost = 0;
				for (int i = 0; i < a.length && i < b.length; i++) {
					if (!a[i].equals(config.literal) && a[i].equals(b[i])) cost -= inOrderReward;
				}
				return cost;
			}
		}

		@Override
		public double matchedOrphanReward(String type) {
			if (config.literal.equals(type)) return 0;
			// A matched orphan node should be equivalent to the node being out of order, and we
			// also have to counteract the cost of the node being missing from its original parent
			return outOfOrderReward + missingCost;
		}

		@Override
		public boolean isContainer(String type) {
			return config.containers.contains(type);
		}
	};

	private void align(List<Node> fromNodes, List<Node> toNodes,
			DistanceMeasure distanceMeasure, final ListMap<String, Node> fromMap) {
		String[][] fromStates = stateArray(fromNodes);
		String[][] toStates = stateArray(toNodes);
		int[][] toOrderGroups = orderGroups(toNodes);

		String type = fromNodes.get(0).type();

		double minCost = Integer.MAX_VALUE;
		double[][] costMatrix = new double[fromStates.length][toStates.length];
		CountMap<Double> costCounts = new CountMap<>();
		for (int i = 0; i < fromStates.length; i++) {
			for (int j = 0; j < toStates.length; j++) {
				double cost = distanceMeasure.measure(type, fromStates[i], toStates[j],
						toOrderGroups[j]);
				// If the to node can match anything, matching has 0 cost
				if (toNodes.get(j).readOnlyAnnotations().matchAnyChildren) cost = 0;
				costCounts.change(cost, 1);
				costMatrix[i][j] = cost;
				minCost = Math.min(minCost, cost);
			}
		}
		for (int i = 0; i < fromStates.length; i++) {
			for (int j = 0; j < toStates.length; j++) {
				double cost = costMatrix[i][j];
				// Only break ties for entries
				if (costCounts.get(cost) <= 1) continue;
				// For any matches, break ties based on the existing mappings
				if (toNodes.get(j).readOnlyAnnotations().matchAnyChildren) {
					if (mapping.getFrom(fromNodes.get(i)) == toNodes.get(j)) cost -= 1;
					costMatrix[i][j] = cost;
					continue;
				}
				if (useSubCost) {
					// Break ties for 0-cost matches with the cost of their children
					// This is useful for dealing program with similar structures that differ
					// further down the tree
//					double subCost = new NodeAlignment(fromNodes.get(i), toNodes.get(j), false)
//							.calculateCost(distanceMeasure);
					// Instead of using the exact cost, we use an estimated cost based on a depth-
					// first traversal of the children, which is usually a darn good estimate
					double subCost = getSubCostEsitmate(fromNodes.get(i), toNodes.get(j),
							distanceMeasure);
					cost += subCost * 0.001;
				}
				// Break further ties with existing mappings from parents
				if (mapping.getFrom(fromNodes.get(i)) == toNodes.get(j)) cost -= 0.0001;
				costMatrix[i][j] = cost;
				minCost = Math.min(minCost, cost);
			}
		}

		// Ensure all costs are non-negative
		for (int i = 0; i < fromStates.length; i++) {
			for (int j = 0; j < toStates.length; j++) {
				costMatrix[i][j] = costMatrix[i][j] - minCost;
			}
		}

		HungarianAlgorithm alg = new HungarianAlgorithm(costMatrix);
		int[] matching = alg.execute();
		Set<Integer> matchedTo = new HashSet<>();

		// We pre-compute whether each of the from and to nodes have been previous put in the
		// mapping. We do this beforehand because adding contradictory matches may remove some
		// nodes from the mapping temporarily until they are repaired.
		boolean[] mappedFrom = new boolean[fromNodes.size()];
		for (int i = 0; i < mappedFrom.length; i++) {
			mappedFrom[i] = mapping.containsFrom(fromNodes.get(i));
		}
		boolean[] mappedTo = new boolean[toNodes.size()];
		for (int i = 0; i < mappedTo.length; i++) {
			mappedTo[i] = mapping.containsTo(toNodes.get(i));
		}

		for (int i = 0; i < fromStates.length; i++) {
			int j = matching[i];
			matchedTo.add(j);
			// TODO: why don't we penalize unmatched nodes in fromStates?
			// Especially when we do penalize unmatched nodes in toStates below...
			// Currently it works because out "cost" function is actually a reward function, so
			// failing to match a from state loses you a reward. This wouldn't work if cost was
			// generally more than 0. However, we probably shouldn't simply add a penalty, as this
			// would double-penalize unmatched from states
			if (j == -1) continue;

			final Node from = fromNodes.get(i), to = toNodes.get(j);

			// If to's children match anything, edit them to match the children of from
			if (to.readOnlyAnnotations().matchAnyChildren) {
				mapping.put(from, to);
				to.children.clear();
				from.recurse(new Action() {
					@Override
					public void run(Node node) {
						if (node == from) return;
						// Make a copy of the node and match them together
						Node parent = mapping.getFrom(node.parent);
						Node copy = node.shallowCopy(parent);
						parent.children.add(copy);
						mapping.put(node, copy);

						// Remove the from-child from the fromMap, so it doesn't get matched to
						// other things later on
						List<Node> list = fromMap.get(parentNodeKey(node));
						if (list != null) {
							for (int i = 0; i < list.size(); i++) {
								if (list.get(i) == node) {
									list.remove(i);
									break;
								}
							}
						}
					}
				});

				// Then we're done, so continue
				continue;
			}

			// Recalculate the distance to remove tie-breaking costs
			double matchCost = distanceMeasure.measure(type, fromStates[i],
					toStates[j], toOrderGroups[j]);
			cost += matchCost;

			// If we are pairing nodes that have not been paired from their parents, there should
			// be some reward for this, determined by the distance measure
			if (!mappedFrom[i] && !mappedTo[j]) {
				cost -= distanceMeasure.matchedOrphanReward(type);
			}
			mapping.put(from, to);

			// Get any reordering of the to states that needs to be done and see if anything is
			// out of order
			// TODO: This doesn't catch reorders that will be needed for child nodes
			// We do have a failsafe in HintHighlighter, but this would be better fixed
			// TODO: This does not work well for arguments, which cannot be deleted.
			// So [A, B] and [B, C] won't reorder, since a is assumed to be deleted.
			int[] reorders = Alignment.reorderIndices(fromStates[i], toStates[j], toOrderGroups[j]);
			boolean needsReorder = needsReorder(reorders);
			if (needsReorder) {

				// If so, re-add the children of to in the correct order
				List<Node> reordered = new LinkedList<>();
				for (int k = 0; k < reorders.length; k++) {
					reordered.add(null);
				}
				for (int k = 0; k < reorders.length; k++) {
					reordered.set(reorders[k], to.children.get(k));
				}

				// Sanity check
				Arrays.sort(reorders);
				if (reorders.length != to.children.size() || needsReorder(reorders)) {
					// For debugging
					Alignment.reorderIndices(fromStates[i], toStates[j], toOrderGroups[j]);
					throw new RuntimeException("Invalid reorder indices: " +
							Arrays.toString(reorders));
				}

				to.children.clear();
				to.children.addAll(reordered);
				toStates[j] = to.getChildArray();
			}

			// Try to align the children of these paired nodes and add them to the mapping,
			// but don't worry about score, since that's accounted for in the progress score
			// These mappings may be overwritten later if the nodes themselves are matched as
			// parents, but this is not a wholly consistent alignment algorithm
			// We use 2/2/3 here to use replacements only if necessary (these are not returned as
			// pairs if they do not match)
			List<int[]> childPairs = Alignment.alignPairs(fromStates[i], toStates[j], 2, 2, 3);
			for (int[] pair : childPairs) {
				if (pair[0] >= 0 && pair[1] >= 0) {
					mapping.put(from.children.get(pair[0]), to.children.get(pair[1]));
				}
			}
		}

		// For each unmatched toState (in the proposed solution), add its cost as well
		// This essentially penalizes unused nodes that have a matching root path in the student's
		// current solution, but not their children
		for (int i = 0; i < toStates.length; i++) {
			if (matchedTo.contains(i)) continue;
			cost += distanceMeasure.measure(
					toNodes.get(i).type(), new String[0], toStates[i], null);
		}
	}

	private boolean needsReorder(int[] reorders) {
		boolean needsReorder = false;
		for (int k = 0; k < reorders.length; k++) {
			if (reorders[k] != k) {
				needsReorder = true;
				break;
			}
		}
		return needsReorder;
	}

	public static double getSubCostEsitmate(Node a, Node b, DistanceMeasure dm) {
		String[] aDFI = a.depthFirstIteration();
		String[] bDFI = b.depthFirstIteration();
		return dm.measure(null, aDFI, bDFI, null);
	}

	private String[][] stateArray(List<Node> nodes) {
		String[][] states = new String[nodes.size()][];
		for (int i = 0; i < states.length; i++) {
			states[i] = nodes.get(i).getChildArray();
		}
		return states;
	}

	private int[][] orderGroups(List<Node> nodes) {
		int[][] orderGroups = new int[nodes.size()][];
		for (int i = 0; i < orderGroups.length; i++) {
			Node node = nodes.get(i);
			int[] orders = new int[node.children.size()];
			for (int j = 0; j < orders.length; j++) {
				orders[j] = node.children.get(j).readOnlyAnnotations().orderGroup;
			}
			orderGroups[i] = orders;
		}
		return orderGroups;
	}

	private ListMap<String, Node> getChildMap(Node node) {
		final ListMap<String, Node> map = new ListMap<>(MapFactory.LinkedHashMapFactory);
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.children.isEmpty() && node.parent != null &&
						!node.readOnlyAnnotations().matchAnyChildren) {
					return;
				}
				String key = parentNodeKey(node);
				map.add(key, node);
			}
		});
		return map;
	}

	private String parentNodeKey(Node node) {
		return HintMap.toRootPath(node).root().toCanonicalString();
	}

	public static List<Node> findBestMatches(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure) {
		List<Node> best = new LinkedList<>();
		double bestCost = Double.MAX_VALUE;
		for (Node to : matches) {
			NodeAlignment align = new NodeAlignment(from, to);
			double cost = align.calculateCost(distanceMeasure);
			if (cost < bestCost) {
				best.clear();
			}
			if (cost <= bestCost) {
				bestCost = cost;
				best.add(to);
			}
		}
		return best;
	}

	public static Node findBestMatch(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure) {
		Node best = null;
		int smallest = Integer.MAX_VALUE;
		List<Node> bestMatches = findBestMatches(from, matches, distanceMeasure);
//		System.out.println("Size: " + bestMatches.size());
		for (Node node : bestMatches) {
			int size = node.treeSize();
			if (size < smallest) {
				best = node;
				smallest = size;
			}
		}
		return best;
	}

	public static void main1(String[] args) {
		Node n1 = new Node(null, "script");
		n1.children.add(new Node (n1, "a"));
		n1.children.add(new Node (n1, "b"));
		n1.children.add(new Node (n1, "c"));
		n1.children.add(new Node (n1, "d"));
		n1.children.add(new Node (n1, "e"));
		n1.children.add(new Node (n1, "f"));

		Node n2 = new Node(null, "script");
		n2.children.add(new Node (n2, "b").setOrderGroup(1));
		n2.children.add(new Node (n2, "a").setOrderGroup(1));
		n2.children.add(new Node (n2, "c"));
		n2.children.add(new Node (n2, "f").setOrderGroup(2));
		n2.children.add(new Node (n2, "d").setOrderGroup(2));
		n2.children.add(new Node (n2, "e").setOrderGroup(2));
		n2.children.add(new Node (n2, "g"));

		System.out.println(n1);
		System.out.println(n2);

		NodeAlignment na = new NodeAlignment(n1, n2);
		double cost = na.calculateCost(HintHighlighter.getDistanceMeasure(new HintConfig()));

		System.out.println(n2);

		System.out.println(cost);
		for (Node n1c : na.mapping.keysetFrom()) {
			Node n2c = na.mapping.getFrom(n1c);
			System.out.println(n1c + "  -->  " + n2c);
		}
	}
}
