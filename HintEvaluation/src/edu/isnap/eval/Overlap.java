package edu.isnap.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.aggregate.CSC200;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.util.map.CountMap;

public class Overlap {

	public static void main(String[] args) {
		Map<String, AssignmentAttempt> gg1 = CSC200.GuessingGame1.load(Mode.Use, true,
				true, new SnapParser.LikelySubmittedOnly());
		CountMap<String> counts = new CountMap<>();
		CountMap<String> countsNoValues = new CountMap<>();
		CountMap<String> countsNoOrder = new CountMap<>();
		System.out.println("Size: " + gg1.size());

		int i = 0;
		for (AssignmentAttempt attempt : gg1.values()) {
			Set<String> nodes = new HashSet<>();
			Set<String> nodesNoValues = new HashSet<>();
			Set<String> nodesNoOrder = new HashSet<>();

			for (AttemptAction action : attempt) {
				Node node = SimpleNodeBuilder.toTree(action.lastSnapshot, true);

				Node largestScript = null;
				int largestTreeSize = -1;
				List<Node> scripts = new ArrayList<>();
				node.recurse(n -> {
					if (n.hasType("script")) {
						scripts.add(n);
					}
				});
				for (Node script : scripts) {
					int treeSize = script.treeSize();
					if (treeSize > largestTreeSize) {
						largestTreeSize = treeSize;
						largestScript = script;
					}
				}
				for (Node script : scripts) {
					if (script == largestScript) continue;
					script.parent.children.remove(script.index());
				}

				nodes.add(node.toCanonicalString());
				nodesNoValues.add(normalize(node, null, false).toCanonicalString());
				nodesNoOrder.add(normalize(node, null, true).toCanonicalString());
			}

			nodes.forEach(n -> counts.increment(n));
			nodesNoValues.forEach(n -> countsNoValues.increment(n));
			nodesNoOrder.forEach(n -> countsNoOrder.increment(n));

			System.out.println(i++  + " / " + gg1.size());
		}

		print(counts);
		print(countsNoValues);
		print(countsNoOrder);
	}

	static void print(CountMap<String> countMap) {
		int duped = (int) countMap.values().stream().filter(n -> n > 1).count();
		System.out.println(duped + "/" + countMap.size() + " = " +
				((double) duped / countMap.size()));
	}

	static Node normalize(Node node, Node parent, boolean order) {
		Node copy = node.constructNode(parent, node.type());
		List<Node> children = node.children.stream()
				.map(n -> normalize(n, copy, order))
				.collect(Collectors.toList());
		if (order) {
			children.sort((n1, n2) -> n1.toCanonicalString().compareTo(n2.toCanonicalString()));
		}
		copy.children.addAll(children);
		return copy;
	}
}
