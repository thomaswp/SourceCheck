package edu.isnap.ctd.hint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Tuple;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.MapFactory;

public class Ordering {

	private final LinkedHashSet<Tuple<String, Integer>> additions = new LinkedHashSet<>();

	public Ordering() {

	}

	public Ordering(List<Node> history) {
		if (history.size() == 0) return;
		history.forEach(this::addSnapshot);
		addSolution(history.get(history.size() - 1));
	}

	public void addSnapshot(Node node) {
		extractAdditions(node).forEach(additions::add);
	}

	private List<Tuple<String,Integer>> extractAdditions(Node node) {
		CountMap<String> labelCounts = new CountMap<>(MapFactory.LinkedHashMapFactory);
		node.recurse(child -> labelCounts.increment(getLabel(child)));
		return labelCounts.keySet().stream()
				.map(k -> new Tuple<>(k, labelCounts.get(k)))
				.collect(Collectors.toList());
	}

	public void addSolution(Node node) {
		additions.retainAll(extractAdditions(node));
	}

	public static String getLabel(Node node) {
		return node.rootPathString();
	}

	@Override
	public String toString() {
		return String.join("\n", additions.stream().map(t -> t.toString()).toArray(String[]::new));
	}
}
