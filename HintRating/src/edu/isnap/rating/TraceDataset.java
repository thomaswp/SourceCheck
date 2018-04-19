package edu.isnap.rating;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.util.map.ListMap;

public abstract class TraceDataset {

	protected final ListMap<String, Trace> tracesMap = new ListMap<>();

	public Collection<String> getAssignmentIDs() {
		return tracesMap.keySet();
	}

	public List<Trace> getTraces(String assignmentID) {
		return Collections.unmodifiableList(tracesMap.get(assignmentID));
	}

	protected static void addDirectory(TraceDataset dataset, String name, String directory)
			throws IOException{
		File dirFile = new File(directory);
		if (!dirFile.exists()) {
			throw new FileNotFoundException("Directory does not exist: " +
					dirFile.getAbsolutePath());
		}
		for (File assignmentDir : dirFile.listFiles(f -> f.isDirectory())) {
			dataset.addAssignment(assignmentDir);
		}
		dataset.tracesMap.values().forEach(Collections::sort);
	}

	private void addAssignment(File directory) throws IOException {
		String assignmentID = directory.getName();
		for (File attemptDir : directory.listFiles(f -> f.isDirectory())) {
			Trace trace = new Trace(attemptDir.getName(), assignmentID);
			for (File snapshotFile : attemptDir.listFiles()) {
				if (!snapshotFile.getName().toLowerCase().endsWith(".json")) {
					System.err.println("Unknown file: " + snapshotFile.getAbsolutePath());
					continue;
				}
				String source = new String(Files.readAllBytes(snapshotFile.toPath()));
				ASTNode node = ASTNode.parse(source);
				trace.add(node);
			}
			tracesMap.add(assignmentID, trace);
		}
	}
}
