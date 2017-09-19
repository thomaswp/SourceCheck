package edu.isnap.eval.agreement;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Spring2017;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class TutorEdits {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		compareHints(Spring2017.instance);
//		verifyHints(Spring2017.instance);
	}

	public static void verifyHints(Dataset dataset) throws FileNotFoundException, IOException {
		ListMap<String, TutorEdit> edits = readTutorEdits(dataset);
		edits.values().forEach(l -> l.forEach(e -> e.verify()));
	}

	public static void compareHints(Dataset dataset) throws FileNotFoundException, IOException {
		ListMap<String, TutorEdit> assignmentMap = readTutorEdits(dataset);
		for (String assignmentID : assignmentMap.keySet()) {
			System.out.println("\n#---------> " + assignmentID + " <---------#\n");

			List<TutorEdit> edits = assignmentMap.get(assignmentID);
			Set<Integer> rowIDs = edits.stream().map(e -> e.rowID)
					.collect(Collectors.toSet());
			for (Integer rowID : rowIDs) {
				System.out.println("-------- " + rowID + " --------");

				ListMap<List<EditHint>, TutorEdit> givers = new ListMap<>();
				edits.stream()
				.filter(e -> e.rowID == rowID)
				.forEach(e -> givers.add(e.edits, e));

				for (List<EditHint> editSet : givers.keySet()) {
					System.out.println(String.join(" AND ",
							editSet.stream()
							.map(e -> e.toString())
							.collect(Collectors.toList())));
					for (TutorEdit tutorEdit : givers.get(editSet)) {
						System.out.printf("  %d/%s\n", tutorEdit.priority, tutorEdit.tutor);
					}
				}
				System.out.println();
			}
		}
	}

	public static ListMap<String,TutorEdit> readTutorEdits(Dataset dataset)
			throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(dataset.dataDir + "/handmade_hints.csv"),
				CSVFormat.DEFAULT.withHeader());

		ListMap<String, TutorEdit> edits = new ListMap<>();

		Map<String, Assignment> assignments = dataset.getAssignmentMap();
		Map<Integer, AttemptAction> hintActionMap = new HashMap<>();
		Set<String> loadedAssignments = new HashSet<>();

		for (CSVRecord record : parser) {
			int hintID = Integer.parseInt(record.get("hid"));
			String tutor = record.get("userID");
			int rowID = Integer.parseInt(record.get("rowID"));
			String assignmentID = record.get("assignmentID");
			String priorityString = record.get("priority");
			int priority;
			try {
				priority = Integer.parseInt(priorityString);
			} catch (NumberFormatException e) {
				if (!priorityString.equals("NULL")) {
					System.err.println("Unknown priority: " + priorityString);
				}
				continue;
			}

			if (loadedAssignments.add(assignmentID)) {
				loadAssignment(assignments, hintActionMap, assignmentID);
			}

			if (!hintActionMap.containsKey(rowID)) System.err.println("Missing hintID: " + rowID);
			Snapshot from = hintActionMap.get(rowID).lastSnapshot;

			String toXML = record.get("hintCode");
			Snapshot to = Snapshot.parse("TutorHint_" + hintID, toXML);

			TutorEdit edit = new TutorEdit(hintID, rowID, tutor, assignmentID, priority, from, to);
			edits.add(assignmentID, edit);
		}
		parser.close();

		return edits;
	}

	private static void loadAssignment(Map<String, Assignment> assignments,
			Map<Integer, AttemptAction> hintActionMap, String assignmentID) {
		Assignment assignment = assignments.get(assignmentID);
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
		for (AssignmentAttempt attempt : attempts.values()) {
			for (AttemptAction action : attempt) {
				if (HintSelection.isHintRow(action)) {
					hintActionMap.put(action.id, action);
				}
			}
		}
	}

	public static class TutorEdit {
		public final int hintID, rowID, priority;
		public final String tutor, assignmentID;
		public final Node from, to;
		public final List<EditHint> edits;

		public TutorEdit(int hintID, int rowID, String tutor, String assignmentID,
				int priority, Snapshot from, Snapshot to) {
			this.hintID = hintID;
			this.rowID = rowID;
			this.tutor = tutor;
			this.assignmentID = assignmentID;
			this.priority = priority;
			this.from = Agreement.toTree(from);
			this.to = Agreement.toTree(to);
			edits = Agreement.findEdits(this.from, this.to);
			if (edits.size() == 0 && this.from.equals(this.to)) {
				System.out.println("No edits for " + this);
			}
			Collections.sort(edits);
		}

		public boolean verify() {
			boolean pass = Agreement.testEditConsistency(from, to, true);
			if (!pass) {
				System.out.println("Failed: " + this);
			}
			return pass;
		}

		@Override
		public String toString() {
			return String.format("%s, row #%d, hint #%d", tutor, rowID, hintID);
		}
	}
}
