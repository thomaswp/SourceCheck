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

		Map<String, AssignmentAttempt> attempts = Fall2016.GuessingGame1.load(Mode.Use, true);
		for (AssignmentAttempt attempt : attempts.values()) {
			Snapshot last = null;
			for (AttemptAction action : attempt) {
				if (last != null) {
					testEditConsistency(last, action.snapshot);
				}
				last = action.snapshot;
			}
			break;
		}

	}

	private static boolean testEditConsistency(Snapshot a, Snapshot b) {
		Node from = SimpleNodeBuilder.toTree(a, true, ider);
		Node to = SimpleNodeBuilder.toTree(b, true, ider);
		Node originalFrom = from.copy();

		List<EditHint> edits = findEdits(from, to);

		// Capture edit strings before applying edits
		List<String> editStrings = new ArrayList<>();
		for (EditHint edit : edits) editStrings.add(edit.toString());

		try {
			EditHint.applyEdits(from, edits);
		} catch (Exception e) {
			e.printStackTrace();
			EditHint.applyEdits(originalFrom, edits);
			return false;
		}

		prune(to); prune(from);

		boolean equal = to.equals(from);
		if (!equal) {
//			System.out.println(originalFrom.prettyPrintWithIDs());
//			System.out.println(to.prettyPrintWithIDs());

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
				return String.format("%s(%s:%d)", code.type(), parent.id, parent.children.size());

			}
			return id;
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
