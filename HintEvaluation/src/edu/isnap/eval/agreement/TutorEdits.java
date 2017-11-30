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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringEscapeUtils;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.ctd.hint.RuleSet;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.Diff.ColorStyle;
import edu.isnap.ctd.util.NullSream;
import edu.isnap.ctd.util.Tuple;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.agreement.RateHints.GoldStandard;
import edu.isnap.eval.agreement.RateHints.HintOutcome;
import edu.isnap.eval.agreement.RateHints.HintSet;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class TutorEdits {

	public enum Validity {
		NoTutors(0), OneTutor(1), MultipleTutors(2), Consensus(3);

		public final int value;

		Validity(int value) {
			this.value = value;
		}
	}

	public enum Priority {
		Highest(1), High(2), Normal(3);

		public final int value;

		public int points() {
			return 4 - value;
		}

		Priority(int value) {
			this.value = value;
		}

		public static Priority fromInt(int value) {
			for (Priority priority : Priority.values()) {
				if (priority.value == value) return priority;
			}
			return null;
		}
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
//		compareHints(Fall2016.instance);
//		verifyHints(Fall2016.instance);

//		Map<String, HintSet> hintSets = readTutorHintSets(Spring2017.instance);
//		for (HintSet hintSet : hintSets.values()) {
//			System.out.println("------------ " + hintSet.name + " --------------");
//			RateHints.rate(standard, hintSet);
//		}

//		System.out.println("Fall");
//		testConsensus(Fall2016.instance, Spring2017.instance);
//		System.out.println("Spring");
//		testConsensus(Spring2017.instance, Fall2016.instance);

		highlightSQL(Fall2016.instance, Spring2017.instance);
//		highlightSQL(Spring2017.instance, Fall2016.instance);

//		exportConsensusHintRequests(Spring2017.instance, "consensus-gg-sq.csv",
//				"hint-eval", Spring2017.Squiral, Spring2017.GuessingGame1);
//		exportConsensusHintRequests(Fall2016.instance, "consensus-gg-sq.csv",
//				"hint-eval", Fall2016.Squiral, Fall2016.GuessingGame1);
	}

	@SuppressWarnings("unused")
	private static void testConsensus(Dataset testDataset, Dataset trainingDataset)
			throws FileNotFoundException, IOException {
		RuleSet.trace = NullSream.instance;
		GoldStandard standard = readConsensus(testDataset, "consensus-gg-sq.csv");
		HintConfig[] configs = new HintConfig[] {
				new SnapHintConfig(), // new SnapHintConfig(), new SnapHintConfig()
		};
//		configs[0].createSubedits = true;
//		configs[0].useRulesToFilter = false; configs[0].valuesPolicy = ValuesPolicy.IgnoreAll;
//		configs[1].useRulesToFilter = true; configs[1].valuesPolicy = ValuesPolicy.IgnoreAll;
//		configs[2].useRulesToFilter = true; configs[2].valuesPolicy = ValuesPolicy.MappedOnly;

		for (int i = 0; i < configs.length; i++) {
			HighlightHintSet hintSet = new HighlightHintSet(trainingDataset.getName(),
					trainingDataset, standard.getHintRequests(), configs[i]);
//			RateHints.rate(standard, hintSet);
			hintSet.toTutorEdits().forEach(e -> System.out.println(
					e.toSQLInsert("handmade_hints", "highlight", 20000, false, true)));
		}
	}

	private static void highlightSQL(Dataset testDataset, Dataset trainingDataset)
			throws FileNotFoundException, IOException {
		RuleSet.trace = NullSream.instance;
		GoldStandard standard = readConsensus(testDataset, "consensus-gg-sq.csv");
		HintConfig config = new SnapHintConfig();

		int offset = 20000;

		Spreadsheet spreadsheet = new Spreadsheet();
		HighlightHintSet hintSet = new HighlightHintSet(trainingDataset.getName(),
				trainingDataset, standard.getHintRequests(), config);
		hintSet.toTutorEdits().forEach(edit -> {
			System.out.println(edit.toSQLInsert(
					"handmade_hints", "highlight", offset, false, true));

			spreadsheet.newRow();
			spreadsheet.put("Assignment ID", edit.assignmentID);
			spreadsheet.put("Row ID", edit.rowID);
			spreadsheet.put("Hint ID", edit.hintID + offset);

			spreadsheet.put("Valid (0-1)", null);
			spreadsheet.put("Priority (1-3)", null);

			spreadsheet.put("Hint", edit.editsString(false));
		});
		spreadsheet.write(testDataset.analysisDir() + "/highlight-hints.csv");
	}

	public static void exportConsensusHintRequests(Dataset dataset, String path, String folder,
			Assignment... assignments)
			throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(dataset.dataDir + "/" + path),
				CSVFormat.DEFAULT.withHeader());
		Set<Integer> ids = new HashSet<>();
		for (CSVRecord record : parser) {
			ids.add(Integer.parseInt(record.get("Row ID")));
		}
		parser.close();

		Set<Integer> collectedIDs = new HashSet<>();
		for (Assignment assignment : assignments) {
			if (assignment.dataset != dataset) {
				throw new RuntimeException("Assignment must be from given dataset!");
			}
			Set<String> stopped = new HashSet<>();
			Function<AssignmentAttempt, Predicate<AttemptAction>> actionFilter =
					attempt -> action -> {
				if (ids.contains(action.id)) {
					stopped.add(attempt.id);
					collectedIDs.add(action.id);
					return true;
				}
				return !stopped.contains(attempt.id);
			};
			JsonAST.exportAssignmentTraces(assignment, folder + "/requests",
					attempt -> attempt.rows.rows.stream().anyMatch(a -> ids.contains(a.id)),
					actionFilter,
					attempt -> attempt.rows.rows.stream()
									.filter(a -> ids.contains(a.id))
									.map(a -> String.valueOf(a.id))
									.findAny().orElse(attempt.id));
			JsonAST.exportAssignmentTraces(assignment, folder + "/training",
					attempt -> !stopped.contains(attempt.id) &&
						attempt.grade != null && attempt.grade.average() == 1,
					attempt -> action -> true,
					attempt -> attempt.id);
		}

		if (collectedIDs.size() != ids.size()) {
			ids.removeAll(collectedIDs);
			throw new RuntimeException("Missing row IDs: " + ids);
		}
		JsonAST.write(dataset.dataDir + "/export/" + folder + "/values.txt",
				JsonAST.flushWrittenValues());
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
				.forEach(e -> givers.add(RateHints.normalizeNewValues(e.from, e.to), e));

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
					int priorityMatches = 0;
					for (TutorEdit tutorEdit : tutorEdits) {
						if (tutorEdit.priority == firstEdit.priority) priorityMatches++;
						System.out.printf("  %d/%s: #%d\n",
								tutorEdit.priority.value, tutorEdit.tutor, tutorEdit.hintID);
					}

					sql.append(firstEdit.toSQLInsert(
							"handmade_hints", "consensus", hintOffset,
							priorityMatches == tutors.size(), true));
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

	public static Map<String, HintSet> readTutorHintSets(Dataset dataset)
			throws FileNotFoundException, IOException {
		Map<String, HintSet> hintSets = new HashMap<>();
		ListMap<String, TutorEdit> allEdits = readTutorEdits(dataset);
		for (List<TutorEdit> list : allEdits.values()) {
			for (TutorEdit edit : list) {
				if (edit.tutor.equals("consensus")) continue;
				HintSet set = hintSets.get(edit.tutor);
				if (set == null) {
					hintSets.put(edit.tutor, set = new HintSet(edit.tutor));
				}
				set.add(edit.rowID, edit.toOutcome());
			}
		}
		return hintSets;
	}

	public static GoldStandard readConsensus(Dataset dataset, String... consensusPaths)
			throws FileNotFoundException, IOException {
		Map<Integer, Tuple<Validity, Priority>> consensus =
				readConsensusSpreadsheet(dataset, consensusPaths);
		ListMap<String, TutorEdit> allEdits = readTutorEdits(dataset);
		ListMap<String, TutorEdit> consensusEdits = new ListMap<>();
		for (String assignmentID : allEdits.keySet()) {
			List<TutorEdit> list = allEdits.get(assignmentID);
			List<TutorEdit> keeps = new ArrayList<>();
			for (TutorEdit hint : list) {
				if (!hint.tutor.equals("consensus")) continue;
				Tuple<Validity, Priority> ratings = consensus.get(hint.hintID);
				if (ratings == null) {
					throw new RuntimeException("No consensus rating for: " + hint.hintID);
				}
				if (ratings.x == Validity.NoTutors) continue;
				hint.validity = ratings.x;
				hint.priority = ratings.y;
				keeps.add(hint);
			}
			consensusEdits.put(assignmentID, keeps);
		}
		return new GoldStandard(consensusEdits);
	}

	private static Map<Integer, Tuple<Validity, Priority>> readConsensusSpreadsheet(Dataset dataset,
			String[] paths) throws FileNotFoundException, IOException {
		Map<Integer, Tuple<Validity, Priority>> map = new HashMap<>();
		for (String path : paths) {
			CSVParser parser = new CSVParser(new FileReader(dataset.dataDir + "/" + path),
					CSVFormat.DEFAULT.withHeader());
			for (CSVRecord row : parser) {
				int id = Integer.parseInt(row.get("Hint ID"));
				boolean consensus = Double.parseDouble(row.get("Consensus (Validity)")) == 1;
				int priority = consensus ? Integer.parseInt(row.get("Consensus (Priority)")) : 0;
				int v1 = Integer.parseInt(row.get("V1"));
				Validity validity;
				if (consensus) {
					validity = Validity.Consensus;
				} else if (v1 > 1) {
					validity = Validity.MultipleTutors;
				} else if (v1 > 0) {
					validity = Validity.OneTutor;
				} else {
					validity = Validity.NoTutors;
				}
				map.put(id, new Tuple<>(validity, Priority.fromInt(priority)));
			}
			parser.close();
		}
		return map;
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
			Priority priority = null;
			try {
				int priorityValue = Integer.parseInt(priorityString);
				priority = Priority.fromInt(priorityValue);
			} catch (NumberFormatException e) {
				if (!priorityString.equals("NULL")) {
					System.err.println("Unknown priority: " + priorityString);
				}
			}

			if (loadedAssignments.add(assignmentID)) {
				loadAssignment(assignments, hintActionMap, assignmentID);
			}

			if (!hintActionMap.containsKey(rowID)) System.err.println("Missing hintID: " + rowID);
			Snapshot from = hintActionMap.get(rowID).lastSnapshot;

			String toXML = record.get("hintCode");
			// Skip empty hints (they may exist if no hint is appropriate for a snapshot)
			if (toXML.equals("NULL")) continue;

			Snapshot to = Snapshot.parse(from.name, toXML);

			TutorEdit edit = new TutorEdit(hintID, rowID, tutor, assignmentID, from, to, toXML);
			edit.priority = priority;
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
		public final int hintID, rowID;
		public final String tutor, assignmentID;
		public final Node from, to;
		public final String toXML;
		public final List<EditHint> edits;

		public Validity validity;
		public Priority priority;

		public TutorEdit(int hintID, int rowID, String tutor, String assignmentID, Snapshot from,
				Snapshot to, String toXML) {
			this(hintID, rowID, tutor, assignmentID, Agreement.toTree(from), Agreement.toTree(to),
					toXML);
		}

		public TutorEdit(int hintID, int rowID, String tutor, String assignmentID, Node from,
				Node to, String toXML) {
			this.hintID = hintID;
			this.rowID = rowID;
			this.tutor = tutor;
			this.assignmentID = assignmentID;
			this.from = from;
			this.to = to;
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
			return editsToString(edits, useANSI);
		}

		public static String editsToString(List<EditHint> edits, boolean useANSI) {
			ColorStyle oldStyle = Diff.colorStyle;
			Diff.colorStyle = useANSI ? ColorStyle.ANSI : ColorStyle.None;
			String editsString = String.join(" AND\n",
					edits.stream()
					.map(e -> e.toString())
					.collect(Collectors.toList()));
			Diff.colorStyle = oldStyle;
			return editsString;
		}

		public String toSQLInsert(String table, String user, int hintIDOffset,
				boolean addPriority, boolean addDate) {
			return String.format("INSERT INTO `%s` (`hid`, `userID`, `rowID`, `trueAssignmentID`, "
					+ "`priority`, `hintCode`, `hintEdits`, `updatedTime`) "
					+ "VALUES (%d, '%s', %d, '%s', %s, '%s', '%s', %s);",
					table, hintID + hintIDOffset, user, rowID, assignmentID,
					addPriority ? String.valueOf(priority.value) : "NULL",
					StringEscapeUtils.escapeSql(toXML),
					StringEscapeUtils.escapeSql(HintJSON.hintArray(edits).toString()),
					addDate ? "NOW()" : "NULL");
		}

		public HintOutcome toOutcome() {
			return new HintOutcome(to, rowID, priority == null ? 0 : (1.0 / priority.value), edits);
		}
	}
}
