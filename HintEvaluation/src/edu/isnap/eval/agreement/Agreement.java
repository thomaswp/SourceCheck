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
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.NodeAlignment.Mapping;
import edu.isnap.ctd.util.NullSream;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder.IDer;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Script;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.util.IHasID;

public class Agreement {

	private static boolean PRINT = true;

	public static boolean testEditConsistency(Snapshot a, Snapshot b, boolean compareValues) {
		Node from = toTree(a);
		Node to = toTree(b);
		return testEditConsistency(from, to, compareValues, PRINT);
	}

	public static boolean testEditConsistency(Node from, Node to, boolean compareValues,
			boolean print) {
		from = from.copy();
		to = to.copy();
		Node originalFrom = from.copy(), originalTo = to.copy();
		List<EditHint> edits = findEdits(from, to, compareValues);

		// Ensure we can apply the edits to a copy of from, not the one the edits were derived from
		Node appliedFrom = from;
		from = from.copy();

		// Capture edit strings before applying edits
		List<String> editStrings = new ArrayList<>();
		for (EditHint edit : edits) editStrings.add(edit.toString());

		try {
			EditHint.applyEdits(from, edits);
		} catch (Exception e) {
			e.printStackTrace();
			edits = findEdits(originalFrom, to, compareValues);
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

			findEdits(originalFrom, originalTo, compareValues);
		}

		// We have to prune here because the findEdits method can add some scripts to appliedFrom
		// Hopefully this will be changed one day, and then this pruning should be removed
		prune(appliedFrom); prune(originalFrom);
		if (!appliedFrom.equals(originalFrom)) {
			System.out.println("Edit altered original node without application");
			System.out.println(Diff.diff(originalFrom.prettyPrint(), appliedFrom.prettyPrint()));
			return false;
		}

		return equal;
	}

	public final static String GEN_ID_PREFIX = "GEN_";

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
			return String.format("%s%s{%s:%d}", GEN_ID_PREFIX, type, parent.id, index);
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
				"varMenu",
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

	public static Node pruneImmediateChildren(Node node) {
		for (int i = 0; i < node.children.size(); i++) {
			Node child = node.children.get(i);
			if (prunable.contains(child.type())) {
				pruneImmediateChildren(child);
				if (child.children.isEmpty()) {
					node.children.remove(i--);
				}
			}
		}
		return node;
	}

	@SuppressWarnings("unused")
	private static List<EditHint> findEdits(Snapshot from, Snapshot to, boolean compareValues) {
		Node fromNode = toTree(from);
		Node toNode = toTree(to);
		return findEdits(fromNode, toNode, compareValues);
	}

	public static List<EditHint> findEdits(Node from, Node to, boolean compareValues) {

		Map<String, Node> fromIDMap = getIDMap(from);
		Map<String, Node> toIDMap = getIDMap(to);

		List<EditHint> renames = new ArrayList<>();
		HintConfig config = new HintConfig();
		config.harmlessCalls.clear();
		config.useValues = compareValues;
		Mapping mapping = new Mapping(from, to, config, compareValues);

		for (String id : fromIDMap.keySet()) {
			Node fromNode = fromIDMap.get(id);
			Node toNode = toIDMap.get(id);

			if (id == null) {
				System.err.println("Null id for: " + fromNode + " in " + fromNode.parent);
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

			// You can relabel a block and it keeps its ID, so we check for that here
			boolean equal = compareValues ? fromNode.shallowEquals(toNode) :
				fromNode.hasType(toNode.type());
			if (!equal) {
				Insertion rename = new Insertion(fromNode.parent, toNode, fromNode.index(),
						toNode.value);
				rename.replaced = fromNode;
				rename.keepChildrenInReplacement = true;
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

				if (mapping.getTo(toNode) != null) {
					continue;
				}
				Node parentPair = fromIDMap.get(toNode.parent.id);
				if (parentPair != null) {
					// TODO: This should be represented as an insertion in order to:
					// a) avoid modifying the from node that is passed to this method and
					// b) allow the edits to be applied to nodes that were not passed to this method
					// This will involve some reworking of Insertions to allow chaining
					fromNode = toNode.shallowCopy(parentPair);
					fromNode.tag = "temp";
					parentPair.children.add(fromNode);
				} else {
					System.err.println("Warning: Added sprite with script");
				}

				mapping.put(fromNode, toNode);
			}
		}

		HintHighlighter highlighter = new HintHighlighter(new LinkedList<Node>(), config);
		highlighter.trace = NullSream.instance;
		List<EditHint> hints = highlighter.highlight(from, mapping, false);
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
