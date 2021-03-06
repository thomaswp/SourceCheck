package edu.isnap.eval.tutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
import org.json.JSONException;

import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.eval.agreement.Agreement;
import edu.isnap.eval.agreement.HintSelection;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.eval.python.PythonImport;
import edu.isnap.eval.python.PythonImport.PythonNode;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.NullStream;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.SnapNode;
import edu.isnap.hint.util.Tuple;
import edu.isnap.node.ASTNode;
import edu.isnap.node.ASTSnapshot;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.rating.HintRater;
import edu.isnap.rating.RatingConfig;
import edu.isnap.rating.data.GoldStandard;
import edu.isnap.rating.data.Trace;
import edu.isnap.rating.data.TraceDataset;
import edu.isnap.rating.data.TutorHint;
import edu.isnap.rating.data.TutorHint.Priority;
import edu.isnap.rating.data.TutorHint.Validity;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.util.Diff;
import edu.isnap.util.Diff.ColorStyle;
import edu.isnap.util.Spreadsheet;
import edu.isnap.util.map.ListMap;

public class TutorEdits {

//	protected static void highlightSQL(Dataset trainingDataset, GoldStandard standard)
//			throws FileNotFoundException, IOException {
//		RuleSet.trace = NullStream.instance;
//		HintConfig config = new SnapHintConfig();
//
//		int offset = 20000;
//
//		Spreadsheet spreadsheet = new Spreadsheet();
//		HighlightHintSet hintSet = new DatasetHighlightHintSet(
//				trainingDataset.getName(), config, trainingDataset)
//				.addHints(standard.getHintRequests());
//		hintSet.toTutorEdits().forEach(edit -> {
//			System.out.println(edit.toSQLInsert(
//					"handmade_hints", "highlight", offset, false, true));
//
//			spreadsheet.newRow();
//			spreadsheet.put("Assignment ID", edit.assignmentID);
//			spreadsheet.put("Row ID", edit.requestID);
//			spreadsheet.put("Hint ID", edit.hintID + offset);
//
//			spreadsheet.put("Valid (0-1)", null);
//			spreadsheet.put("Priority (1-3)", null);
//
//			spreadsheet.put("Hint", edit.editsString(false));
//		});
//		spreadsheet.write(trainingDataset.analysisDir() + "/highlight-hints.csv");
//	}

	public static void buildSnapDatasets(Dataset dataset, String consensusPath,
			TraceDataset training, TraceDataset requests, Assignment... assignments)
			throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(dataset.dataDir + "/" + consensusPath),
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
			for (AssignmentAttempt attempt : assignment.load(
					Mode.Use, false, true, new SnapParser.LikelySubmittedOnly()).values()) {
				List<Integer> requestIDs = attempt.rows.rows.stream()
						.filter(a -> ids.contains(a.id))
						.map(a -> a.id)
						.collect(Collectors.toList());
				collectedIDs.addAll(requestIDs);

				if (requestIDs.size() == 0) {
					if (assignment.wasLoggingUnstable(attempt.id) ||
							attempt.researcherGrade == null || attempt.researcherGrade.average() != 1) continue;
					Trace trace = JsonAST.createTrace(attempt, assignment.name, true, true, null);
					training.addTrace(trace);
				} else {
					for (Integer requestID : requestIDs) {
						requests.addTrace(JsonAST.createTrace(
								attempt, assignment.name, true, true, requestID));
					}
				}

			}
		}

		if (collectedIDs.size() != ids.size()) {
			ids.removeAll(collectedIDs);
			throw new RuntimeException("Missing row IDs: " + ids);
		}
	}

	public static void buildPythonDatasets(String jsonDir, String editsDir,
			TraceDataset training, TraceDataset requests) throws IOException {

		Map<String, ListMap<String, PythonNode>> nodeMap =
				PythonImport.loadAllAssignments(jsonDir);
		ListMap<String, PrintableTutorHint> tutorHint =
				readTutorEditsPython(editsDir + "/handmade_hints_ast.csv", null);

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
					training.addTrace(createTrace(assignmentID, snapshots, null));
				} else {
					for (PythonNode request : hintRequestNodes) {
						hintRequestIDs.remove(request.id);
						requests.addTrace(createTrace(assignmentID, snapshots, request));
					}
				}
			}

			if (!hintRequestIDs.isEmpty()) {
				throw new RuntimeException("Missing hint request IDs for " + assignmentID + ": " +
						hintRequestIDs);
			}
		}
	}

	private static Trace createTrace(String assignmentID, List<PythonNode> snapshots,
			PythonNode requestNode) throws FileNotFoundException, JSONException {
		if (snapshots.size() == 0) return null;
		String id = requestNode == null ? snapshots.get(0).id : requestNode.id;
		Trace trace = new Trace(id, assignmentID);
		PythonNode lastNode = null;
		for (PythonNode snapshot : snapshots) {
			if (snapshot.equals(lastNode)) continue;
			if (snapshot.tag == null || !(snapshot.tag instanceof ASTSnapshot)) {
				throw new RuntimeException("PythonNode missing ASTSnapshot tag");
			}
			trace.add((ASTSnapshot) snapshot.tag);
			if (snapshot == requestNode) break;
		}
		return trace;
	}

	public static void verifyHints(Dataset dataset) throws FileNotFoundException, IOException {
		ListMap<String, PrintableTutorHint> edits = readTutorEditsSnap(dataset);
		edits.values().forEach(l -> l.forEach(e -> e.verify()));
	}

	public static void compareHintsSnap(Dataset dataset, int offset)
			throws FileNotFoundException, IOException {
		String writeDir = String.format("%s/tutor-hints/%d/", dataset.analysisDir(), offset);
		compareHints(readTutorEditsSnap(dataset), writeDir, offset, RatingConfig.Snap);
	}

	public static void compareHintsPython(String dir, int offset)
			throws FileNotFoundException, IOException {
		String writeDir = String.format("%s/analysis/tutor-hints/%d/", dir, offset);
		compareHints(readTutorEditsPython(dir + "/handmade_hints_ast.csv", null),
				writeDir, offset, RatingConfig.Python);
	}

	public static void compareHints(ListMap<String, PrintableTutorHint> assignmentMap,
			String writeDir, int offset, RatingConfig config)
					throws FileNotFoundException, IOException {

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
						HintRater.normalizeNewValuesTo(
								HintRater.normalizeNodeValues(e.from, config),
								e.to, config, null), e));

				ASTNode from = givers.values().stream().findFirst().get().get(0).from;
				String fromPP = from.prettyPrint(true, config);
				System.out.println(fromPP);

				List<ASTNode> keys = new ArrayList<>(givers.keySet());
				// Sort by the hintID of the first hint with this outcome (which will be used
				// as the representative TutorEdit)
				keys.sort((n1, n2) -> Integer.compare(
						givers.get(n1).get(0).hintID, givers.get(n2).get(0).hintID));
				for (ASTNode to : keys) {
					System.out.println(Diff.diff(fromPP,
							to.prettyPrint(true, config), 1));
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
							"handmade_hints", "consensus", offset,
							priorityMatches == tutors.size(), true));
					sql.append("\n");

					Map<String, TutorHint> givingTutors = null;
					try {
						givingTutors = tutorEdits.stream()
								.collect(Collectors.toMap(e -> e.tutor, e -> e));
					} catch (Exception e) {
						System.out.println("Duplicate hints from one tutor:");
						System.out.println(from.prettyPrint(true, config));
						System.out.println(to.prettyPrint(true, config));
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
						spreadsheet.put("Hint ID", firstEdit.hintID + offset);

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

	public static Map<String, TutorHintSet> readTutorHintSetsSnap(Dataset dataset)
			throws FileNotFoundException, IOException {
		ListMap<String, PrintableTutorHint> allEdits = readTutorEditsSnap(dataset);
		return createTutorHintSets(allEdits, RatingConfig.Snap);
	}

	public static Map<String, TutorHintSet> readTutorHintSetsPython(String filePath, String year)
			throws FileNotFoundException, IOException {
		ListMap<String, PrintableTutorHint> allEdits = readTutorEditsPython(
				filePath + "handmade_hints_ast.csv", year);
		return createTutorHintSets(allEdits, RatingConfig.Python);
	}

	private static Map<String, TutorHintSet> createTutorHintSets(
			ListMap<String, PrintableTutorHint> allEdits, RatingConfig config) {
		ListMap<String, TutorHint> hintMap = new ListMap<>();
		allEdits.values().stream()
		.flatMap(list -> list.stream())
		.filter(edit -> !edit.tutor.equals("consensus"))
		.forEach(hint -> hintMap.add(hint.tutor, hint));
		return hintMap.keySet().stream().collect(Collectors.toMap(
				tutor -> tutor,
				tutor -> new TutorHintSet(tutor, config, hintMap.get(tutor))));
	}

	protected static GoldStandard readConsensusSnap(Dataset dataset, String consensusPath)
			throws FileNotFoundException, IOException {
		Map<Integer, Tuple<EnumSet<Validity>, Priority>> consensus =
				readConsensusSpreadsheet(new File(dataset.dataDir, consensusPath).getPath(), true);
		ListMap<String, PrintableTutorHint> allEdits = readTutorEditsSnap(dataset);
		return readConsensus(consensus, allEdits);
	}

	protected static GoldStandard readConsensusPython(String dir, String year)
			throws FileNotFoundException, IOException {
		Map<Integer, Tuple<EnumSet<Validity>, Priority>> consensus =
				readConsensusSpreadsheet(new File(dir, "consensus.csv").getPath(), false);
		ListMap<String, PrintableTutorHint> allEdits =
				readTutorEditsPython(dir + "/handmade_hints_ast.csv", year);
		return readConsensus(consensus, allEdits);
	}

	private static GoldStandard readConsensus(Map<Integer,
			Tuple<EnumSet<Validity>, Priority>> consensus,
			ListMap<String, PrintableTutorHint> allEdits) {
		ListMap<String, PrintableTutorHint> consensusEdits = new ListMap<>();
		for (String assignmentID : allEdits.keySet()) {
			List<PrintableTutorHint> list = allEdits.get(assignmentID);
			List<PrintableTutorHint> keeps = new ArrayList<>();
			for (PrintableTutorHint hint : list) {
				if (!hint.tutor.equals("consensus")) continue;
				Tuple<EnumSet<Validity>, Priority> ratings = consensus.get(hint.hintID);
				if (ratings == null) {
					throw new RuntimeException("No consensus rating for: " + hint.hintID);
				}
				if (ratings.x.isEmpty()) continue;
				hint.validity = ratings.x;
				hint.priority = ratings.y;
				keeps.add(hint);
			}
			consensusEdits.put(assignmentID, keeps);
		}
		return new GoldStandard(consensusEdits);
	}

	private static Map<Integer, Tuple<EnumSet<Validity>, Priority>>
			readConsensusSpreadsheet(String path, boolean failIfNoConsensus)
			throws FileNotFoundException, IOException {
		Map<Integer, Tuple<EnumSet<Validity>, Priority>> map = new HashMap<>();
		CSVParser parser = new CSVParser(new FileReader(path), CSVFormat.DEFAULT.withHeader());
		for (CSVRecord row : parser) {
			int id = Integer.parseInt(row.get("Hint ID"));
			String priorityString = row.get("Consensus (Priority)");
			boolean consensusValid = false;
			int priority = 0;
			try {
				consensusValid = Double.parseDouble(row.get("Consensus (Validity)")) == 1;
				if (consensusValid) {
					priority = Integer.parseInt(priorityString);
				}
			} catch (NumberFormatException e) {
				if (failIfNoConsensus) {
					parser.close();
					throw e;
				}
			}

			int v1 = Integer.parseInt(row.get("V1"));

			if (parser.getHeaderMap().containsKey("P4")) {
				// This is a fix to ensure that "Too Soon" votes are not counted as valid votes:
				// Count the validity using the number of P1-P3 votes, since P4 votes (which are
				// counted in V1) should not count as votes for validity unless there is consensus.
				int validPriorityCount = 0;
				for (int i = 1; i <= 3; i++) {
					String key = "P" + i;
					validPriorityCount += Integer.parseInt(row.get(key));
				}
				int tooSoonCount = Integer.parseInt(row.get("P4"));
				if (v1 > validPriorityCount + tooSoonCount) {
					parser.close();
					System.err.printf("ID: %d, V1: %d > (P1-3: %d) + (P4: %d)\n",
							id, v1, validPriorityCount, tooSoonCount);
					throw new RuntimeException("Valid count does not match priority count.");
				}
				v1 -= tooSoonCount;
			}

			EnumSet<Validity> validity = EnumSet.noneOf(Validity.class);

			if (consensusValid) {
				validity.add(Validity.Consensus);
			}
			if (v1 > 1) {
				validity.add(Validity.MultipleTutors);
			}
			if (v1 > 0) {
				validity.add(Validity.OneTutor);
			}
			map.put(id, new Tuple<>(validity, Priority.fromInt(priority)));
		}
		parser.close();
		return map;
	}

	public static ListMap<String, PrintableTutorHint> readTutorEditsSnap(Dataset dataset)
			 throws FileNotFoundException, IOException {
		return readTutorEditsSnap(dataset, "handmade_hints.csv");
	}

	public static ListMap<String, PrintableTutorHint> readTutorEditsSnap(Dataset dataset,
			String csvFile) throws FileNotFoundException, IOException {

		Map<String, Assignment> assignments = dataset.getAssignmentMap();
		Map<Integer, AttemptAction> hintActionMap = new HashMap<>();
		Set<String> loadedAssignments = new HashSet<>();

		String year = dataset.getName().toLowerCase();

		return readTutorEdits(dataset.dataDir + File.separator + csvFile,
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
				System.out.printf("No edits for %s, request %s, hint #%d\n",
						tutor, requestID, hintID);
				return null;
			}

			// To keep backwards compatibility with older datasets, we strip out non-numeric
			// literals (e.g. colors, menu dropdowns) when generating GS or training data
			ASTNode from = JsonAST.toAST(fromS, true, true);
			ASTNode to = JsonAST.toAST(toS, true, true);

			if (from.equals(to)) {
				// If the edit involves only changing literal values, we still exclude it, since the
				// training dataset doesn't include these
				return null;
			}

			fixNewCustomBlockValues(from, to);

			return new PrintableTutorHint(hintID, requestID, tutor, assignmentID, year,
					from, to, toSource);
		});
	}

	/**
	 * Fixes an issue with newly-added custom block calls, where an un-applied custom block may
	 * have a different name than the call block the tutor used. Since a hint algorithm will only
	 * see the current version of the custom block name, this is problematic, so we search for the
	 * match and change the value to the most recent custom block name.
	 */
	private static boolean fixNewCustomBlockValues(ASTNode from, ASTNode to) {
		Map<String, ASTNode> added = new HashMap<>();
		Map<String, ASTNode> customBlocks = new HashMap<>();
		to.recurse(node -> {
			if (node.id != null) added.put(node.id, node);
			if (node.hasType("customBlock")) customBlocks.put(node.value, node);
		});
		from.recurse(node -> added.remove(node.id));
		boolean fixed = true;
		for (ASTNode node : added.values()) {
			if (node.hasType("evaluateCustomBlock")) {
				if (!customBlocks.containsKey(node.value)) {
					List<String> matches = customBlocks.keySet().stream()
						.filter(name -> name.contains(node.value) || node.value.contains(name))
						.collect(Collectors.toList());
					if (matches.size() != 1) {
						System.err.println("No matching custom block: " + node.value);
						System.err.println(ASTNode.diff(from, to, RatingConfig.Snap));
						continue;
					}
					node.replaceWith(new ASTNode(node.type, matches.get(0), node.id));
					fixed = true;
				}
			}
		}
		return fixed;
	}

	private static ASTNode toPrunedPythonNode(String json) {
		ASTNode node = ASTNode.parse(json);
		node.recurse(n -> {
			for (int i = 0; i < n.children().size(); i++) {
				ASTNode child = n.children().get(i);
				if (child != null && "Str".equals(child.type) && "~".equals(child.value)) {
					n.removeChild(i);
					n.addChild(i, new ASTNode(ASTNode.EMPTY_TYPE, null, null));
				}
			}
		});
		return node;
	}

	public static ListMap<String, PrintableTutorHint> readTutorEditsPython(String filePath,
			String year) throws FileNotFoundException, IOException {
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
			return new PrintableTutorHint(hintID, requestID, tutor, assignmentID, year, from, to,
					toSource);
		});
	}

	private interface NodeParser {
		PrintableTutorHint parse(int hintID, String requestID, String tutor, String assignmentID,
				String toSource, CSVRecord row);
	}

	private static ListMap<String, PrintableTutorHint> readTutorEdits(String path, NodeParser np)
			throws FileNotFoundException, IOException {
		CSVParser parser = new CSVParser(new FileReader(path),
				CSVFormat.DEFAULT.withHeader());

		ListMap<String, PrintableTutorHint> edits = new ListMap<>();

		for (CSVRecord record : parser) {
			int hintID = Integer.parseInt(record.get("hid"));
			String tutor = record.get("userID");
			// Highlight hints aren't parsable
			if ("highlight".equals(tutor) || "algorithms".equals(tutor)) continue;
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
				String year, ASTNode from, ASTNode to, String toSource) {
			super(hintID, requestID, tutor, assignmentID, year, from, to);
			fromNode = HighlightHintSet.copyWithIDs(JsonAST.toNode(from, SnapNode::new));
			toNode = JsonAST.toNode(to, SnapNode::new);
			edits = calculateEdits();
			this.toSource = toSource;
		}

		private List<EditHint> calculateEdits() {
			// TODO: Find a better way to parse/read the edits
			// This shouldn't be strictly necessary to compare edits, since it should be using only
			// the to Node for comparison, but it still seems important.
			HintConfig config = new SnapHintConfig();
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
