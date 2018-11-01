package edu.isnap.sourcecheck.priority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import edu.isnap.node.Node;
import edu.isnap.util.map.CountMap;
import edu.isnap.util.map.MapFactory;

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
		node.recurse(child -> {
			String label = getLabel(child);
			if (label != null) labelCounts.increment(getLabel(child));
		});
		return labelCounts;
	}

	public void addSolution(Node node) {
		// Get all the labels of the final solution and keep only additions with those labels
		Set<String> labels = extractAdditions(node).stream()
				.map(t -> t.label).collect(Collectors.toSet());
		additions.retainAll(additions.stream()
				.filter(t -> labels.contains(t.label))
				.collect(Collectors.toSet()));
//		int i = 0;
//		for (Addition addition : additions) {
//			System.out.printf("%s,%s,%d\n",
//					node.id, addition, i++);
//		}
	}

	private static boolean ignore(String type) {
		// TODO: config
		return "literal".equals(type) || "script".equals(type);
	}

	public static String getLabel(Node node) {
		if (ignore(node.type())) return null;
		List<String> path = new LinkedList<>();
		Node n = node;
		int length = 0;
		while (n != null && length < MAX_RP_LENGTH) {
			String type = n.type();
			if (!ignore(type)) {
				path.add(0, type);
				length++;
			}
			n = n.parent;
		}
		return String.join("->", path);
	}

	@Override
	public String toString() {
		return String.join("\n", additions.stream().map(t -> t.toString()).toArray(String[]::new));
	}

	public static class Addition {
		public final String label;
		public final int count;

		@SuppressWarnings("unused")
		private Addition() {
			this(null, 0);
		}

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

		@Override
		public String toString() {
			return String.format("{%s:%d}", label, count);
		}
	}

	public static class OrderMatrix {

		private final List<Addition> additions;
		private final double[][] matrix;

		public List<Addition> additions() {
			return Collections.unmodifiableList(additions);
		}

		public double getPercOrdered(int beforeIndex, int afterIndex) {
			if (beforeIndex < 0 || beforeIndex >= additions.size()) return 0;
			if (afterIndex < 0 || afterIndex >= additions.size()) return 0;
			return matrix[beforeIndex][afterIndex];
		}

		@SuppressWarnings("unused")
		private OrderMatrix() {
			additions = null;
			matrix = null;
		}

		public OrderMatrix(Collection<Ordering> orderings, double frequencyThreshhold) {
			CountMap<Addition> additionCounts = new CountMap<>();
			for (Ordering ordering : orderings) {
				ordering.additions.forEach(addition -> additionCounts.increment(addition));
			}
			int minCount = (int) (orderings.size() * frequencyThreshhold);
			additions = additionCounts.keySet().stream()
				.filter(addition -> additionCounts.getCount(addition) >= minCount)
				.collect(Collectors.toList());

			int n = additions.size();
			matrix = new double[n][n];
			for (int i = 0; i < n; i++) {
				for (int j = i + 1; j < n; j++) {
					Addition a = additions.get(i);
					Addition b = additions.get(j);
					int count = 0;
					int aFirst = 0;
					for (Ordering ordering : orderings) {
						int orderA = ordering.getOrder(a);
						int orderB = ordering.getOrder(b);
						if (orderA == -1 || orderB == -1) continue;
						count++;
						if (orderA < orderB) aFirst++;
					}
					matrix[i][j] = (double) aFirst / count;
					matrix[j][i] = 1 - matrix[i][j];
				}
			}
		}
	}
}
