package edu.isnap.eval.agreement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.graph.Node.Action;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.hint.edit.Insertion;
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

	private static boolean PRINT = true;

	public static boolean testEditConsistency(Snapshot a, Snapshot b) {
		Node from = toTree(a);
		Node to = toTree(b);
		return testEditConsistency(from, to, PRINT);
	}

	public static boolean testEditConsistency(Node from, Node to, boolean print) {
		from = from.copy();
		to = to.copy();
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
		if (!equal && print) {
			System.out.println(originalFrom.prettyPrintWithIDs());
			System.out.println(originalTo.prettyPrintWithIDs());
			for (String editString : editStrings) {
				System.out.println(editString);
			}
			System.out.println(Diff.diff(to.prettyPrint(), from.prettyPrint()));

			findEdits(originalFrom, originalTo);
		}

		return equal;
	}

	private final static IDer ider = new IDer() {

		// We just use a default config since this is Snap-specific code, but we may need to
		// update this at some point
		private HintConfig config = new HintConfig();

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
			if (config.isCodeElement(parent)) {
				// For ID-less parameters (lists and literals), the index is most stable
				// The parent's children are being added, so the current size will be the index
				index = parent.children.size();
			} else {
				// Otherwise, for ID-less children of non-code-elements (e.g. scripts), the most
				// stable index is the count of siblings with the same type that come before this
				// node
				for (Node child : parent.children) if (child.hasType(type)) index++;
			}

			// The ID is as unique as we can get it: the node's type, plus its parent's type and
			// some stable index
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

	@SuppressWarnings("unused")
	private static List<EditHint> findEdits(Snapshot from, Snapshot to) {
		Node fromNode = toTree(from);
		Node toNode = toTree(to);
		return findEdits(fromNode, toNode);
	}

	public static List<EditHint> findEdits(Node from, Node to) {

		Map<String, Node> fromIDMap = getIDMap(from);
		Map<String, Node> toIDMap = getIDMap(to);

		List<EditHint> renames = new ArrayList<>();
		BiMap<Node, Node> mapping = new BiMap<>(MapFactory.IdentityHashMapFactory);
		for (String id : fromIDMap.keySet()) {
			Node fromNode = fromIDMap.get(id);
			Node toNode = toIDMap.get(id);

			if (id == null) {
				System.err.println("Null id for: " + fromNode);
			}

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
				rename.replaced = fromNode;
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
		Map<String, Node> idMap = new LinkedHashMap<>();
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

	public static Node toTree(Snapshot snapshot) {
		return SimpleNodeBuilder.toTree(snapshot, true, ider);
	}

}
