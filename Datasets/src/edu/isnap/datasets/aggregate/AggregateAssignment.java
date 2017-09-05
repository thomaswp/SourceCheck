package edu.isnap.datasets.aggregate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.parser.SnapParser.Filter;
import edu.isnap.parser.Store.Mode;

public class AggregateAssignment extends ConfigurableAssignment {

	public final List<Assignment> assignments;

	public AggregateAssignment(AggregateDataset dataset, String name,
			List<Assignment> assignments) {
		super(dataset, name,
				Collections.min(assignments.stream().map(assignment -> assignment.start)
						.collect(Collectors.toList())), false);
		this.assignments = Collections.unmodifiableList(assignments);
	}

	@Override
	public Map<String, AssignmentAttempt> load(Mode mode, boolean snapshotsOnly,
			boolean addMetadata, Filter... filters) {

		TreeMap<String, AssignmentAttempt> attempts = new TreeMap<>();
		assignments.stream().map(assignment -> assignment.load(mode, snapshotsOnly, addMetadata))
			.forEach((a) -> attempts.putAll(a));
		return attempts;
	}

	@Override
	public HintConfig getConfig() {
		return ((AggregateDataset) dataset).getDefaultHintConfig();
	}

}
