package edu.isnap.sourcecheck.priority;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.isnap.hint.IDataModel;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.priority.Ordering.Addition;
import edu.isnap.util.map.CountMap;

public class OrderingModel implements IDataModel {

	private final Map<Node, Ordering> nodeOrderings = new IdentityHashMap<>();

	@Override
	public void addTrace(String id, List<Node> trace) {
		if (trace.size() == 0) return;
		nodeOrderings.put(trace.get(trace.size() - 1), new Ordering(trace));
	}

	@Override
	public void finished() {
		// TODO: config
		createMatrix(nodeOrderings.values(), 0.3);
	}

	private List<Addition> additions;
	private double[][] matrix;

	public List<Addition> additions() {
		return Collections.unmodifiableList(additions);
	}

	public double getPercOrdered(int beforeIndex, int afterIndex) {
		if (beforeIndex < 0 || beforeIndex >= additions.size()) return 0;
		if (afterIndex < 0 || afterIndex >= additions.size()) return 0;
		return matrix[beforeIndex][afterIndex];
	}

	private void createMatrix(Collection<Ordering> orderings, double frequencyThreshhold) {
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
