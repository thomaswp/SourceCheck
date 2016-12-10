package edu.isnap.ctd.util;

import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.util.BiMap.MapFactory;

public class NodeAlignment {

	public final Node from, to;

	public final BiMap<Node, Node> mapping = new BiMap<>(new MapFactory() {
		@Override
		public <A, B> Map<A, B> createMap() {
			return new IdentityHashMap<>();
		}
	});;


	private int score = 0;

	public NodeAlignment(Node from, Node to) {
		this.from = from;
		this.to = to;
	}

	public int calculateScore(int inOrderReward, int outOfOrderReward) {
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

			align(fromNodes, toNodes, inOrderReward, outOfOrderReward);
		}
		return score;
	}

	private void align(List<Node> fromNodes, List<Node> toNodes,
			int inOrderReward, int outOfOrderReward) {
		String[][] fromStates = stateArray(fromNodes);
		String[][] toStates = stateArray(toNodes);

		int maxCost = 0;
		double[][] costMatrix = new double[fromStates.length][toStates.length];
		for (int i = 0; i < fromStates.length; i++) {
			for (int j = 0; j < toStates.length; j++) {
				int cost = Alignment.getProgress(fromStates[i], toStates[j],
						inOrderReward, outOfOrderReward);
				costMatrix[i][j] = cost;
				maxCost = Math.max(maxCost, cost);
			}
		}
		// Invert the score to make it a cost
		for (int i = 0; i < fromStates.length; i++) {
			for (int j = 0; j < toStates.length; j++) {
				costMatrix[i][j] = maxCost - costMatrix[i][j];
			}
		}

		HungarianAlgorithm alg = new HungarianAlgorithm(costMatrix);
		int[] matching = alg.execute();

		for (int i = 0; i < fromStates.length; i++) {
			int j = matching[i];
			if (j == -1) continue;
			score += maxCost - costMatrix[i][j];
			Node from = fromNodes.get(i), to = toNodes.get(j);
			mapping.put(from, to);

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
	}

	private String[][] stateArray(List<Node> nodes) {
		String[][] states = new String[nodes.size()][];
		for (int i = 0; i < states.length; i++) {
			states[i] = nodes.get(i).getChildArray();
		}
		return states;
	}

	private ListMap<String, Node> getChildMap(Node node) {
		final ListMap<String, Node> map = new ListMap<>();
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
			int inOrderReward, int outOfOrderReward) {
		List<Node> best = new LinkedList<>();
		int bestScore = 0;
		for (Node to : matches) {
			NodeAlignment align = new NodeAlignment(from, to);
			int score = align.calculateScore(inOrderReward, outOfOrderReward);
			if (score > bestScore) {
				best.clear();
			}
			if (score >= bestScore) {
				bestScore = score;
				best.add(to);
			}
		}
		return best;
	}

	public static Node findBestMatch(Node from, List<Node> matches,
			int inOrderReward, int outOfOrderReward) {
		Node best = null;
		int smallest = Integer.MAX_VALUE;
		List<Node> bestMatches = findBestMatches(from, matches, inOrderReward, outOfOrderReward);
		System.out.println("Size: " + bestMatches.size());
		for (Node node : bestMatches) {
			int size = node.size();
			if (size < smallest) {
				best = node;
				smallest = size;
			}
		}
		return best;
	}
}
