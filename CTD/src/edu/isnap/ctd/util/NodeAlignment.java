package edu.isnap.ctd.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.Canonicalization;
import edu.isnap.ctd.hint.Canonicalization.OrderGroup;
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

	private int cost;

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
		return calculateCost(distanceMeasure, false);
	}

	private int calculateCost(DistanceMeasure distanceMeasure, boolean debug) {
		cost = 0;

		ListMap<String, Node> fromMap = getChildMap(from);
		ListMap<String, Node> toMap = getChildMap(to);

		mapping.clear();

		for (String key : fromMap.keySet()) {
			List<Node> fromNodes = fromMap.get(key);
			List<Node> toNodes = toMap.get(key);

			if (toNodes == null) {
				for (Node node : fromNodes) {
					mapping.put(node, null);
				}
				continue;
			}

			align(fromNodes, toNodes, distanceMeasure, debug);
		}
		return cost;
	}

	public interface DistanceMeasure {
		public double measure(String type, String[] a, String[] b, int[] bOrderGroups);
	}

	public static class ProgressDistanceMeasure implements DistanceMeasure {

		public final int inOrderReward, outOfOrderReward;
		public final double missingCost;
		public final String scriptType, ignoreType;

		public ProgressDistanceMeasure(int inOrderReward, int outOfOrderReward,
				double missingCost, String scriptType, String ignoreType) {
			this.inOrderReward = inOrderReward;
			this.outOfOrderReward = outOfOrderReward;
			this.missingCost = missingCost;
			this.scriptType = scriptType;
			this.ignoreType = ignoreType;
		}

		@Override
		public double measure(String type, String[] a, String[] b, int[] bOrderGroups) {
			if (type == null || scriptType.equals(type)) {
				return Alignment.getMissingNodeCount(a, b) * missingCost -
						Alignment.getProgress(a, b, bOrderGroups,
								// TODO: skip cost should maybe be another value?
								inOrderReward, outOfOrderReward, missingCost);
			} else {
				// TODO: this is a little snap-specific, so perhaps modify later
				// TODO: support order groups
				int cost = 0;
				for (int i = 0; i < a.length && i < b.length; i++) {
					if (!a[i].equals(ignoreType) && a[i].equals(b[i])) cost -= inOrderReward;
				}
				return cost;
			}
		}
	};

	private void align(List<Node> fromNodes, List<Node> toNodes,
			DistanceMeasure distanceMeasure, boolean debug) {
		String[][] fromStates = stateArray(fromNodes);
		String[][] toStates = stateArray(toNodes);
		int[][] toOrderGroups = orderGroups(toNodes);

		// TODO: remove debug flag
//		if (debug && fromNodes.get(0).type().equals("doSayFor")) {
//			System.out.println("!");
//		}

		double minCost = Integer.MAX_VALUE;
		double[][] costMatrix = new double[fromStates.length][toStates.length];
		CountMap<Double> costCounts = new CountMap<>();
		for (int i = 0; i < fromStates.length; i++) {
			for (int j = 0; j < toStates.length; j++) {
				String type = fromNodes.get(i).type();
				double cost = distanceMeasure.measure(type, fromStates[i], toStates[j],
						toOrderGroups[j]);
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

		for (int i = 0; i < fromStates.length; i++) {
			int j = matching[i];
			matchedTo.add(j);
			// TODO: why don't we penalize unmatched nodes in fromStates?
			// Especially when we do penalize unmatched nodes in toStates below...
			if (j == -1) continue;
			cost += costMatrix[i][j] + minCost;
			Node from = fromNodes.get(i), to = toNodes.get(j);
			mapping.put(from, to);

			// Get any reordering of the to states that needs to be done and see if anything is
			// out of order
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
				to.children.clear();
				to.children.addAll(reordered);
				toStates[j] = to.getChildArray();

				// Sanity check
				Arrays.sort(reorders);
				if (needsReorder(reorders)) {
					throw new RuntimeException("Invalid reorder indices: " +
							Arrays.toString(reorders));
				}
			}

			// Try to align the children of these paired nodes and add them to the mapping,
			// but don't worry about score, since that's accounted for in the progress score
			// These mappings may be overwritten later if the nodes themselves are matched as
			// parents, but this is not a wholly consistent alignment algorithm
			List<int[]> childPairs = Alignment.alignPairs(fromStates[i], toStates[j], 1, 1, 100);
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

	private double getSubCostEsitmate(Node a, Node b, DistanceMeasure dm) {
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
				for (Canonicalization can : node.children.get(j).canonicalizations) {
					if (can instanceof OrderGroup) {
						orders[j] = ((OrderGroup) can).group;
						break;
					}
				}
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
				if (node.children.isEmpty()) return;
				String key = HintMap.toRootPath(node).root().toCanonicalString();
				map.add(key, node);
			}
		});
		return map;
	}

	public static List<Node> findBestMatches(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure) {
		List<Node> best = new LinkedList<>();
		int bestCost = Integer.MAX_VALUE;
		for (Node to : matches) {
			NodeAlignment align = new NodeAlignment(from, to);
			int cost = align.calculateCost(distanceMeasure, false);
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
		n2.children.add(new Node (n2, "b").addCanonicalization(new OrderGroup(1)));
		n2.children.add(new Node (n2, "a").addCanonicalization(new OrderGroup(1)));
		n2.children.add(new Node (n2, "c"));
		n2.children.add(new Node (n2, "f").addCanonicalization(new OrderGroup(2)));
		n2.children.add(new Node (n2, "d").addCanonicalization(new OrderGroup(2)));
		n2.children.add(new Node (n2, "e").addCanonicalization(new OrderGroup(2)));
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
