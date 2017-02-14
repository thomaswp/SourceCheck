package edu.isnap.eval.agreement;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
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

public class Agreement {

	public static void main(String[] args) {
		try {
			Snapshot a = Snapshot.parse(new File("A.xml"));
			Snapshot b = Snapshot.parse(new File("B.xml"));
			findEdits(a, b);
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

	private static void findEdits(Snapshot fromSnapshot, Snapshot toSnapshot) {
		Node from = SimpleNodeBuilder.toTree(fromSnapshot, true, ider);
		Node to = SimpleNodeBuilder.toTree(toSnapshot, true, ider);

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
