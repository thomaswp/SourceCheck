package edu.isnap.eval.agreement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.RuleSet;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.Diff.ColorStyle;
import edu.isnap.ctd.util.NullStream;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.agreement.EditComparer.EditDifference;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class EDM2017 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		EditHint.useValues = false;
		RuleSet.trace = NullStream.instance;

//		compareEdits(Spring2017.instance, Fall2016.Squiral, Fall2016.GuessingGame1);
//		compareEdits(Spring2017.instance, CSC200Solutions.Squiral, CSC200Solutions.GuessingGame1);
//		compareEdits(Spring2017.instance, Spring2016.Squiral, Spring2016.GuessingGame1);

		analyzeEdits(Spring2017.instance, Spring2016.Squiral, Spring2016.GuessingGame1);

//		testExtractEdits(Spring2017.instance);
	}

	public static void testLogEdits() {
		Map<String, AssignmentAttempt> attempts = Fall2016.GuessingGame1.load(Mode.Use, true, true,
				new SnapParser.SubmittedOnly());
		int i = 0;
		for (AssignmentAttempt attempt : attempts.values()) {
			Snapshot last = null;
			for (AttemptAction action : attempt) {
				if (last != null) {// && action.id == 222243) {
					if (!Agreement.testEditConsistency(last, action.snapshot, false)) {
						System.out.println(action.id);
					}
				}
				last = action.snapshot;
			}
			if (i++ >= 10) break;
		}
	}

	private static void readEdits(Map<String, Map<String, List<EditHint>>> expertMap,
			Map<String, Map<String, List<EditHint>>> comparisonMap, Map<String, Node> nodeMap,
			Map<String, String> assignmentMap, Dataset testDataset,
			Assignment... trainingAssignments) throws IOException, FileNotFoundException {

		CSVParser parser = new CSVParser(new FileReader(new File(testDataset.dataDir, "hints.csv")),
				CSVFormat.DEFAULT.withHeader());

		HashMap<String, HintHighlighter> highlighters = new HashMap<>();
		for (Assignment assignment : trainingAssignments) {
			HintConfig config = ConfigurableAssignment.getConfig(assignment);
			config.useRulesToFilter = false;
			SnapHintBuilder builder = new SnapHintBuilder(assignment);
			HintMap hintMap = builder.buildGenerator(Mode.Ignore, 1).hintMap;
			highlighters.put(assignment.name, new HintHighlighter(hintMap));
		}

		// Since sometimes assignments are incorrect in the logs, we have to redirect prequel
		// assignments
		// TODO: This is neither robust nor complete. The only real solution is search through
		// all assignments for one with the given row, since the database just doesn't have the
		// complete information
		for (Assignment assignment : testDataset.all()) {
			if (assignment.prequel != null) {
				if (!highlighters.containsKey(assignment.name)) {
					HintHighlighter highlighter = highlighters.get(assignment.prequel.name);
					if (highlighter != null) {
						highlighters.put(assignment.name, highlighter);
					}
				}
			}
		}

		for (CSVRecord record : parser) {

			String codeXML = record.get("code");
			String h1CodeXML = record.get("h1Code");
			String h2CodeXML = record.get("h2Code");

			String userID = record.get("userID");
			String rowID = record.get("rowID");
			String assignmentID = record.get("assignmentID");

			Snapshot code = Snapshot.parse("code", codeXML);
			Snapshot h1Code = Snapshot.parse("h1", h1CodeXML);
			Snapshot h2Code = Snapshot.parse("h2", h2CodeXML);

			HintHighlighter highlighter = highlighters.get(assignmentID);
			highlighter.trace = NullStream.instance;

			Node fromNode = nodeMap.get(rowID);
			if (fromNode == null) {
				nodeMap.put(rowID, fromNode = Agreement.toTree(code));
				assignmentMap.put(rowID, assignmentID);
			}

			Node toNodeIdeal = Agreement.toTree(h1Code);
			Node toNodeAll = Agreement.toTree(h2Code);

			Map<String, List<EditHint>> expertRowMap = expertMap.get(rowID);
			if (expertRowMap == null) {
				expertMap.put(rowID, expertRowMap = new TreeMap<>());
			}

			Map<String, List<EditHint>> comparisonRowMap = comparisonMap.get(rowID);
			if (comparisonRowMap == null) {
				comparisonMap.put(rowID, comparisonRowMap = new TreeMap<>());
			}

			List<EditHint> idealEdits = Agreement.findEdits(fromNode, toNodeIdeal, false);
			List<EditHint> allEdits = Agreement.findEdits(fromNode, toNodeAll, false);
			expertRowMap.put(userID + "-ideal", idealEdits);
			expertRowMap.put(userID + "-all", allEdits);
			comparisonRowMap.put(userID + "-all", allEdits);


			if (!expertRowMap.containsKey("highlight")) {
				List<EditHint> edits = highlighter.highlightWithPriorities(fromNode);
				comparisonRowMap.put("highlight", edits);
//				comparisonRowMap.put("highlight-rted", highlighter.highlightRTED(fromNode));
//				comparisonRowMap.put("highlight-sed", highlighter.highlightStringEdit(fromNode));
			}
		}

		parser.close();
	}

	protected static void compareEdits(Dataset testDataset, Assignment... trainingAssignments)
			throws FileNotFoundException, IOException {

		Map<String, Map<String, List<EditHint>>> expertMap = new LinkedHashMap<>();
		Map<String, Map<String, List<EditHint>>> comparisonMap = new LinkedHashMap<>();
		Map<String, Node> nodeMap = new HashMap<>();
		Map<String, String> assignmentMap = new HashMap<>();

		readEdits(expertMap, comparisonMap, nodeMap, assignmentMap, testDataset,
				trainingAssignments);

		for (Assignment assignment : trainingAssignments) {
			System.out.println(assignment);
			System.out.println("--------------");

			HashMap<String, EditDifference> comps = new LinkedHashMap<>();

			ListMap<String, Integer> totalCompEdits = new ListMap<>();

			for (String row : expertMap.keySet()) {
				if (!assignmentMap.get(row).equals(assignment.name)) continue;

	//			System.out.println(row);
				Map<String, List<EditHint>> expertRowMap = expertMap.get(row);
				Map<String, List<EditHint>> comparisonRowMap = comparisonMap.get(row);

				Node node = nodeMap.get(row);
				for (String keyA : expertRowMap.keySet()) {
					List<EditHint> editsA = expertRowMap.get(keyA);
					String typeA = getType(keyA);
					for (String keyB : comparisonRowMap.keySet()) {
						if (keyB.startsWith(keyA.substring(0, 4))) continue;
						List<EditHint> editsB = comparisonRowMap.get(keyB);
						String typeB = getType(keyB);
						String key = typeA + " vs " + typeB;
						EditDifference diff = EditComparer.compare(node, editsA, editsB);
						EditDifference last = comps.get(key);
						comps.put(key, EditDifference.sum(diff, last));
					}
				}

				for (String keyB : comparisonRowMap.keySet()) {
					totalCompEdits.add(keyB, comparisonRowMap.get(keyB).size());
				}
			}

			for (String key : totalCompEdits.keySet()) {
				System.out.println(key + ": " + totalCompEdits.get(key));
			}

			File file = new File(assignment.analysisDir(), "/agreement.csv");
			file.getParentFile().mkdirs();
			CSVPrinter printer = new CSVPrinter(
					new FileWriter(file),
					CSVFormat.DEFAULT.withHeader(EditDifference.CSV_HEADER));
			for (String key : comps.keySet()) {
				String[] parts = key.split(" vs ");
				comps.get(key).print(parts[0], parts[1]);
				comps.get(key).printCSV(parts[0], parts[1], printer);
				System.out.println();
			}
			printer.close();
		}
	}

	protected static void analyzeEdits(Dataset testDataset, Assignment... trainingAssignments)
			throws FileNotFoundException, IOException {

		Map<String, Map<String, List<EditHint>>> expertMap = new LinkedHashMap<>();
		Map<String, Map<String, List<EditHint>>> comparisonMap = new LinkedHashMap<>();
		Map<String, Node> nodeMap = new HashMap<>();
		Map<String, String> assignmentMap = new HashMap<>();

		Spreadsheet spreadsheet = new Spreadsheet();

		readEdits(expertMap, comparisonMap, nodeMap, assignmentMap, testDataset,
				trainingAssignments);

		for (Assignment assignment : trainingAssignments) {
			System.out.println(assignment);

			for (String row : expertMap.keySet()) {
				if (!assignmentMap.get(row).equals(assignment.name)) continue;

	//			System.out.println(row);
				Map<String, List<EditHint>> expertRowMap = expertMap.get(row);

				Set<EditHint> highlightEdits =
						new HashSet<>(comparisonMap.get(row).get("highlight"));
				Set<EditHint> allEdits = new HashSet<>(), idealEdits = new HashSet<>();
				for (String key : expertRowMap.keySet()) {
					List<EditHint> edits = expertRowMap.get(key);
					(key.contains("all") ? allEdits : idealEdits).addAll(edits);
				}

				// Get the ideal hints that the highlight failed to generate
				Set<EditHint> missedIdeal = new HashSet<>(idealEdits);
				missedIdeal.removeAll(highlightEdits);

				// Get the ideal/all hints that highlight also generated
				Set<EditHint> agreedAll = new HashSet<>(highlightEdits),
						agreedIdeal = new HashSet<>(highlightEdits),
						extraHints = new HashSet<>(highlightEdits);
				agreedAll.retainAll(allEdits);
				agreedIdeal.retainAll(idealEdits);

				// Get the highlight hints that humans didn't generate
				extraHints.removeAll(allEdits);
				// Remove the ideal hints from all hints to remove redundancy
				agreedAll.removeAll(agreedIdeal);

				System.out.println("-------+ " + assignment.name + " / " + row + " +-------");
				Node node = nodeMap.get(row);
				System.out.println(node.prettyPrint(true));
				System.out.println("Ideal:");
				printEditsWithPriorities("ideal", agreedIdeal, spreadsheet, assignment, row);
				System.out.println("Ok:");
				printEditsWithPriorities("ok", agreedAll, spreadsheet, assignment, row);
				System.out.println("Bad:");
				printEditsWithPriorities("bad", extraHints, spreadsheet, assignment, row);
				System.out.println("Missed Ideal:");
				printEditsWithPriorities("missed", missedIdeal, null, null, null);
				System.out.println();
			}
		}

		spreadsheet.write(testDataset.analysisDir() + "/edm2017-prioritize.csv");
	}

	private static void printEditsWithPriorities(String category, Collection<EditHint> edits,
			Spreadsheet spreadsheet, Assignment assignment, String row) {
		for (EditHint edit : edits) {
			System.out.println(edit);
			if (edit.priority != null) System.out.println(edit.priority);

			if (spreadsheet == null || edit.priority == null) continue;
			Diff.colorStyle = ColorStyle.None;
			spreadsheet.newRow();
			spreadsheet.put("assignment", assignment.name);
			spreadsheet.put("row", row);
			spreadsheet.put("type", edit.action());
			if (edit.priority != null) {
				Map<String, Object> props = edit.priority.getPropertiesMap();
				for (String key : props.keySet()) {
					spreadsheet.put(key, props.get(key));
				}
			}
			spreadsheet.put("edit", edit.toString());
			spreadsheet.put("category", category);
			Diff.colorStyle = ColorStyle.ANSI;
		}
	}

	private static String getType(String key) {
		if (key.endsWith("-all")) return "all";
		if (key.endsWith("-ideal")) return "ideal";
		return key;
	}

	@SuppressWarnings("unused")
	private static void testExtractEdits(Dataset dataset)
			throws FileNotFoundException, IOException {


		CSVParser parser = new CSVParser(new FileReader(new File(dataset.dataDir, "hints.csv")),
				CSVFormat.DEFAULT.withHeader());

		for (CSVRecord record : parser) {
			String codeXML = record.get("code");
			String h1CodeXML = record.get("h1Code");
			String h2CodeXML = record.get("h2Code");

			String userID = record.get("userID");
			String rowID = record.get("rowID");
			String prefix = userID + " (" + rowID + ") ";

//			if (!"rzhi (122231) ".equals(id)) continue;

			Snapshot code = Snapshot.parse("code", codeXML);
			Snapshot h1Code = Snapshot.parse("h1", h1CodeXML);
			Snapshot h2Code = Snapshot.parse("h2", h2CodeXML);

			if (!Agreement.testEditConsistency(code, h1Code, false)) {
				System.out.println(prefix + 1);
			}

			if (!Agreement.testEditConsistency(code, h2Code, false)) {
				System.out.println(prefix + 2);
			}

			printEditSQL(userID, rowID, 1, code, h1Code);
			printEditSQL(userID, rowID, 2, code, h2Code);
		}

		parser.close();
	}

	private static void printEditSQL(String userID, String rowID, int phase,
			Snapshot a, Snapshot b) {
		Node from = Agreement.toTree(a);
		Node to = Agreement.toTree(b);
		List<EditHint> edits = Agreement.findEdits(from, to, false);
		JSONArray hintArray = HintJSON.hintArray(edits);
		String json = hintArray.toString();
		json = StringEscapeUtils.escapeSql(json);
		String sql = String.format(
				"UPDATE hints SET h%dEdits='%s' WHERE userID='%s' AND rowID=%s;",
				phase, json, userID, rowID);
		System.out.println(sql);
	}
}
