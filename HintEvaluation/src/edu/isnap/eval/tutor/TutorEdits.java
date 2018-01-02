package edu.isnap.eval.tutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import org.json.JSONException;
import org.json.JSONObject;

import edu.isnap.ctd.graph.ASTNode;
import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.ctd.hint.RuleSet;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.Diff.ColorStyle;
import edu.isnap.ctd.util.NullStream;
import edu.isnap.ctd.util.Tuple;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.agreement.Agreement;
import edu.isnap.eval.agreement.HintSelection;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.eval.python.PythonHintConfig;
import edu.isnap.eval.python.PythonImport;
import edu.isnap.eval.python.PythonImport.PythonNode;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.rating.GoldStandard;
import edu.isnap.rating.HintSet;
import edu.isnap.rating.RateHints;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.TutorHint;
import edu.isnap.rating.TutorHint.Priority;
import edu.isnap.rating.TutorHint.Validity;

public class TutorEdits {

	private final static String ISNAP_GOLD_STANDARD =
			RateHints.ISNAP_DATA_DIR + RateHints.GS_SPREADSHEET;
	private final static String CONSENSUS_GG_SQ = "consensus-gg-sq.csv";

	public static void main(String[] args) throws FileNotFoundException, IOException {
//		compareHints(Fall2016.instance);
//		compareHintsPython("../data/itap");

//		verifyHints(Fall2016.instance);

//		Map<String, HintSet> hintSets = readTutorHintSets(Spring2017.instance);
//		for (HintSet hintSet : hintSets.values()) {
//			System.out.println("------------ " + hintSet.name + " --------------");
//			RateHints.rate(standard, hintSet);
//		}


//		GoldStandard standard = readConsensusPython("../data/itap");
//		HighlightHintSet hintSet = new ImportHighlightHintSet("sourcecheck", new PythonHintConfig(),
//				RateHints.ITAP_DATA_DIR + RateHints.TRAINING_DIR);
//		hintSet.addHints(standard);
//		TutorHintSet hintSet = TutorHintSet.fromFile("ITAP", RatingConfig.Python,
//				"../data/itap/handmade_hints_itap_ast.csv");
//		RateHints.rate(standard, hintSet);

		GoldStandard standard = GoldStandard.parseSpreadsheet(ISNAP_GOLD_STANDARD);
//		writeSnapStandard();
//		runConsensus("../data/hint-rating/isnap2017/training", standard, new SnapHintConfig());
//		writeHighlight(RateHints.ISNAP_DATA_DIR, "sourcecheck", standard, new SnapHintConfig());
		RateHints.rate(standard, HintSet.fromFolder("sourcecheck", RatingConfig.Snap,
				RateHints.ISNAP_DATA_DIR + RateHints.ALGORITHMS_DIR + "/sourcecheck"));

//		System.out.println("Fall");
//		runConsensus(Fall2016.instance, readConsensus(Spring2017.instance, CONSENSUS_GG_SQ));
//		System.out.println("Spring");
//		runConsensus(Spring2017.instance, readConsensus(Fall2016.instance, CONSENSUS_GG_SQ));

//		exportRatingDatasetPython("../../PythonAST/data", "../data/itap", RateHints.ITAP_DATA_DIR);
//		exportRatingDatasetSnap(Spring2017.instance, CONSENSUS_GG_SQ,
//				"hint-eval", Spring2017.Squiral, Spring2017.GuessingGame1);
//		exportRatingDatasetSnap(Fall2016.instance, CONSENSUS_GG_SQ,
//				"hint-eval", Fall2016.Squiral, Fall2016.GuessingGame1);
	}

	protected static void writeSnapStandard() throws FileNotFoundException, IOException {
		GoldStandard fall2016Standard = readConsensusSnap(Fall2016.instance, CONSENSUS_GG_SQ);
		GoldStandard spring2017Standard = readConsensusSnap(Spring2017.instance, CONSENSUS_GG_SQ);
		GoldStandard standard = GoldStandard.merge(fall2016Standard, spring2017Standard);
		standard.writeSpreadsheet(ISNAP_GOLD_STANDARD);
	}

	protected static void writeHighlight(String dataDirectory, String name, GoldStandard standard,
			HintConfig hintConfig)
			throws FileNotFoundException, IOException {
		String trainingDirectory = new File(dataDirectory, RateHints.TRAINING_DIR).getPath();
		HighlightHintSet hintSet = new ImportHighlightHintSet(name, hintConfig, trainingDirectory);
		hintSet.addHints(standard.getHintRequests());
		hintSet.writeToFolder(new File(
				dataDirectory, RateHints.ALGORITHMS_DIR + File.separator + name).getPath(), true);
	}

	protected static void runConsensus(Dataset trainingDataset, GoldStandard standard)
			throws FileNotFoundException, IOException {
		HighlightHintSet hintSet = new DatasetHighlightHintSet(
			trainingDataset.getName(), new SnapHintConfig(), trainingDataset)
				.addHints(standard.getHintRequests());
		RateHints.rate(standard, hintSet);
//		hintSet.toTutorEdits().forEach(e -> System.out.println(
//				e.toSQLInsert("handmade_hints", "highlight", 20000, false, true)));
	}

	protected static void runConsensus(String trainingDirectory, GoldStandard standard,
			HintConfig hintConfig)
			throws FileNotFoundException, IOException {
		HighlightHintSet hintSet = new ImportHighlightHintSet(
				new File(trainingDirectory).getName(), hintConfig, trainingDirectory);
		hintSet.addHints(standard.getHintRequests());
		RateHints.rate(standard, hintSet);
	}

	protected static void highlightSQL(Dataset trainingDataset, GoldStandard standard)
			throws FileNotFoundException, IOException {
		RuleSet.trace = NullStream.instance;
		HintConfig config = new SnapHintConfig();

		int offset = 20000;

		Spreadsheet spreadsheet = new Spreadsheet();
		HighlightHintSet hintSet = new DatasetHighlightHintSet(
				trainingDataset.getName(), config, trainingDataset)
				.addHints(standard.getHintRequests());
		hintSet.toTutorEdits().forEach(edit -> {
			System.out.println(edit.toSQLInsert(
					"handmade_hints", "highlight", offset, false, true));

			spreadsheet.newRow();
			spreadsheet.put("Assignment ID", edit.assignmentID);
			spreadsheet.put("Row ID", edit.requestID);
			spreadsheet.put("Hint ID", edit.hintID + offset);

			spreadsheet.put("Valid (0-1)", null);
			spreadsheet.put("Priority (1-3)", null);

			spreadsheet.put("Hint", edit.editsString(false));
		});
		spreadsheet.write(trainingDataset.analysisDir() + "/highlight-hints.csv");
	}

	public static void exportRatingDatasetSnap(Dataset dataset, String path,
			String folder, Assignment... assignments)
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
			JsonAST.exportAssignmentTraces(assignment, true, folder + "/requests",
					attempt -> attempt.rows.rows.stream().anyMatch(a -> ids.contains(a.id)),
					actionFilter,
					attempt -> attempt.rows.rows.stream()
									.filter(a -> ids.contains(a.id))
									.map(a -> String.valueOf(a.id))
									.findAny().orElse(attempt.id));
			JsonAST.exportAssignmentTraces(assignment, true, folder + "/training",
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

	public static void exportRatingDatasetPython(String jsonDir, String editsDir, String outputDir)
			throws IOException {
		Map<String, ListMap<String, PythonNode>> nodeMap =
				PythonImport.loadAllAssignments(jsonDir);
		ListMap<String, PrintableTutorHint> tutorHint =
				readTutorEditsPython(editsDir + "/handmade_hints_ast.csv");

		for (String assignmentID : tutorHint.keySet()) {
			List<PrintableTutorHint> list = tutorHint.get(assignmentID);
			Set<String> hintRequestIDs = list.stream()
					.filter(hint -> "consensus".equals(hint.tutor))
					.map(hint -> hint.requestID)
					.collect(Collectors.toSet());

			ListMap<String, PythonNode> attemptMap = nodeMap.get(assignmentID);
			for (String studentID : attemptMap.keySet()) {
				List<PythonNode> snapshots = attemptMap.get(studentID);
				List<PythonNode> hintRequestNodes = snapshots.stream()
						.filter(node -> hintRequestIDs.contains(node.id))
						.collect(Collectors.toList());
				if (hintRequestNodes.isEmpty()) {
					if (!snapshots.stream().anyMatch(node -> node.correct.orElse(false))) continue;
					while (!snapshots.get(snapshots.size() - 1).correct.orElse(false)) {
						snapshots.remove(snapshots.size() - 1);
					}
					exportHistory(outputDir, assignmentID, snapshots, null);
				} else {
					for (PythonNode request : hintRequestNodes) {
						hintRequestIDs.remove(request.id);
						exportHistory(outputDir, assignmentID, snapshots, request);
					}
				}
			}

			if (!hintRequestIDs.isEmpty()) {
				throw new RuntimeException("Missing hint request IDs for " + assignmentID + ": " +
						hintRequestIDs);
			}
		}
	}

	private static void exportHistory(String rootDir, String assignmentID,
			List<PythonNode> snapshots, PythonNode requestNode)
					throws FileNotFoundException, JSONException {
		PythonNode lastNode = null;
		int order = 0;
		boolean isTraining = requestNode == null;
		for (PythonNode snapshot : snapshots) {
			if (snapshot.equals(lastNode)) continue;
			JSONObject json = snapshot.toASTNode().toJSON();
			json.put("isCorrect", snapshot.correct.orElse(false));
			json.put("source", snapshot.source);
			String id = snapshot.id;
			if (id.length() > 8) id = id.substring(0, 8);
			JsonAST.write(
					String.format("%s/%s/%s/%s/%05d-%s.json",
						rootDir,
						isTraining ? "training" : "requests",
						assignmentID,
						isTraining ? snapshot.student : requestNode.id,
						order++, id),
					json.toString(2));
			if (snapshot == requestNode) break;
		}
	}

	public static void verifyHints(Dataset dataset) throws FileNotFoundException, IOException {
		ListMap<String, PrintableTutorHint> edits = readTutorEditsSnap(dataset);
		edits.values().forEach(l -> l.forEach(e -> e.verify()));
	}

	private final static int compareEditsHintOffset = 10000;

	public static void compareHintsSnap(Dataset dataset) throws FileNotFoundException, IOException {
		String writeDir = String.format("%s/tutor-hints/%d/", dataset.analysisDir(),
				compareEditsHintOffset);
		compareHints(readTutorEditsSnap(dataset), writeDir, RatingConfig.Snap);
	}

	public static void compareHintsPython(String dir)
			throws FileNotFoundException, IOException {
		String writeDir = String.format("%s/analysis/tutor-hints/%d/", dir, compareEditsHintOffset);
		compareHints(readTutorEditsPython(dir + "/handmade_hints_ast.csv"),
				writeDir, RatingConfig.Python);
	}

	public static void compareHints(ListMap<String, PrintableTutorHint> assignmentMap,
			String writeDir, RatingConfig config) throws FileNotFoundException, IOException {

		Set<String> tutors =  assignmentMap.values().stream()
				.flatMap(List::stream).map(e -> e.tutor)
				.collect(Collectors.toSet());
		Map<String, Spreadsheet> tutorSpreadsheets = tutors.stream()
				.collect(Collectors.toMap(t -> t, t -> new Spreadsheet()));
		StringBuilder sql = new StringBuilder();

		Set<String> assignments = new TreeSet<>(assignmentMap.keySet());
		for (String assignmentID : assignments) {
			System.out.println("\n#---------> " + assignmentID + " <---------#\n");

			List<PrintableTutorHint> edits = assignmentMap.get(assignmentID);
			edits = edits.stream()
					.filter(e -> !"consensus".equals(e.tutor))
					.collect(Collectors.toList());
			Set<String> requestIDs = new TreeSet<>(edits.stream().map(e -> e.requestID)
					.collect(Collectors.toSet()));

			for (String requestID : requestIDs) {
				System.out.println("-------- " + requestID + " --------");

				ListMap<ASTNode, PrintableTutorHint> givers = new ListMap<>();
				edits.stream()
				.filter(e -> e.requestID.equals(requestID))
				.forEach(e -> givers.add(
						RateHints.normalizeNewValuesTo(e.from, e.to, config), e));

				ASTNode from = givers.values().stream().findFirst().get().get(0).from;
				String fromPP = from.prettyPrint(true, config::nodeTypeHasBody);
				System.out.println(fromPP);

				List<ASTNode> keys = new ArrayList<>(givers.keySet());
				// Sort by the hintID of the first hint with this outcome (which will be used
				// as the representative TutorEdit)
				keys.sort((n1, n2) -> Integer.compare(
						givers.get(n1).get(0).hintID, givers.get(n2).get(0).hintID));
				for (ASTNode to : keys) {
					System.out.println(Diff.diff(fromPP,
							to.prettyPrint(true, config::nodeTypeHasBody), 1));
					List<PrintableTutorHint> tutorEdits = givers.get(to);
					PrintableTutorHint firstEdit = tutorEdits.get(0);
					String editsString = firstEdit.editsString(true);
					System.out.println(editsString);
					int priorityMatches = 0;
					for (TutorHint tutorEdit : tutorEdits) {
						if (tutorEdit.priority == firstEdit.priority) priorityMatches++;
						System.out.printf("  %d/%s: #%d\n",
								tutorEdit.priority.value, tutorEdit.tutor, tutorEdit.hintID);
					}
					System.out.println("=======");

					sql.append(firstEdit.toSQLInsert(
							"handmade_hints", "consensus", compareEditsHintOffset,
							priorityMatches == tutors.size(), true));
					sql.append("\n");

					Map<String, TutorHint> givingTutors = null;
					try {
						givingTutors = tutorEdits.stream()
								.collect(Collectors.toMap(e -> e.tutor, e -> e));
					} catch (Exception e) {
						System.out.println("Duplicate hints from one tutor:");
						System.out.println(from.prettyPrint(true, config::nodeTypeHasBody));
						System.out.println(to.prettyPrint(true, config::nodeTypeHasBody));
						tutorEdits.forEach(System.out::println);
						throw e;
					}

					String editsStringNoANSI = firstEdit.editsString(false);

					for (String tutor : tutorSpreadsheets.keySet()) {
						TutorHint edit = givingTutors.get(tutor);

						Spreadsheet spreadsheet = tutorSpreadsheets.get(tutor);
						spreadsheet.newRow();
						spreadsheet.put("Assignment ID", firstEdit.assignmentID);
						spreadsheet.put("Row ID", firstEdit.requestID);
						spreadsheet.put("Hint ID", firstEdit.hintID + compareEditsHintOffset);

						spreadsheet.put("Valid (0-1)", edit == null ? null : 1);
						spreadsheet.put("Priority (1-4)",
								edit == null ? null : edit.priority.value);

						spreadsheet.put("Hint", editsStringNoANSI);
					}
				}
				System.out.println();
			}
		}

		for (String tutor : tutorSpreadsheets.keySet()) {
			Spreadsheet spreadsheet = tutorSpreadsheets.get(tutor);
			spreadsheet.write(writeDir + tutor + ".csv");
		}
		JsonAST.write(writeDir + "consensus.sql", sql.toString());
	}

	public static Map<String, HintSet> readTutorHintSets(Dataset dataset)
			throws FileNotFoundException, IOException {
		Map<String, HintSet> hintSets = new HashMap<>();
		ListMap<String, PrintableTutorHint> allEdits = readTutorEditsSnap(dataset);
		for (List<PrintableTutorHint> list : allEdits.values()) {
			for (TutorHint edit : list) {
				if (edit.tutor.equals("consensus")) continue;
				HintSet set = hintSets.get(edit.tutor);
				if (set == null) {
					hintSets.put(edit.tutor,
							set = new HintSet(edit.tutor, RatingConfig.Snap));
				}
				set.add(edit.toOutcome());
			}
		}
		hintSets.values().forEach(s -> s.finish());
		return hintSets;
	}

	protected static GoldStandard readConsensusSnap(Dataset dataset, String consensusPath)
			throws FileNotFoundException, IOException {
		Map<Integer, Tuple<Validity, Priority>> consensus =
				readConsensusSpreadsheet(new File(dataset.dataDir, consensusPath).getPath(), true);
		ListMap<String, PrintableTutorHint> allEdits = readTutorEditsSnap(dataset);
		return readConsensus(consensus, allEdits);
	}

	protected static GoldStandard readConsensusPython(String dir)
			throws FileNotFoundException, IOException {
		Map<Integer, Tuple<Validity, Priority>> consensus =
				readConsensusSpreadsheet(new File(dir, "consensus.csv").getPath(), false);
		ListMap<String, PrintableTutorHint> allEdits =
				readTutorEditsPython(dir + "/handmade_hints_ast.csv");
		return readConsensus(consensus, allEdits);
	}

	private static GoldStandard readConsensus(Map<Integer, Tuple<Validity, Priority>> consensus,
			ListMap<String, PrintableTutorHint> allEdits) {
		ListMap<String, PrintableTutorHint> consensusEdits = new ListMap<>();
		for (String assignmentID : allEdits.keySet()) {
			List<PrintableTutorHint> list = allEdits.get(assignmentID);
			List<PrintableTutorHint> keeps = new ArrayList<>();
			for (PrintableTutorHint hint : list) {
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

	private static Map<Integer, Tuple<Validity, Priority>> readConsensusSpreadsheet(String path,
			boolean failIfNoConsensus)
			throws FileNotFoundException, IOException {
		Map<Integer, Tuple<Validity, Priority>> map = new HashMap<>();
		CSVParser parser = new CSVParser(new FileReader(path), CSVFormat.DEFAULT.withHeader());
		for (CSVRecord row : parser) {
			int id = Integer.parseInt(row.get("Hint ID"));
			boolean consensus = false;
			try {
				consensus = Double.parseDouble(row.get("Consensus (Validity)")) == 1;
			} catch (NumberFormatException e) {
				if (failIfNoConsensus) {
					parser.close();
					throw e;
				}
			}
			String priorityString = row.get("Consensus (Priority)");
			int priority = 0;
			if (consensus) {
				try {
					priority = Integer.parseInt(priorityString);
				} catch (NumberFormatException e) {
					if (failIfNoConsensus) {
						parser.close();
						throw e;
					}
				}
			}
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
		return map;
	}

	public static ListMap<String, PrintableTutorHint> readTutorEditsSnap(Dataset dataset)
			throws FileNotFoundException, IOException {

		Map<String, Assignment> assignments = dataset.getAssignmentMap();
		Map<Integer, AttemptAction> hintActionMap = new HashMap<>();
		Set<String> loadedAssignments = new HashSet<>();

		return readTutorEdits(dataset.dataDir + "/handmade_hints.csv",
				(hintID, requestID, tutor, assignmentID, toSource, row) -> {

			int requestNumber = Integer.parseInt(requestID);
			if (loadedAssignments.add(assignmentID)) {
				loadAssignment(assignments, hintActionMap, assignmentID);
			}

			if (!hintActionMap.containsKey(requestNumber)) {
				System.err.println("Missing hintID: " + requestNumber);
			}

			Snapshot fromS = hintActionMap.get(requestNumber).lastSnapshot;
			Snapshot toS = Snapshot.parse(fromS.name, toSource);

			if (SimpleNodeBuilder.toTree(fromS, true).equals(SimpleNodeBuilder.toTree(toS, true))) {
				System.out.printf("Node edits for %s, request %s, hint #%d\n",
						tutor, requestID, hintID);
				return null;
			}

			ASTNode from = JsonAST.toAST(fromS, true);
			ASTNode to = JsonAST.toAST(toS, true);

			if (from.equals(to)) {
				// If the edit involves only changing literal values, we still exclude it, since the
				// training dataset doesn't include these
				return null;
			}

			return new PrintableTutorHint(hintID, requestID, tutor, assignmentID, from, to,
					toSource);
		});
	}

	private static ASTNode toPrunedPythonNode(String json) {
		ASTNode node = ASTNode.parse(json);
		node.recurse(n -> {
			for (int i = 0; i < n.children().size(); i++) {
				ASTNode child = n.children().get(i);
				if (child != null && "Str".equals(child.type) && "~".equals(child.value)) {
					n.removeChild(i--);
				}
			}
		});
		return node;
	}

	public static ListMap<String, PrintableTutorHint> readTutorEditsPython(String filePath)
			throws FileNotFoundException, IOException {
		return readTutorEdits(filePath,
				(hintID, requestID, tutor, assignmentID, toSource, row) -> {
			ASTNode from, to;
			try {
				from = toPrunedPythonNode(row.get("codeAST"));
				to = toPrunedPythonNode(row.get("hintCodeAST"));
			} catch (Exception e){
				System.out.println("Error reading hint: " + hintID);
				throw e;
			}
			return new PrintableTutorHint(hintID, requestID, tutor, assignmentID, from, to,
					toSource);
		});
	}

	private interface NodeParser {
		PrintableTutorHint parse(int hintID, String requestID, String tutor, String assignmentID,
				String toSource, CSVRecord row);
	}

	public static ListMap<String, PrintableTutorHint> readTutorEdits(String path, NodeParser np)
			throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(path),
				CSVFormat.DEFAULT.withHeader());

		ListMap<String, PrintableTutorHint> edits = new ListMap<>();

		for (CSVRecord record : parser) {
			int hintID = Integer.parseInt(record.get("hid"));
			String tutor = record.get("userID");
			// Highlight hints aren't parsable
			if ("highlight".equals(tutor)) continue;
			String requestID = record.get("rowID");
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

			String toSource = record.get("hintCode");
			// Skip empty hints (they may exist if no hint is appropriate for a snapshot)
			if (toSource.trim().isEmpty() || toSource.equals("NULL")) continue;

			PrintableTutorHint edit = np.parse(
					hintID, requestID, tutor, assignmentID, toSource, record);
			// If parse returns null it means the edit is empty or shouldn't be used, e.g. literal-
			// only hints
			if (edit == null) continue;
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

	static class PrintableTutorHint extends TutorHint {

		public final String toSource;

		// Edits are for human reading _only_ and should not be used for comparisons, since they
		// are generated by the SourceCheck algorithm
		private final List<EditHint> edits;
		private final Node fromNode, toNode;


		public PrintableTutorHint(int hintID, String requestID, String tutor, String assignmentID,
				ASTNode from, ASTNode to, String toSource) {
			super(hintID, requestID, tutor, assignmentID, from, to);
			fromNode = HighlightHintSet.copyWithIDs(JsonAST.toNode(from, SnapNode::new));
			toNode = JsonAST.toNode(to, SnapNode::new);
			edits = calculateEdits();
			this.toSource = toSource;
		}

		private List<EditHint> calculateEdits() {
			// TODO: Find a better way to parse/read the edits
			// This shouldn't be strictly necessary to compare edits, since it should be using only
			// the to Node for comparison, but it still seems important.
			HintConfig config = new PythonHintConfig();
			List<Node> solutions = Collections.singletonList(toNode);
			HintHighlighter highlighter = new HintHighlighter(solutions, config);
			highlighter.trace = NullStream.instance;
			List<EditHint> edits = highlighter.highlight(fromNode);
			return edits;
		}

		public boolean verify() {
			boolean pass = Agreement.testEditConsistency(fromNode, toNode, true, true);
			if (!pass) {
				System.out.println("Failed: " + this);
			}
			return pass;
		}

		String editsString(boolean useANSI) {
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
			boolean requestIDIsInt = false;
			try {
				Integer.parseInt(requestID);
				requestIDIsInt = true;
			} catch (NumberFormatException e) { }

			return String.format("INSERT INTO `%s` (`hid`, `userID`, `rowID`, `trueAssignmentID`, "
					+ "`priority`, `hintCode`, `hintEdits`, `updatedTime`) "
					+ "VALUES (%d, '%s', %s, '%s', %s, '%s', '%s', %s);",
					table, hintID + hintIDOffset, user,
					requestIDIsInt ? requestID : ("'" + requestID + "'"),
					assignmentID,
					addPriority ? String.valueOf(priority.value) : "NULL",
					StringEscapeUtils.escapeSql(toSource),
					StringEscapeUtils.escapeSql(HintJSON.hintArray(edits).toString()),
					addDate ? "NOW()" : "NULL");
		}

	}
}
