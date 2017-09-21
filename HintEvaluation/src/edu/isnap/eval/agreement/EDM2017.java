package edu.isnap.eval.agreement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.agreement.EditComparer.EditDifference;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;

public class EDM2017 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		compareEdits(Spring2017.instance, Fall2016.Squiral, Fall2016.GuessingGame1);
//		compareEdits(Spring2017.instance, Spring2016.Squiral, Spring2016.GuessingGame1);
	}

	public static void testLogEdits() {
		Map<String, AssignmentAttempt> attempts = Fall2016.GuessingGame1.load(Mode.Use, true, true,
				new SnapParser.SubmittedOnly());
		int i = 0;
		for (AssignmentAttempt attempt : attempts.values()) {
			Snapshot last = null;
			for (AttemptAction action : attempt) {
				if (last != null) {// && action.id == 222243) {
					if (!Agreement.testEditConsistency(last, action.snapshot)) {
						System.out.println(action.id);
					}
				}
				last = action.snapshot;
			}
			if (i++ >= 10) break;
		}
	}

	private static void compareEdits(Dataset testDataset, Assignment... trainingAssignments)
			throws FileNotFoundException, IOException {


		CSVParser parser = new CSVParser(new FileReader(new File(testDataset.dataDir, "hints.csv")),
				CSVFormat.DEFAULT.withHeader());

//		Random rand = new Random(1234);

		HashMap<String, HintHighlighter> highlighters = new HashMap<>();
		for (Assignment assignment : trainingAssignments) {
			SnapHintBuilder builder = new SnapHintBuilder(assignment);
			HintMap hintMap = builder.buildGenerator(Mode.Use, 1).hintMap;
			List<Node> solutions = new ArrayList<>(hintMap.solutions);
//			Collections.shuffle(solutions, rand);
//			for (int i = solutions.size(); i >= 0; i--) {
//				if (i % 2 == 0) solutions.remove(i);
//			}
			highlighters.put(assignment.name, new HintHighlighter(
					solutions, hintMap.getHintConfig()));
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

		Map<String, Map<String, List<EditHint>>> expertMap = new LinkedHashMap<>();
		Map<String, Map<String, List<EditHint>>> comparisonMap = new LinkedHashMap<>();
		Map<String, Node> nodeMap = new HashMap<>();
		Map<String, String> assignmentMap = new HashMap<>();

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

//			if (!userID.equals("twprice")) continue;

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

			List<EditHint> idealEdits = Agreement.findEdits(fromNode, toNodeIdeal);
			List<EditHint> allEdits = Agreement.findEdits(fromNode, toNodeAll);
			expertRowMap.put(userID + "-ideal", idealEdits);
			expertRowMap.put(userID + "-all", allEdits);
			comparisonRowMap.put(userID + "-all", allEdits);


			if (!expertRowMap.containsKey("highlight")) {
				comparisonRowMap.put("highlight", highlighter.highlight(fromNode));
//				comparisonRowMap.put("highlight-rted", highlighter.highlightRTED(fromNode));
//				comparisonRowMap.put("highlight-sed", highlighter.highlightStringEdit(fromNode));
			}
//			if (!assignmentID.equals("squiralHW")) {
//				System.out.println(rowID + " / " + userID);
//				EditComparer.compare(fromNode, allEdits, comparisonRowMap.get("highlight"));
//			}
		}

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

		parser.close();
	}

	private static String getType(String key) {
		if (key.endsWith("-all")) return "all";
		if (key.endsWith("-ideal")) return "ideal";
		return key;
	}

	@SuppressWarnings("unused")
	private static void testExtracttEdits(Dataset dataset)
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

			if (!Agreement.testEditConsistency(code, h1Code)) {
				System.out.println(prefix + 1);
			}

			if (!Agreement.testEditConsistency(code, h2Code)) {
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
		List<EditHint> edits = Agreement.findEdits(from, to);
		JSONArray hintArray = HintJSON.hintArray(edits);
		String json = hintArray.toString();
		json = StringEscapeUtils.escapeSql(json);
		String sql = String.format(
				"UPDATE hints SET h%dEdits='%s' WHERE userID='%s' AND rowID=%s;",
				phase, json, userID, rowID);
		System.out.println(sql);
	}
}
