package edu.isnap.rating;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.CountMap;
import edu.isnap.ctd.util.map.ListMap;

public class TrainingDataset {

	private final ListMap<String, Trace> traces = new ListMap<>();

	public Collection<String> getAssignmentIDs() {
		return traces.keySet();
	}

	public List<Trace> getTraces(String assignmentID) {
		return traces.get(assignmentID);
	}

	private TrainingDataset() {

	}

	public static TrainingDataset fromDirectory(String name, String directory)
			throws IOException{
		TrainingDataset dataset = new TrainingDataset();
		File dirFile = new File(directory);
		if (!dirFile.exists()) {
			throw new FileNotFoundException("Directory does not exist: " +
					dirFile.getAbsolutePath());
		}
		for (File assignmentDir : dirFile.listFiles(f -> f.isDirectory())) {
			dataset.addAssignment(assignmentDir);
		}
		return dataset;
	}

	private void addAssignment(File directory) throws IOException {
		String assignment = directory.getName();
		for (File attemptDir : directory.listFiles(f -> f.isDirectory())) {
			Trace trace = new Trace(attemptDir.getName());
			for (File snapshotFile : attemptDir.listFiles()) {
				if (!snapshotFile.getName().toLowerCase().endsWith(".json")) {
					System.err.println("Unknown file: " + snapshotFile.getAbsolutePath());
					continue;
				}
				String source = new String(Files.readAllBytes(snapshotFile.toPath()));
				ASTNode node = ASTNode.parse(source);
				trace.add(node);
			}
			traces.add(assignment, trace);
		}
	}

	public void printAllSolutions(String assignmentID, RatingConfig config, boolean group) {
		CountMap<String> solutions = new CountMap<>();
		for (Trace trace : getTraces(assignmentID)) {
			ASTNode solution = trace.getSolution();
			if (solution == null) continue;
			String prettyPrint = solution.prettyPrint(true, config);
			if (!group) {
				System.out.println(trace.name);
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

	@SuppressWarnings("serial")
	public static class Trace extends ArrayList<ASTNode> {
		public final String name;

		public ASTNode getSolution() {
			if (isEmpty()) return null;
			return get(size() - 1);
		}

		public Trace(String name) {
			this.name = name;
		}
	}
}
