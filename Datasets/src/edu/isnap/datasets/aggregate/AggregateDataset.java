package edu.isnap.datasets.aggregate;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;

public abstract class AggregateDataset extends Dataset {

	public final Dataset[] datasets;

	public AggregateDataset(String dataDir, Dataset... datasets) {
		super(new Date(Arrays.stream(datasets).map(
				dataset -> dataset.start.getTime()).reduce(Long::min).get()), dataDir);
		this.datasets = datasets;
	}

	protected static AggregateAssignment createAssignment(AggregateDataset instance, String name) {
		List<Assignment> assignments = new LinkedList<>();
		for (Dataset dataset : instance.datasets) {
			assignments.addAll(Arrays.stream(dataset.all()).filter(
					assignment -> assignment.name.equals(name)).collect(Collectors.toList()));
		}
		return new AggregateAssignment(instance, name, assignments);
	}
}
