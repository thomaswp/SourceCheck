package edu.isnap.ctd.hint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.Tuple;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.MapFactory;

public class Ordering {

	// TODO: config
	private final static int MAX_RP_LENGTH = 3;

	private final LinkedHashSet<Tuple<String, Integer>> additions = new LinkedHashSet<>();
	private final ArrayList<Tuple<String, Integer>> listWrapper = new ArrayList<>();

	public Ordering() {

	}

	public Ordering(List<Node> history) {
		if (history.size() == 0) return;
		history.forEach(this::addSnapshot);
		addSolution(history.get(history.size() - 1));
	}

	public int getOrder(String label, int count) {
		return getOrder(new Tuple<>(label, count));
	}

	public int getOrder(Tuple<String, Integer> item) {

		if (listWrapper.size() != additions.size()) {
			listWrapper.clear();
			listWrapper.addAll(additions);
		}
		return listWrapper.indexOf(item);
	}

	public void addSnapshot(Node node) {
		extractAdditions(node).forEach(additions::add);
	}

	private List<Tuple<String,Integer>> extractAdditions(Node node) {
		CountMap<String> labelCounts = countLabels(node);
		return labelCounts.keySet().stream()
				// For each label/count, add <label,c> for c in {1..count},
				// since have 2 of something means you also have 1 of it
				.flatMap(k -> IntStream.range(1, labelCounts.get(k) + 1)
						.mapToObj(i -> new Tuple<>(k, i)))
				.collect(Collectors.toList());
	}

	public static CountMap<String> countLabels(Node node) {
		CountMap<String> labelCounts = new CountMap<>(MapFactory.LinkedHashMapFactory);
		node.recurse(child -> labelCounts.increment(getLabel(child)));
		return labelCounts;
	}

	public void addSolution(Node node) {
		// TODO: This more correct solution leads to worse results... why?
		// Get all the labels of the final solution and keep only tuples with those labels
		Set<String> labels = extractAdditions(node).stream()
				.map(t -> t.x).collect(Collectors.toSet());
		additions.retainAll(additions.stream()
				.filter(t -> labels.contains(t.x))
				.collect(Collectors.toSet()));
	}

	public static String getLabel(Node node) {
		return node.rootPathString(MAX_RP_LENGTH);
	}

	@Override
	public String toString() {
		return String.join("\n", additions.stream().map(t -> t.toString()).toArray(String[]::new));
	}
}
