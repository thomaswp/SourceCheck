package edu.isnap.sourcecheck;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintConfig.SimpleHintConfig;
import edu.isnap.hint.HintConfig.ValuesPolicy;
import edu.isnap.hint.util.Alignment;
import edu.isnap.hint.util.HungarianAlgorithm;
import edu.isnap.node.Node;
import edu.isnap.node.Node.Action;
import edu.isnap.node.SimpleNode;
import edu.isnap.util.map.BiMap;
import edu.isnap.util.map.CountMap;
import edu.isnap.util.map.ListMap;
import edu.isnap.util.map.MapFactory;

public class NodeAlignment {

	public final Node from, to;
	public final HintConfig config;
	private final boolean useSubCost;

	// Temporary instance variable for use during calculateMapping
	private Mapping mapping;

	private final static String GENERATED_TAG = "GEN";

	public static class Mapping extends BiMap<Node, Node> implements Comparable<Mapping> {
		public final Node from, to;
		public final HintConfig config;

		private double cost;
		private List<MappingCost> itemizedCost = new ArrayList<>();
		private Set<String> mappedTypes = new HashSet<>();

		public final Map<String, BiMap<String, String>> valueMappings = new LinkedHashMap<>();

		public String itemizedCostString() {
			return String.format("Total cost: %.02f\n%s", cost,
					itemizedCost.stream()
					.map(Object::toString)
					.collect(Collectors.joining("\n")));
		}

		public JSONArray itemizedCostJSON() {
			JSONArray array = new JSONArray();
			itemizedCost.forEach(c -> array.put(c.toJSON()));
			return array;
		}

		public Mapping(Node from, Node to, HintConfig config) {
			super(MapFactory.IdentityHashMapFactory);
			this.from = from;
			this.to = to;
			this.config = config;
			for (String[] types : config.getValueMappedTypes()) {
				for (String type : types) mappedTypes.add(type);
			}
		}

		public double cost() {
			return cost;
		}

		public void clearCost() {
			cost = 0;
			itemizedCost.clear();
		}

		private void incrementCost(Node fromNode, Node toNode, double cost, String type) {
			this.cost += cost;
			itemizedCost.add(new MappingCost(fromNode, toNode, cost, type));
		}

		public String prettyPrint(boolean showValues) {
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
			return to.prettyPrint(showValues, labels);
		}

		@Override
		public int compareTo(Mapping o) {
			int costCompare = Double.compare(cost, o.cost);
			if (costCompare != 0) return costCompare;
			return Integer.compare(to.treeSize(), o.to.treeSize());
		}

		/**
		 * Returns a type or value to match on for this node. If the node has a value which matches
		 * a known value in the student's code, the value (or its match) will be returned. Otherwise
		 * the type will be returned.
		 */
		public String getMappedType(Node node, boolean isFrom) {
			// TODO: This is a bit problematic, since if a node (e.g. a string) has the same value
			// as an existing type, they'll be treated as interchangeable
			String value = getMappedValue(node, isFrom);
			return value == null ? node.type() : value;
		}

		/**
		 * If this node has a value which matches a known value in the students' code, the value
		 * (or its match) will be returned. Otherwise, it will return null.
		 */
		public String getMappedValue(Node node, boolean isFrom) {
			String type = node.type(), value = node.value;
			// If we should ignore value in general or for this node, we return the type
			if (config.valuesPolicy == ValuesPolicy.IgnoreAll ||
					config.shouldIgnoreNodesValues(node)) {
				return type;
			}
			// If we match values exactly, the we don't check the mapping
			if (config.valuesPolicy == ValuesPolicy.MatchAllExactly) return value;
			// If we match all values, we only use the mapping for those with a mapped type
			if (config.valuesPolicy == ValuesPolicy.MatchAllWithMapping &&
					!mappedTypes.contains(type)) {
				return value;
			}

			BiMap<String,String> map = valueMappings.get(type);
			if (map != null) {
				if (isFrom && map.containsFrom(value)) return value;
				if (!isFrom && map.containsTo(value)) return map.getTo(value);
			}
			return null;
		}

		public String[] getMappedChildArray(Node node, boolean isFrom) {
			return getMappedNodeTypeArray(node.children, isFrom);
		}

		public String[] getMappedNodeTypeArray(List<Node> nodes, boolean isFrom) {
			String[] children = new String[nodes.size()];
			for (int i = 0; i < children.length; i++) {
				children[i] = getMappedType(nodes.get(i), isFrom);
			}
			return children;
		}

		private void calculateValueMappings() {
			calculateValueMappings(this);
		}

		private void calculateValueMappings(BiMap<Node, Node> mapping) {

			valueMappings.clear();
			if (!config.shouldCalculateValueMapping()) return;

			for (String[] types : config.getValueMappedTypes()) {
				BiMap<String, String> valueMap = new BiMap<>();

				List<String> valuesFrom = getValues(from, types);
				List<String> valuesTo = getValues(to, types);

				if (valuesFrom.isEmpty() || valuesTo.isEmpty()) continue;

				for (String type : types) {
					valueMappings.put(type, valueMap);
				}

				// If any values match exactly in name, assume that's meaningful and match them
				for (int i = 0; i < valuesFrom.size(); i++) {
					String fromValue = valuesFrom.get(i);
					for (int j = 0; j < valuesTo.size(); j++) {
						if (fromValue.equals(valuesTo.get(j))) {
							valuesFrom.remove(i--);
							valuesTo.remove(j);
							valueMap.put(fromValue, fromValue);
							break;
						}
					}
				}

				if (valuesFrom.isEmpty() || valuesTo.isEmpty()) continue;

				double[][] costMatrix = new double[valuesFrom.size()][valuesTo.size()];

				double minCost = 0;
				for (Node from : mapping.keysetFrom()) {
					Node to = mapping.getFrom(from);
					if (from.hasType(types)) {
						int i = valuesFrom.indexOf(from.value);
						int j = valuesTo.indexOf(to.value);
						if (i == -1 || j == -1) continue;
						costMatrix[i][j]--;
						minCost = Math.min(minCost, costMatrix[i][j]);
					}
				}
				for (int i = 0; i < valuesFrom.size(); i++) {
					for (int j = 0; j < valuesTo.size(); j++) {
						costMatrix[i][j] -= minCost;
					}
				}

				HungarianAlgorithm alg = new HungarianAlgorithm(costMatrix);
				int[] matching = alg.execute();

				for (int i = 0; i < matching.length; i++) {
					int j = matching[i];
					if (j != -1) valueMap.put(valuesFrom.get(i), valuesTo.get(j));
				}
			}
		}

		private static List<String> getValues(Node root, String[] types) {
			Set<String> values = new HashSet<>();
			root.recurse(node -> {
				if (node.hasType(types) && node.value != null && !node.value.trim().isEmpty() &&
						// Ensure that we don't count nodes that were generated (due to annotations)
						!GENERATED_TAG.equals(node.tag)) {
					values.add(node.value);
				}
			});
			return new ArrayList<>(values);
		}

		public void printValueMappings(PrintStream out) {
			out.println("Value mappings:");
			for (String[] types : config.getValueMappedTypes()) {
				if (types.length == 0) continue;
				String key = types[0];
				BiMap<String, String> map = valueMappings.get(key);
				if (map != null) {
					out.println("-- " + Arrays.toString(types) + " --");
					for (String from : map.keysetFrom()) {
						out.printf("%s <-> %s\n", from, map.getFrom(from));
					}
				}
			}
		}

		public static class MappingCost {
			public final Node fromNode, toNode;
			public final double cost;
			public final String type;

			public MappingCost(Node fromNode, Node toNode, double cost, String type) {
				super();
				this.fromNode = fromNode;
				this.toNode = toNode;
				this.cost = cost;
				this.type = type;
			}

			@Override
			public String toString() {
				String out = String.format("[%.02f] %s / %s: %s vs %s",
						cost, type, fromNode.rootPathString(),
						Arrays.toString(fromNode.getChildArray()),
						Arrays.toString(toNode.getChildArray()));
				if (fromNode.id == null) out = "*" + out;
				if (toNode.id == null) out = "+" + out;
				return out;
			}

			public JSONObject toJSON() {
				JSONObject json = new JSONObject();
				json.put("type", type);
				json.put("cost", cost);
				json.put("fromID", fromNode.id);
				json.put("toID", toNode.id);
				return json;
			}
		}
	}

	public NodeAlignment(Node from, Node to, HintConfig config) {
		this(from, to, config, true);
	}

	private NodeAlignment(Node from, Node to, HintConfig config, boolean useSubCost) {
		this.from = from;
		this.to = to;
		this.config = config;
		this.useSubCost = useSubCost;
	}

	public Mapping calculateMapping(DistanceMeasure distanceMeasure) {
		return calculateMapping(distanceMeasure, null);
	}

	private Mapping calculateMapping(DistanceMeasure distanceMeasure, Mapping previousMapping) {
		mapping = new Mapping(from, to, config);
		// If we're given a previous mapping, we use it to calculate value mappings
		if (previousMapping != null) mapping.calculateValueMappings(previousMapping);

		to.resetAnnotations();
		ListMap<String, Node> fromMap = getChildMap(from, true);
		ListMap<String, Node> toMap = getChildMap(to, false);

		for (String key : fromMap.keySet()) {
			List<Node> fromNodes = fromMap.get(key);
			List<Node> toNodes = toMap.get(key);

			if (toNodes == null) {
				// Continue if we have no toNodes to match
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

		// The matching only works one level deep, so if the config says so, add penalties for
		// other descendants of unmatched nodes.
//		if (config.penalizeUnmatchedNodeDescendants) {
			to.recurse(node -> {
				if (config.isValueless(node.type())) return;
				if (mapping.containsTo(node)) return;
				if (mapping.containsTo(node.parent)) return;
				mapping.incrementCost(node, node, config.progressMissingFactor,
						"Descendant of unmatched node");
			});
//		}

		// Clear mapping before returning it
		Mapping ret = mapping;
		mapping = null;

		if (config.shouldCalculateValueMapping() && previousMapping == null) {
			// If we we're not given a previous mapping, this is the first round, so recalculate
			// using this mapping to map values
			return calculateMapping(distanceMeasure, ret);
		}

		// Recalculate the value mappings with our own data
		ret.calculateValueMappings();
		return ret;
	}

	private Node getContainer(DistanceMeasure distanceMeasure, Node from) {
		Node parent = from.parent;
		while (parent != null && parent.parent != null &&
				!config.isContainer(parent.type())) {
			parent = parent.parent;
		}
		return parent;
	}

	public interface DistanceMeasure {
		public double measure(Node from, String[] a, String[] b, int[] bOrderGroups);
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
			if (!config.hasFixedChildren(from)) {
				return Alignment.getMissingNodeCount(a, b) * missingCost -
						Alignment.getProgress(a, b, bOrderGroups,
								// TODO: skip cost should maybe be another value?
								inOrderReward, outOfOrderReward, missingCost);
			} else {
				// TODO: this is a little snap-specific, so perhaps modify later
				// TODO: support order groups in parameters
				double cost = 0;
				int i = 0;
				for (; i < a.length && i < b.length; i++) {
					if (a[i].equals(b[i])) {
						if (!config.isValueless(a[i])) cost -= inOrderReward;
					} else {
						cost += missingCost;
					}
				}
				// If the target solution has extra children, we penalize with the missing cost
				// (This mostly just happens with lists)
				cost += missingCost * Math.max(0, b.length - i);
				return cost;
			}
		}

		@Override
		public double matchedOrphanReward(String type) {
			if (config.isValueless(type)) return 0;
			// A matched orphan node should be equivalent to the node being out of order, and we
			// also have to counteract the cost of the node being missing from its original parent
			return outOfOrderReward + missingCost;
		}
	};

	private void align(List<Node> fromNodes, List<Node> toNodes,
			DistanceMeasure distanceMeasure, final ListMap<String, Node> fromMap) {
		String[][] fromStates = stateArray(fromNodes, true);
		String[][] toStates = stateArray(toNodes, false);
		int[][] toOrderGroups = orderGroups(toNodes);

		String type = fromNodes.get(0).type();

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
				Node from = fromNodes.get(i), to = toNodes.get(j);
				double cost = costMatrix[i][j];
				// Only break ties for entries
				if (costCounts.get(cost) <= 1) continue;
				// For any matches, break ties based on the existing mappings
				if (to.readOnlyAnnotations().matchAnyChildren) {
					if (mapping.getFrom(from) == to) cost -= 1;
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
					double subCost = getSubCostEsitmate(from, to, config, distanceMeasure);
					cost += subCost * 0.001;
				}
				// Break further ties with existing mappings from parents
				if (mapping.getFrom(from) == to) cost -= 0.0001;
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
				addMatchingChildren(from, to, fromMap);
				// Then we're done, so continue
				continue;
			}

			// Recalculate the distance to remove tie-breaking costs
			double matchCost = distanceMeasure.measure(from, fromStates[i],
					toStates[j], toOrderGroups[j]);
			mapping.incrementCost(from, to, matchCost, "Match Children");

			// If we are pairing nodes that have not been paired from their parents, there should
			// be some reward for this, determined by the distance measure
			if (!mappedFrom[i] && !mappedTo[j]) {
				mapping.incrementCost(from, to,
						-distanceMeasure.matchedOrphanReward(type), "Match Parents");
			}
			mapping.put(from, to);
			toStates[j] = reorderToIfNeeded(from, to, fromStates[i], toStates[j], toOrderGroups[j]);

			matchChildren(from.children, to.children);

		}
	}

	private void addMatchingChildren(final Node from, final Node to,
			final ListMap<String, Node> fromMap) {
		mapping.put(from, to);
		to.children.clear();
		from.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node == from) return;
				// Make a copy of the node and match them together
				Node parent = mapping.getFrom(node.parent);
				Node copy = node.shallowCopy(parent);
				copy.tag = GENERATED_TAG;
				parent.children.add(copy);
				mapping.put(node, copy);

				// Remove the from-child from the fromMap, so it doesn't get matched to
				// other things later on
				List<Node> list = fromMap.get(parentNodeKey(node, true));
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
	}

	private String[] reorderToIfNeeded(Node from, Node to, String[] fromState,
			String[] toState, int[] toOrderGroups) {
		// Get any reordering of the to states that needs to be done and see if anything is
		// out of order
		// TODO: This doesn't catch reorders that will be needed for child nodes
		// We do have a failsafe in HintHighlighter, but this would be better fixed
		// TODO: This does not work well for arguments, which cannot be deleted.
		// So [A, B] and [B, C] won't reorder, since a is assumed to be deleted.
		int[] reorders = Alignment.reorderIndices(fromState, toState, toOrderGroups);
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
				Alignment.reorderIndices(fromState, toState, toOrderGroups);
				throw new RuntimeException("Invalid reorder indices: " +
						Arrays.toString(reorders));
			}

			to.children.clear();
			to.children.addAll(reordered);
			return mapping.getMappedChildArray(to, false);
		}
		return toState;
	}

	private void matchChildren(List<Node> fromChildren, List<Node> toChildren) {
		String[] fromChildrenTypes = mapping.getMappedNodeTypeArray(fromChildren, true);
		String[] toChildrenTypes = mapping.getMappedNodeTypeArray(toChildren, false);

		// Try to align the children of these paired nodes and add them to the mapping,
		// but don't worry about score, since that's accounted for in the progress score
		// These mappings may be overwritten later if the nodes themselves are matched as
		// parents, but this is not a wholly consistent alignment algorithm
		// We use 2/2/3 here to use replacements only if necessary (these are not returned as
		// pairs if they do not match)
		List<int[]> childPairs = Alignment.alignPairs(fromChildrenTypes, toChildrenTypes, 2, 2, 3);
		List<Node> unpairedFrom = new LinkedList<>(), unpairedTo = new LinkedList<>();
		for (int[] pair : childPairs) {
			if (pair[0] >= 0 && pair[1] >= 0) {
				mapping.put(fromChildren.get(pair[0]), toChildren.get(pair[1]));
			} else if (pair[1] >= 0) {
				// We also keep track of unpaired nodes to match up out-of-order nodes
				unpairedTo.add(toChildren.get(pair[1]));
			} else if (pair[0] >= 0) {
				unpairedFrom.add(fromChildren.get(pair[0]));
			}
		}
		// Look back through all the unpaired from and to nodes and try to find matches
		// (i.e. out-of-order nodes)
		for (Node uf : unpairedFrom) {
			for (int k = 0; k < unpairedTo.size(); k++) {
				Node ut = unpairedTo.get(k);
				if (mapping.getMappedType(uf, true).equals(mapping.getMappedType(ut, false))) {
					mapping.put(uf, ut);
					unpairedTo.remove(k);
					break;
				}
			}
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

	public static double getSubCostEsitmate(Node a, Node b, HintConfig config, DistanceMeasure dm) {
		String[] aDFI = a.depthFirstIteration();
		String[] bDFI = b.depthFirstIteration();
		if (config.useDeletionsInSubcost) {
			return dm.measure(null, aDFI, bDFI, null);
		} else {
			for (int i = 0; i < aDFI.length; i++) if (config.isValueless(aDFI[i])) aDFI[i] = null;
			for (int i = 0; i < bDFI.length; i++) if (config.isValueless(bDFI[i])) bDFI[i] = null;
			return -Alignment.getProgress(aDFI, bDFI, 1, 0, 0);
		}
	}

	private String[][] stateArray(List<Node> nodes, boolean isFrom) {
		String[][] states = new String[nodes.size()][];
		for (int i = 0; i < states.length; i++) {
			states[i] = mapping.getMappedChildArray(nodes.get(i), isFrom);
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

	private ListMap<String, Node> getChildMap(Node node, boolean isFrom) {
		final ListMap<String, Node> map = new ListMap<>(MapFactory.LinkedHashMapFactory);
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.children.isEmpty() && node.parent != null &&
						!node.readOnlyAnnotations().matchAnyChildren) {
					return;
				}
				String key = parentNodeKey(node, isFrom);
				map.add(key, node);
			}
		});
		return map;
	}

	private String parentNodeKey(Node node, boolean isFrom) {
		String[] list = new String[node.depth() + 1];
		int i = list.length - 1;
		while (node != null) {
			list[i--] = mapping.getMappedType(node, isFrom);
			node = node.parent;
		}
		return Arrays.toString(list);
	}

	public static List<Mapping> findBestMatches(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure, HintConfig config, int maxReturned) {
		List<Mapping> best = new LinkedList<>();
		if (matches.size() == 0) return best;

		boolean v2 = config.sourceCheckV2;

		Mapping[] mappings = new Mapping[matches.size()];
		for (int i = 0; i < mappings.length; i++) {
			NodeAlignment align = new NodeAlignment(from, matches.get(i), config);
			Mapping mapping = v2 ? align.align(distanceMeasure) :
				align.calculateMapping(distanceMeasure);
			mappings[i] = mapping;
		}

		Arrays.sort(mappings);

		for (int i = 0; i < maxReturned && i < mappings.length; i++) {
			best.add(mappings[i]);
		}
		return best;
	}

	public static Mapping findBestMatch(Node from, List<Node> matches,
			DistanceMeasure distanceMeasure, HintConfig config) {
		List<Mapping> bestMatches = findBestMatches(from, matches, distanceMeasure, config, 1);
		return bestMatches.size() > 0 ? bestMatches.get(0) : null;
	}

	public static void main1(String[] args) {
		Node n1 = new SimpleNode(null, "script", null, null);
		n1.addChild("a");
		n1.addChild("b");
		n1.addChild("c");
		n1.addChild("d");
		n1.addChild("e");
		n1.addChild("f");

		Node n2 = new SimpleNode(null, "script", null, null);
		n2.addChild("b").setOrderGroup(1);
		n2.addChild("a").setOrderGroup(1);
		n2.addChild("c");
		n2.addChild("f").setOrderGroup(2);
		n2.addChild("d").setOrderGroup(2);
		n2.addChild("e").setOrderGroup(2);
		n2.addChild("g");

		System.out.println(n1);
		System.out.println(n2);

		NodeAlignment na = new NodeAlignment(n1, n2, new SimpleHintConfig());
		double cost = na.calculateMapping(
				HintHighlighter.getDistanceMeasure(new SimpleHintConfig())).cost;

		System.out.println(n2);

		System.out.println(cost);
		for (Node n1c : na.mapping.keysetFrom()) {
			Node n2c = na.mapping.getFrom(n1c);
			System.out.println(n1c + "  -->  " + n2c);
		}
	}

	public Mapping align(DistanceMeasure distanceMeasure) {
		return align(distanceMeasure, null);
	}

	private Mapping align(DistanceMeasure dm, Mapping previousMapping) {
		mapping = new Mapping(from, to, config);
		if (previousMapping != null) mapping.calculateValueMappings(previousMapping);

		ListMap<String, Node> fromNodeMap = getNodesByType(from, true);
		ListMap<String, Node> toNodeMap = getNodesByType(to, false);

		// TODO: idea: maybe do a BF-iteration of the from nodes and greedily match them, then
		// when calculating the root path match cost, stop alignment at the nearest matched parent,
		// so a pair of matched nodes with matched parents have 0 RP alignment cost
		for (String type : fromNodeMap.keySet()) {
			List<Node> fromNodes = fromNodeMap.get(type);
			List<Node> toNodes = toNodeMap.get(type);
			if (toNodes == null) continue;

			double[][] costMatrix = new double[fromNodes.size()][toNodes.size()];

			if (fromNodes.size() * toNodes.size() == 0) continue;

			for (int i = 0; i < fromNodes.size(); i++) {
				for (int j = 0; j < toNodes.size(); j++) {
					Node fromCandidate = fromNodes.get(i);
					Node toCandidate = toNodes.get(j);

					double subCost = -getSubCostEsitmate(fromCandidate, toCandidate, config, dm);

					String[] rpFrom = getRootPathArray(fromCandidate, true);
					String[] rpTo = getRootPathArray(toCandidate, true);
					double parentCost = Alignment.alignCost(rpFrom, rpTo, 1, 1, 1);

					// TODO: config
					double cost = subCost * 0.5 + parentCost;
					costMatrix[i][j] = cost;
				}
			}

			HungarianAlgorithm alg = new HungarianAlgorithm(costMatrix);
			int[] matching = alg.execute();

			for (int i = 0; i < fromNodes.size(); i++) {
				int j = matching[i];
				if (j < 0) continue;

				// TODO: determine/config
				if (costMatrix[i][j] > 1000) continue;
				mapping.put(fromNodes.get(i), toNodes.get(j));
			}
		}

		List<Node> queue = new ArrayList<>();
		queue.add(from);
		while (queue.size() > 0) {
			Node fromCandidate = queue.remove(0);
			for (Node child : fromCandidate.children) {
				if (!fromCandidate.children.isEmpty()) queue.add(child);
			}

			Node toCandidate = mapping.getFrom(fromCandidate);
			if (toCandidate == null) continue;

			List<Node> fromChildren = new ArrayList<>(fromCandidate.children);
			List<Node> toChildren = new ArrayList<>(toCandidate.children);
			for (int i = 0; i < fromChildren.size(); i++) {
				Node fromChild = fromChildren.get(i);
				Node pair = mapping.getFrom(fromChild);
				boolean matched = false;
				for (int j = 0; j < toChildren.size(); j++) {
					if (toChildren.get(j) == pair) {
						toChildren.remove(j);
						matched = true;
						break;
					}
				}
				if (matched) {
					fromChildren.remove(i--);
				}
			}

			// Basic problem here: if we remap a node to be under it's parent's match, it's old
			// match is now unpaired
			matchChildren(fromChildren, toChildren);
		}

		if (config.shouldCalculateValueMapping() && previousMapping == null) {
			// If we we're not given a previous mapping, this is the first round, so recalculate
			// using this mapping to map values
			return align(dm, mapping);
		}

		mapping.calculateValueMappings();
		calculateMappingReward();
		return mapping;
	}

	private void calculateMappingReward() {
		mapping.clearCost();
		// Each node in from can contribute up to 2 reward points:
		//  - One for how close it's root path is to it's pair
		//  - One for being in the proper order for its parent
		// Each unpaired node in "to" adds 0.5 cost point
		from.recurse(node -> {
			if (config.isValueless(node.type())) return;
			Node pair = mapping.getFrom(node);
			if (pair == null) return;

			List<String> fromIDs = new ArrayList<>();
			List<String> toIDs = new ArrayList<>();
			for (Node child : node.children) {
				Node childPair = mapping.getFrom(child);
				if (!config.isValueless(node.type()) &&
						childPair != null && childPair.parent == pair) {
					fromIDs.add(child.hexHash());
				}
			}
			for (Node child : pair.children) {
				Node childPair = mapping.getTo(child);
				if (!config.isValueless(node.type()) &&
						childPair != null && childPair.parent == node) {
					toIDs.add(childPair.hexHash());
				}
			}
			double reward = -Alignment.getProgress(
					fromIDs.toArray(new String[fromIDs.size()]),
					toIDs.toArray(new String[toIDs.size()]), 1, 0);
			reward--;
			mapping.incrementCost(node, pair, reward, "Paired nodes");

		});
		to.recurse(node -> {
			if (!config.isValueless(node.type()) && !mapping.containsTo(node)) {
				mapping.incrementCost(node, node, 0.5, "Unpaired node in to");
			}
		});
	}

	private String[] getRootPathArray(Node node, boolean isFrom) {
		String[] rp = new String[node.rootPathLength()];
		int i = rp.length - 1;
		while (i >= 0) {
			rp[i--] = mapping.getMappedType(node, isFrom);
			node = node.parent;
		}
		return rp;
	}

	private ListMap<String, Node> getNodesByType(Node node, boolean isFrom) {
		final ListMap<String, Node> map = new ListMap<>(MapFactory.LinkedHashMapFactory);
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				if (node.children.isEmpty() && node.parent != null &&
						!node.readOnlyAnnotations().matchAnyChildren) {
					return;
				}
				String key = mapping.getMappedType(node, isFrom);
				map.add(key, node);
			}
		});
		return map;
	}
}
