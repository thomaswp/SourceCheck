package edu.isnap.eval.agreement;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringEscapeUtils;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.util.Spreadsheet;
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

		int hintOffset = 10000;

		Set<String> tutors =  assignmentMap.values().stream()
				.flatMap(List::stream).map(e -> e.tutor)
				.collect(Collectors.toSet());
		Map<String, Spreadsheet> tutorSpreadsheets = tutors.stream()
				.collect(Collectors.toMap(t -> t, t -> new Spreadsheet()));
		StringBuilder sql = new StringBuilder();

		for (String assignmentID : assignmentMap.keySet()) {
			System.out.println("\n#---------> " + assignmentID + " <---------#\n");

			List<TutorEdit> edits = assignmentMap.get(assignmentID);
			Set<Integer> rowIDs = new TreeSet<>(edits.stream().map(e -> e.rowID)
					.collect(Collectors.toSet()));

			for (Integer rowID : rowIDs) {
				System.out.println("-------- " + rowID + " --------");

				ListMap<Node, TutorEdit> givers = new ListMap<>();
				edits.stream()
				.filter(e -> e.rowID == rowID)
				.forEach(e -> givers.add(e.to, e));

				Node from = givers.values().stream().findFirst().get().get(0).from;
				String fromPP = from.prettyPrint(true);
				System.out.println(fromPP);

				List<Node> keys = new ArrayList<>(givers.keySet());
				// Sort by how many raters gave the hint
				keys.sort((n1, n2) -> -Integer.compare(
						givers.get(n1).size(), givers.get(n2).size()));
				for (Node to : keys) {
//					System.out.println(Diff.diff(fromPP, to.prettyPrint(true), 1));
					List<TutorEdit> tutorEdits = givers.get(to);
					TutorEdit firstEdit = tutorEdits.get(0);
					String editsString = firstEdit.editsString(true);
					System.out.println(editsString);
					for (TutorEdit tutorEdit : tutorEdits) {
						System.out.printf("  %d/%s: #%d\n",
								tutorEdit.priority, tutorEdit.tutor, tutorEdit.hintID);
					}

					sql.append(firstEdit.toSQLInsert(
							"handmade_hints", "consensus", hintOffset));
					sql.append("\n");

					Map<String, TutorEdit> givingTutors = tutorEdits.stream()
							.collect(Collectors.toMap(e -> e.tutor, e -> e));
					String editsStringNoANSI = firstEdit.editsString(false);
					for (String tutor : tutorSpreadsheets.keySet()) {
						TutorEdit edit = givingTutors.get(tutor);

						Spreadsheet spreadsheet = tutorSpreadsheets.get(tutor);
						spreadsheet.newRow();
						spreadsheet.put("Assignment ID", firstEdit.assignmentID);
						spreadsheet.put("Row ID", firstEdit.rowID);
						spreadsheet.put("Hint ID", firstEdit.hintID + hintOffset);

						spreadsheet.put("Valid (0-1)", edit == null ? null : 1);
						spreadsheet.put("Priority (1-3)", edit == null ? null : edit.priority);

						spreadsheet.put("Hint", editsStringNoANSI);
					}
				}
				System.out.println();
			}
		}

		String dir = String.format("%s/tutor-hints/%d/", dataset.analysisDir(), hintOffset);
		for (String tutor : tutorSpreadsheets.keySet()) {
			Spreadsheet spreadsheet = tutorSpreadsheets.get(tutor);
			spreadsheet.write(dir + tutor + ".csv");
		}
		JsonAST.write(dir + "consensus.sql", sql.toString());
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
			String assignmentID = record.get("trueAssignmentID");
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
			Snapshot to = Snapshot.parse(from.name, toXML);

			TutorEdit edit = new TutorEdit(hintID, rowID, tutor, assignmentID, priority, from, to,
					toXML);
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
		public final String toXML;
		public final List<EditHint> edits;

		public TutorEdit(int hintID, int rowID, String tutor, String assignmentID,
				int priority, Snapshot from, Snapshot to, String toXML) {
			this.hintID = hintID;
			this.rowID = rowID;
			this.tutor = tutor;
			this.assignmentID = assignmentID;
			this.priority = priority;
			this.from = Agreement.toTree(from);
			// TODO: Need to somehow standardize the creation of new values (e.g. variables)
			this.to = Agreement.toTree(to);
			this.toXML = toXML;
			edits = Agreement.findEdits(this.from.copy(), this.to.copy(), true);
			if (edits.size() == 0 && this.from.equals(this.to)) {
				System.out.println("No edits for " + this);
			}
		}

		public boolean verify() {
			boolean pass = Agreement.testEditConsistency(from, to, true, true);
			if (!pass) {
				System.out.println("Failed: " + this);
			}
			return pass;
		}

		@Override
		public String toString() {
			return String.format("%s, row #%d, hint #%d", tutor, rowID, hintID);
		}

		private String editsString(boolean useANSI) {
			boolean oldANSI = Diff.USE_ANSI_COLORS;
			Diff.USE_ANSI_COLORS = useANSI;
			String editsString = String.join(" AND\n",
					edits.stream()
					.map(e -> e.toString())
					.collect(Collectors.toList()));
			Diff.USE_ANSI_COLORS = oldANSI;
			return editsString;
		}

		public String toSQLInsert(String table, String user, int hintIDOffset) {
			return String.format("INSERT INTO `%s` (`hid`, `userID`, `rowID`, `trueAssignmentID`, "
					+ "`hintCode`, `hintEdits`) "
					+ "VALUES (%d, '%s', %d, '%s', '%s', '%s');",
					table, hintID + hintIDOffset, user, rowID, assignmentID,
					StringEscapeUtils.escapeSql(toXML),
					StringEscapeUtils.escapeSql(HintJSON.hintArray(edits).toString()));
		}
	}
}
