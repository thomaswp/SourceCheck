package edu.isnap.ctd.hint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.MapFactory;

public class Ordering {

	// TODO: config
	private final static int MAX_RP_LENGTH = 3;

	private final LinkedHashSet<Addition> additions = new LinkedHashSet<>();
	private final ArrayList<Addition> listWrapper = new ArrayList<>();

	public Ordering() {

	}

	public Ordering(List<Node> history) {
		if (history.size() == 0) return;
		history.forEach(this::addSnapshot);
		addSolution(history.get(history.size() - 1));
	}

	public int getOrder(String label, int count) {
		return getOrder(new Addition(label, count));
	}

	public int getOrder(Addition item) {

		if (listWrapper.size() != additions.size()) {
			listWrapper.clear();
			listWrapper.addAll(additions);
		}
		return listWrapper.indexOf(item);
	}

	public List<Addition> getPrecedingAdditions(Addition item) {
		int index = getOrder(item);
		List<Addition> list = new ArrayList<>();
		int i = 0;
		for (Addition addition : additions) {
			if (i++ >= index) break;
			list.add(addition);
		}
		return list;
	}

	public void addSnapshot(Node node) {
		extractAdditions(node).forEach(additions::add);
	}

	private List<Addition> extractAdditions(Node node) {
		CountMap<String> labelCounts = countLabels(node);
		return labelCounts.keySet().stream()
				// For each label/count, add <label,c> for c in {1..count},
				// since having 2 of something means you also have 1 of it
				.flatMap(k -> IntStream.range(1, labelCounts.get(k) + 1)
						.mapToObj(i -> new Addition(k, i)))
				.collect(Collectors.toList());
	}

	public static CountMap<String> countLabels(Node node) {
		CountMap<String> labelCounts = new CountMap<>(MapFactory.LinkedHashMapFactory);
		node.recurse(child -> labelCounts.increment(getLabel(child)));
		return labelCounts;
	}

	public void addSolution(Node node) {
		// Get all the labels of the final solution and keep only additions with those labels
		Set<String> labels = extractAdditions(node).stream()
				.map(t -> t.label).collect(Collectors.toSet());
		additions.retainAll(additions.stream()
				.filter(t -> labels.contains(t.label))
				.collect(Collectors.toSet()));
	}

	public static String getLabel(Node node) {
		return node.rootPathString(MAX_RP_LENGTH);
	}

	@Override
	public String toString() {
		return String.join("\n", additions.stream().map(t -> t.toString()).toArray(String[]::new));
	}

	public static class Addition {
		public final String label;
		public final int count;

		public Addition(String label, int count) {
			this.label = label;
			this.count = count;
		}

		@Override
		public int hashCode() {
			return new HashCodeBuilder(11, 3)
					.append(label)
					.append(count)
					.toHashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Addition other = (Addition) obj;
			return new EqualsBuilder()
					.append(label, other.label)
					.append(count, other.count)
					.isEquals();
		}
	}
}
