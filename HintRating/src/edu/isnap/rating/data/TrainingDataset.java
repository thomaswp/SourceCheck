package edu.isnap.rating.data;

import java.io.IOException;

import edu.isnap.node.ASTNode;
import edu.isnap.rating.RatingConfig;
import edu.isnap.util.map.CountMap;

public class TrainingDataset extends TraceDataset {

	private TrainingDataset(String name) {
		super(name);
	}

	@Deprecated
	public static TrainingDataset fromDirectory(String name, String directory) throws IOException {
		TrainingDataset dataset = new TrainingDataset(name);
		dataset.addDirectory(directory);
		return dataset;
	}

	public static TrainingDataset fromSpreadsheet(String name, String path) throws IOException {
		TrainingDataset dataset = new TrainingDataset(name);
		dataset.addSpreadsheet(path);
		return dataset;
	}

	public void printAllSolutions(String assignmentID, RatingConfig config, boolean group) {
		CountMap<String> solutions = new CountMap<>();
		for (Trace trace : getTraces(assignmentID)) {
			ASTNode solution = trace.getFinalSnapshot();
			if (solution == null) continue;
			String prettyPrint = solution.prettyPrint(true, config);
			if (!group) {
				System.out.println(trace.id);
				System.out.println(prettyPrint);
				System.out.println("----------------");
				continue;
			}
			solutions.increment(prettyPrint);
		}
		if (!group) return;
		for (String solution : solutions.keySet()) {
			System.out.println(solutions.getCount(solution));
			System.out.println(solution);
			System.out.println("----------------");
		}
	}
}
