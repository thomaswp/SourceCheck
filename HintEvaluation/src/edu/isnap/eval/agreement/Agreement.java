package edu.isnap.eval.agreement;

import java.io.File;
import java.io.FileNotFoundException;
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
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder.IDer;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Script;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.util.Diff;

public class Agreement {

	public static void main(String[] args) {
		try {
			Snapshot a = Snapshot.parse(new File("A.xml"));
			Snapshot b = Snapshot.parse(new File("B.xml"));

			Node from = SimpleNodeBuilder.toTree(a, true, ider);
			Node to = SimpleNodeBuilder.toTree(b, true, ider);

			List<EditHint> edits = findEdits(from, to);

			EditHint.applyEdits(from, edits);

			prune(to); prune(from);
			System.out.println(Diff.diff(to.prettyPrint(), from.prettyPrint()));
			System.out.println("Equal: " + to.equals(from));


		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

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
			if (prunable.contains(child.type()) && child.children.isEmpty()) {
				node.children.remove(i--);
			} else {
				prune(child);
			}
		}
		return node;
	}

	private static List<EditHint> findEdits(Node from, Node to) {

		Map<String, Node> fromIDMap = getIDMap(from);
		Map<String, Node> toIDMap = getIDMap(to);

		List<EditHint> renames = new LinkedList<>();
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

		System.out.println(from.prettyPrintWithIDs());
		System.out.println(to.prettyPrintWithIDs());

		for (EditHint hint : hints) {
			System.out.println(hint);
		}

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
