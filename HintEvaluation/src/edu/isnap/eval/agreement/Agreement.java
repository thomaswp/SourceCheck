package edu.isnap.eval.agreement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
import edu.isnap.datasets.Fall2016;
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

	public static void main(String[] args) {
//		try {
//			Snapshot a = Snapshot.parse(new File("A.xml"));
//			Snapshot b = Snapshot.parse(new File("B.xml"));
//			testEditConsistency(a, b);
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		}

		Map<String, AssignmentAttempt> attempts = Fall2016.GuessingGame1.load(Mode.Use, true, true,
				new SnapParser.SubmittedOnly());
		int i = 0;
		for (AssignmentAttempt attempt : attempts.values()) {
			Snapshot last = null;
			for (AttemptAction action : attempt) {
				if (last != null) {// && action.id == 196640) {
					System.out.println(action.id);
					testEditConsistency(last, action.snapshot);
				}
				last = action.snapshot;
			}
			if (i++ > 4) break;
		}

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
//			System.out.println(originalFrom.prettyPrintWithIDs());
//			System.out.println(originalTo.prettyPrintWithIDs());
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
			mapping.put(fromNode, toNode);

			// You can relabel a block and it keeps its ID, so we check for that here
			if (toNode != null && !fromNode.hasType(toNode.type())) {
				Insertion rename = new Insertion(fromNode.parent, toNode, fromNode.index());
				rename.replacement = fromNode;
				renames.add(rename);
			}
		}

		// Look for added scripts/lists and add them automatically to the from node, since the
		// highlighter cannot do this itself, and they don't constitute an edit
		for (String id : toIDMap.keySet()) {
			Node fromNode = fromIDMap.get(id);
			Node toNode = toIDMap.get(id);

			if (fromNode == null && toNode.hasType("script") &&
					toNode.parentHasType("sprite", "stage")) {
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
				.highlight(from, mapping);
		hints.addAll(renames);

		return hints;
	}

	private static Map<String, Node> getIDMap(Node node) {
		Map<String, Node> idMap = new HashMap<>();
		node.recurse(new Action() {
			@Override
			public void run(Node node) {
				idMap.put(node.id, node);
			}
		});
		return idMap;
	}

}
