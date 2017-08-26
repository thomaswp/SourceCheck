package edu.isnap.ctd.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
	private final boolean useSubCost;

	// Temporary instance variable for use during calculateMapping
	private Mapping mapping;

	public static class Mapping extends BiMap<Node, Node> implements Comparable<Mapping> {
		public final Node from, to;

		private double cost;
		private StringBuilder itemizedCost = new StringBuilder();

		public String itemizedCost() {
			return itemizedCost.toString();
		}

		public Mapping(Node from, Node to) {
			super(MapFactory.IdentityHashMapFactory);
			this.from = from;
			this.to = to;
		}

		public double cost() {
			return cost;
		}

		private void incrementCost(double by, String key) {
			cost += by;
			itemizedCost.append(by);
			itemizedCost.append(": ");
			itemizedCost.append(key);
			itemizedCost.append("\n");
		}

		public String prettyPrint() {
			final IdentityHashMap<Node, String> labels = new IdentityHashMap<>();
			to.recurse(new Action() {
				@Override
				public void run(Node node) {
					Node pair = getTo(node);
					if (pair != null) {
						labels.put(node, pair.id);
					}
				}
			});
			return to.prettyPrint(labels);
		}

		@Override
		public int compareTo(Mapping o) {
			return Double.compare(cost, o.cost);
		}
	}

	public NodeAlignment(Node from, Node to) {
		this(from, to, true);
	}

	private NodeAlignment(Node from, Node to, boolean useSubCost) {
		this.from = from;
		this.to = to;
		this.useSubCost = useSubCost;
	}

	public Mapping calculateMapping(DistanceMeasure distanceMeasure) {
		mapping = new Mapping(from, to);

		to.resetAnnotations();
		ListMap<String, Node> fromMap = getChildMap(from);
		ListMap<String, Node> toMap = getChildMap(to);

		mapping.clear();

		for (String key : fromMap.keySet()) {
			List<Node> fromNodes = fromMap.get(key);
			List<Node> toNodes = toMap.get(key);

			if (toNodes == null) {
				// Continue if we have to toNodes to match
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

		// Clear mapping before returning it
		Mapping ret = mapping;
		mapping = null;
		return ret;
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
		public double measure(Node from, String[] a, String[] b, int[] bOrderGroups);
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
		public double measure(Node from, String[] a, String[] b, int[] bOrderGroups) {
			if (!config.isCodeElement(from)) {
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
		String costKey = fromNodes.get(0).rootPath();

		double minCost = Integer.MAX_VALUE;
		double[][] costMatrix = new double[fromStates.length][toStates.length];
		CountMap<Double> costCounts = new CountMap<>();
		for (int i = 0; i < fromStates.length; i++) {
			for (int j = 0; j < toStates.length; j++) {
				double cost = distanceMeasure.measure(fromNodes.get(i), fromStates[i], toStates[j],
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
			double matchCost = distanceMeasure.measure(from, fromStates[i],
					toStates[j], toOrderGroups[j]);
			mapping.incrementCost(matchCost, String.format("%s: %s vs %s",
					costKey, Arrays.toString(fromStates[i]), Arrays.toString(toStates[j])));

			// If we are pairing nodes that have not been paired from their parents, there should
			// be some reward for this, determined by the distance measure
			if (!mappedFrom[i] && !mappedTo[j]) {
				mapping.incrementCost(-distanceMeasure.matchedOrphanReward(type), costKey + " [r]");
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
			List<Node> unpairedFrom = new LinkedList<>(), unpairedTo = new LinkedList<>();
			for (int[] pair : childPairs) {
				if (pair[0] >= 0 && pair[1] >= 0) {
					mapping.put(from.children.get(pair[0]), to.children.get(pair[1]));
				} else if (pair[1] >= 0) {
					// We also keep track of unpaired nodes to match up out-of-order nodes
					unpairedTo.add(to.children.get(pair[1]));
				} else if (pair[0] >= 0) {
					unpairedFrom.add(from.children.get(pair[0]));
				}
			}
			// Look back through all the unpaired from and to nodes and try to find matches
			// (i.e. out-of-order nodes)
			for (Node uf : unpairedFrom) {
				for (int k = 0; k < unpairedTo.size(); k++) {
					Node ut = unpairedTo.get(k);
					if (uf.type().equals(ut.type())) {
						mapping.put(uf, ut);
						unpairedTo.remove(k);
						break;
					}
				}
			}

		}

		// For each unmatched toState (in the proposed solution), add its cost as well
		// This essentially penalizes unused nodes that have a matching root path in the student's
		// current solution, but not their children
		for (int i = 0; i < toStates.length; i++) {
			if (matchedTo.contains(i)) continue;
			mapping.incrementCost(distanceMeasure.measure(
					toNodes.get(i), new String[0], toStates[i], null), costKey + " [p]");
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

	public static List<Mapping> findBestMatches(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure, double stdevsFromMin) {
		List<Mapping> best = new LinkedList<>();
		if (matches.size() == 0) return best;

		Mapping[] mappings = new Mapping[matches.size()];
		double totalCost = 0;
		double minCost = Double.MAX_VALUE;
		for (int i = 0; i < mappings.length; i++) {
			NodeAlignment align = new NodeAlignment(from, matches.get(i));
			Mapping mapping = align.calculateMapping(distanceMeasure);
			mappings[i] = mapping;
			totalCost += mapping.cost;
			minCost = Math.min(minCost, mapping.cost);
		}

		// Calculate stdev for the mapping costs
		double meanCost = totalCost / mappings.length;
		double deviation = 0;
		for (Mapping mapping : mappings) deviation += Math.pow(mapping.cost - meanCost, 2);
		double stdev = mappings.length == 1 ? 0 : Math.sqrt(deviation / (mappings.length - 1));

		// The cutoff is the minimum cost, plus some (likely fractional) number of stdevs
		double costCutoff = minCost + stdev * stdevsFromMin;

		for (int i = 0; i < mappings.length; i++) {
			if (mappings[i].cost <= costCutoff) best.add(mappings[i]);
		}
		return best;
	}

	public static Mapping findBestMatch(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure) {
		Mapping best = null;
		int smallest = Integer.MAX_VALUE;
		List<Mapping> bestMatches = findBestMatches(from, matches, distanceMeasure, 0);
//		System.out.println("Size: " + bestMatches.size());
		for (Mapping mapping : bestMatches) {
			int size = mapping.to.treeSize();
			if (size < smallest) {
				best = mapping;
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
		double cost = na.calculateMapping(
				HintHighlighter.getDistanceMeasure(new HintConfig())).cost;

		System.out.println(n2);

		System.out.println(cost);
		for (Node n1c : na.mapping.keysetFrom()) {
			Node n2c = na.mapping.getFrom(n1c);
			System.out.println(n1c + "  -->  " + n2c);
		}
	}
}
