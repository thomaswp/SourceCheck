package edu.isnap.eval.agreement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintHighlighter.EditHint;
import edu.isnap.ctd.hint.HintHighlighter.Insertion;
import edu.isnap.ctd.util.map.BiMap;
import edu.isnap.ctd.util.map.MapFactory;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2016;
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



	public static void main(String[] args) throws FileNotFoundException, IOException {
		extractEdits(Fall2016.instance, Spring2016.instance);
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

	private static void extractEdits(Dataset dataset, Dataset hintDataset)
			throws FileNotFoundException, IOException {


		CSVParser parser = new CSVParser(new FileReader(new File(dataset.dataDir, "hints.csv")),
				CSVFormat.DEFAULT.withHeader());

		for (CSVRecord record : parser) {
			String codeXML = record.get("code");
			String h1CodeXML = record.get("h1Code");
			String h2CodeXML = record.get("h2Code");

			String userID = record.get("userID");
			String rowID = record.get("rowID");
			String id = userID + " (" + rowID + ") ";

//			if (!"twprice (194230) ".equals(id)) continue;

			Snapshot code = Snapshot.parse("code", codeXML);
			Snapshot h1Code = Snapshot.parse("h1", h1CodeXML);
			Snapshot h2Code = Snapshot.parse("h2", h2CodeXML);

			if (!testEditConsistency(code, h1Code)) {
				System.out.println(id + 1);
			}

			if (!testEditConsistency(code, h2Code)) {
				System.out.println(id + 2);
			}
		}

		parser.close();
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
		if (!equal) {
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

				// TODO: warn that we've added a script
				Node parentPair = fromIDMap.get(toNode.parent.id);
				if (parentPair != null) {
					fromNode = new Node(parentPair, toNode.type(), toNode.id);
					parentPair.children.add(fromNode);
				} else {
					System.err.println("Warning: Added sprite with script");
				}

				mapping.put(fromNode, toNode);
			}
		}



		// TODO: need to make sure we're not omitting some edits (e.g. deleting a harmless block)
		List<EditHint> hints = new HintHighlighter(new LinkedList<Node>(), new HintConfig())
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
