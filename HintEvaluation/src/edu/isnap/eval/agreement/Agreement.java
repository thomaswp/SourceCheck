package edu.isnap.eval.agreement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintHighlighter.EditHint;
import edu.isnap.ctd.hint.HintHighlighter.Insertion;
import edu.isnap.ctd.hint.HintJSON;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.ctd.util.map.MapFactory;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.agreement.EditComparer.EditDifference;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder.IDer;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Script;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.util.Diff;

public class Agreement {

	private static boolean PRINT = true;

	public static void main(String[] args) throws FileNotFoundException, IOException {
		compareEdits(Spring2017.instance, Fall2016.Squiral, Fall2016.GuessingGame1);
	}

	public static void testLogEdits() {
		Map<String, AssignmentAttempt> attempts = Fall2016.GuessingGame1.load(Mode.Use, true, true,
				new SnapParser.SubmittedOnly());
		int i = 0;
		for (AssignmentAttempt attempt : attempts.values()) {
			Snapshot last = null;
			for (AttemptAction action : attempt) {
				if (last != null) {// && action.id == 222243) {
					if (!testEditConsistency(last, action.snapshot)) {
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

		HashMap<String, HintHighlighter> highlighters = new HashMap<>();
		for (Assignment assignment : trainingAssignments) {
			SnapHintBuilder builder = new SnapHintBuilder(assignment);
			HintMap hintMap = builder.buildGenerator(Mode.Use, 1).hintMap;
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

		Map<String, Map<String, List<EditHint>>> expertMap = new LinkedHashMap<>();
		Map<String, Map<String, List<EditHint>>> comparisonMap = new LinkedHashMap<>();
		Map<String, Node> nodeMap = new HashMap<>();

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
				nodeMap.put(rowID, fromNode = SimpleNodeBuilder.toTree(code, true, ider));
			}

			Node toNodeIdeal = SimpleNodeBuilder.toTree(h1Code, true, ider);
			Node toNodeAll = SimpleNodeBuilder.toTree(h2Code, true, ider);

			Map<String, List<EditHint>> expertRowMap = expertMap.get(rowID);
			if (expertRowMap == null) {
				expertMap.put(rowID, expertRowMap = new TreeMap<>());
			}

			Map<String, List<EditHint>> comparisonRowMap = comparisonMap.get(rowID);
			if (comparisonRowMap == null) {
				comparisonMap.put(rowID, comparisonRowMap = new TreeMap<>());
			}

			List<EditHint> idealEdits = findEdits(fromNode, toNodeIdeal);
			List<EditHint> allEdits = findEdits(fromNode, toNodeAll);
			expertRowMap.put(userID + "-ideal", idealEdits);
			expertRowMap.put(userID + "-all", allEdits);
			comparisonRowMap.put(userID + "-all", allEdits);


			if (!expertRowMap.containsKey("highlight")) {
				comparisonRowMap.put("highlight", highlighter.highlight(fromNode));
				comparisonRowMap.put("highlight-rted", highlighter.highlightRTED(fromNode));
				comparisonRowMap.put("highlight-sed", highlighter.highlightStringEdit(fromNode));
			}
		}


		HashMap<String, EditDifference> comps = new LinkedHashMap<>();

		for (String row : expertMap.keySet()) {
//			System.out.println(row);
			Map<String, List<EditHint>> expertRowMap = expertMap.get(row);
			Map<String, List<EditHint>> comparisonRowMap = comparisonMap.get(row);
			Node node = nodeMap.get(row);
			for (String keyA : expertRowMap.keySet()) {
				List<EditHint> editsA = expertRowMap.get(keyA);
				for (String keyB : comparisonRowMap.keySet()) {
					if (keyB.startsWith(keyA.substring(0, 4))) continue;
					List<EditHint> editsB = comparisonRowMap.get(keyB);
					String key = keyA + " vs " + keyB;
					EditDifference diff = EditComparer.compare(node, editsA, editsB);
					EditDifference last = comps.get(key);
					comps.put(key, EditDifference.sum(diff, last));
				}
			}
		}

		for (String key : comps.keySet()) {
			System.out.println(key);
			comps.get(key).print();
			System.out.println();
		}

		parser.close();
	}

	private static void extractEdits(Dataset dataset)
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

			if (!testEditConsistency(code, h1Code)) {
				System.out.println(prefix + 1);
			}

			if (!testEditConsistency(code, h2Code)) {
				System.out.println(prefix + 2);
			}

			printEditSQL(userID, rowID, 1, code, h1Code);
			printEditSQL(userID, rowID, 2, code, h2Code);
		}

		parser.close();
	}

	private static void printEditSQL(String userID, String rowID, int phase,
			Snapshot a, Snapshot b) {
		Node from = SimpleNodeBuilder.toTree(a, true, ider);
		Node to = SimpleNodeBuilder.toTree(b, true, ider);
		List<EditHint> edits = findEdits(from, to);
		JSONArray hintArray = HintJSON.hintArray(edits);
		String json = hintArray.toString();
		json = StringEscapeUtils.escapeSql(json);
		String sql = String.format(
				"UPDATE hints SET h%dEdits='%s' WHERE userID='%s' AND rowID=%s;",
				phase, json, userID, rowID);
		System.out.println(sql);
	}

	private static boolean testEditConsistency(Snapshot a, Snapshot b) {
		Node from = SimpleNodeBuilder.toTree(a, true, ider);
		Node to = SimpleNodeBuilder.toTree(b, true, ider);
		Node originalFrom = from.copy(), originalTo = to.copy();

		List<EditHint> edits = findEdits(from, to);

		// Capture edit strings before applying edits
		List<String> editStrings = new ArrayList<>();
		for (EditHint edit : edits) editStrings.add(edit.toString());

		try {
			EditHint.applyEdits(from, edits);
		} catch (Exception e) {
			e.printStackTrace();
			edits = findEdits(originalFrom, to);
			EditHint.applyEdits(originalFrom, edits);
			return false;
		}

		prune(to); prune(from);
		boolean equal = to.equals(from);
		if (!equal && PRINT) {
			System.out.println(originalFrom.prettyPrintWithIDs());
			System.out.println(originalTo.prettyPrintWithIDs());
			for (String editString : editStrings) {
				System.out.println(editString);
			}
			System.out.println(Diff.diff(to.prettyPrint(), from.prettyPrint()));
		}

		return equal;
	}

	private final static IDer ider = new IDer() {
		@Override
		public String getID(Code code, Node parent) {
			String id = code instanceof IHasID ? ((IHasID) code).getID() : null;
			if (code instanceof Script || id == null) {
				return getID(code.type(), parent);
			}
			return id;
		}

		@Override
		public String getID(String type, Node parent) {
			int index = 0;
			for (Node child : parent.children) if (child.hasType(type)) index++;
			return String.format("%s{%s:%d}", type, parent.id, index);
		}
	};

	// Nodes that might get auto-added as parameters to added nodes, which can be pruned away if
	// they have no children to make comparison between trees simpler
	private final static HashSet<String> prunable = new HashSet<>();
	static {
		for (String c : new String[] {
				"literal",
				"script",
				"list",
		}) {
			prunable.add(c);
		}
	}

	public static Node prune(Node node) {
		for (int i = 0; i < node.children.size(); i++) {
			Node child = node.children.get(i);
			prune(child);
			if (prunable.contains(child.type()) && child.children.isEmpty()) {
				node.children.remove(i--);
			}
		}
		return node;
	}

	private static List<EditHint> findEdits(Snapshot from, Snapshot to) {
		Node fromNode = SimpleNodeBuilder.toTree(from, true, ider);
		Node toNode = SimpleNodeBuilder.toTree(to, true, ider);
		return findEdits(fromNode, toNode);
	}

	private static List<EditHint> findEdits(Node from, Node to) {

		Map<String, Node> fromIDMap = getIDMap(from);
		Map<String, Node> toIDMap = getIDMap(to);

		List<EditHint> renames = new ArrayList<>();
		BiMap<Node, Node> mapping = new BiMap<>(MapFactory.IdentityHashMapFactory);
		for (String id : fromIDMap.keySet()) {
			Node fromNode = fromIDMap.get(id);
			Node toNode = toIDMap.get(id);

			if (toNode == null) continue;

			// Check if we've already paired this node
			Node oldToNode = mapping.getFrom(fromNode);
			if (oldToNode != null) {
				// Check if it was a (different) pairing based on the original ID
				if (toNode != oldToNode && fromNode.id.equals(oldToNode.id)) {
					// If so, we override with the pairing based on contents
					mapping.removeFrom(fromNode);
				} else {
					// Otherwise we ignore this pairing
					continue;
				}
			}
			// Then do the reverse to check if we're overriding a pairing based on contents
			Node oldFromNode = mapping.getTo(toNode);
			if (oldFromNode != null) {
				if (fromNode != oldFromNode && toNode.id.equals(oldFromNode.id)) {
					mapping.removeTo(toNode);
				} else {
					continue;
				}
			}

			mapping.put(fromNode, toNode);
//			if (fromNode.hasType("script")) {
//				System.out.println("---> " + fromNode + " -> " + toNode);
//				for (Node key : mapping.keysetFrom()) {
//					if (key.hasType("script")) System.out.println(key.id + " -> " + mapping.getFrom(key).id);
//				}
//			}

			// You can relabel a block and it keeps its ID, so we check for that here
			if (!fromNode.hasType(toNode.type())) {
				Insertion rename = new Insertion(fromNode.parent, toNode, fromNode.index());
				rename.replacement = fromNode;
				rename.keepChildrenInReplacement = true;
				renames.add(rename);
			}
		}

//		for (Node key : mapping.keysetFrom()) {
//			if (key.hasType("script")) System.out.println(key.id + " -> " + mapping.getFrom(key).id);
//		}

		// Look for added scripts/lists and add them automatically to the from node, since the
		// highlighter cannot do this itself, and they don't constitute an edit
		for (String id : toIDMap.keySet()) {
			Node fromNode = fromIDMap.get(id);
			Node toNode = toIDMap.get(id);

			if (fromNode == null && toNode.hasType("script") &&
					toNode.parentHasType("sprite", "stage")) {

				if (mapping.getTo(toNode) != null) {
					continue;
				}

				Node parentPair = fromIDMap.get(toNode.parent.id);
				if (parentPair != null) {
					fromNode = new Node(parentPair, toNode.type(), toNode.id);
					fromNode.tag = "temp";
					parentPair.children.add(fromNode);
				} else {
					System.err.println("Warning: Added sprite with script");
				}

				mapping.put(fromNode, toNode);
			}
		}


		HintConfig config = new HintConfig();
		config.harmlessCalls.clear();
		List<EditHint> hints = new HintHighlighter(new LinkedList<Node>(), config)
				.highlight(from, mapping, false);
		hints.addAll(renames);

		return hints;
	}

	private static Map<String, Node> getIDMap(Node node) {
		Map<String, Node> idMap = new HashMap<>();
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				idMap.put(node.id, node);
				// Include both the overridden, index-based id and and id based on the first child
				if (node.hasType("script") && !node.children.isEmpty() &&
						node.parentHasType("sprite", "stage")) {
					idMap.put(String.format("script{%s}", node.children.get(0).id), node);
				}
			}
		});
		return idMap;
	}

}
